package app.mycontrolpanel.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.GTasksPlan
import app.mycontrolpanel.core.Json
import app.mycontrolpanel.core.Task
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Two-way sync with the Google Tasks default list. Uses Google's authorization API for the
 * "tasks" permission only; the access token lives in memory and is re-requested silently when needed.
 * Planning is pure (GTasksPlan); this class only talks HTTP.
 */
class GoogleTasksSync(private val ctx: Context, private val store: Store) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    @Volatile private var token: String? = null
    private val scope = Scope("https://www.googleapis.com/auth/tasks")
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(scope)).build()

    private fun status(t: String?) = main.post { store.dispatch(Action.GTasksStatus(t)) }

    private var lastTasks: List<Task>? = null

    /** Re-sync a few seconds after tasks change (not on bookkeeping-only changes, which would loop). */
    fun start() {
        store.dataListeners += { d ->
            if (d.gTasksOn && d.tasks != lastTasks) { lastTasks = d.tasks; schedule(8_000) }
        }
    }

    private val syncRunnable = Runnable { syncNow(interactive = null) }
    fun schedule(delayMs: Long) { main.removeCallbacks(syncRunnable); main.postDelayed(syncRunnable, delayMs) }

    /** Asks for the Tasks permission. If Google needs to show a consent screen, [launcher] shows it. */
    fun connect(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        status("Connecting to Google…")
        Identity.getAuthorizationClient(ctx).authorize(request)
            .addOnSuccessListener { res ->
                val pi = res.pendingIntent
                if (res.hasResolution() && pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else { token = res.accessToken; enableAndSync() }
            }
            .addOnFailureListener { status(explain(it.message)) }
    }

    fun onConsentResult(data: Intent?) {
        try {
            val res = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)
            token = res.accessToken
            enableAndSync()
        } catch (e: Exception) { status("Google Tasks wasn't connected (${e.message ?: "cancelled"}).") }
    }

    /** After permission is granted: show the user's lists so they can choose which one syncs. */
    private fun enableAndSync() {
        if (store.state.value.data.gTasksOn) main.post { syncNow(interactive = null) } else loadLists()
    }

    /** Fetch the user's Google Tasks lists (for the list picker). */
    fun loadLists() {
        withToken { io.execute {
            try {
                val root = Json.parse(call("GET", "$API/users/@me/lists?maxResults=100")) as? Map<*, *>
                val lists = (root?.get("items") as? List<*>)?.mapNotNull { i ->
                    val m = i as? Map<*, *> ?: return@mapNotNull null
                    val id = m["id"] as? String ?: return@mapNotNull null
                    id to (m["title"] as? String ?: "Untitled")
                } ?: emptyList()
                main.post { store.dispatch(Action.GListsLoaded(lists)); store.dispatch(Action.GTasksStatus(if (lists.isEmpty()) "No lists found." else null)) }
            } catch (e: HttpError) { status(explain(e.body)) } catch (e: Exception) { status("Couldn't load your lists (${e.message ?: "network"}).") }
        } }
    }

    /** Create a new list in Google Tasks and sync with it. */
    fun createList(title: String, copyExisting: Boolean) {
        withToken { io.execute {
            try {
                val res = Json.parse(call("POST", "$API/users/@me/lists", Json.write(mapOf("title" to title)))) as? Map<*, *>
                val id = res?.get("id") as? String ?: throw HttpError(500, "Google didn't return a list id")
                main.post {
                    store.dispatch(Action.ChooseGList(id, title, copyExisting))
                    store.dispatch(Action.GTasksStatus("Created “$title” in Google Tasks."))
                    syncNow(interactive = null)
                }
            } catch (e: HttpError) { status(explain(e.body)) } catch (e: Exception) { status("Couldn't create the list (${e.message ?: "network"}).") }
        } }
    }

    /** Runs [block] once a token is available (silent re-authorization). */
    private fun withToken(block: () -> Unit) {
        if (token != null) { block(); return }
        Identity.getAuthorizationClient(ctx).authorize(request)
            .addOnSuccessListener { res -> if (!res.hasResolution()) { token = res.accessToken; block() } else status("Tap Connect Google Tasks to allow access.") }
            .addOnFailureListener { status(explain(it.message)) }
    }

    /** Runs one sync round in the background. Silent re-authorization; never pops UI by itself. */
    fun syncNow(interactive: ActivityResultLauncher<IntentSenderRequest>?) {
        if (!store.state.value.data.gTasksOn) return
        if (!running.compareAndSet(false, true)) return
        Identity.getAuthorizationClient(ctx).authorize(request)
            .addOnSuccessListener { res ->
                val pi = res.pendingIntent
                if (res.hasResolution()) {
                    running.set(false)
                    if (interactive != null && pi != null) interactive.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    else status("Google needs you to allow Tasks again — tap Connect.")
                } else { token = res.accessToken; io.execute { round() } }
            }
            .addOnFailureListener { running.set(false); status(explain(it.message)) }
    }

    private fun explain(msg: String?): String = when {
        msg == null -> "Couldn't reach Google."
        msg.contains("access_denied", true) || msg.contains("not been verified", true) || msg.contains("test user", true) ->
            "Google blocked access. Add this Gmail as a test user in the Google Cloud consent screen."
        msg.contains("API has not been used", true) || msg.contains("disabled", true) ->
            "The Google Tasks API isn't enabled in your Google Cloud project yet."
        else -> "Google Tasks: $msg"
    }

    private fun round() {
        try {
            status("Syncing with Google Tasks…")
            val snapshot = store.state.value.data
            val remote = fetchAll()
            var n = 0
            val plan = GTasksPlan.plan(snapshot, remote) { "gt" + System.currentTimeMillis().toString(36) + (n++) }
            val created = HashMap<String, String>()
            for (t in plan.remoteCreates) created[t.id] = create(t)
            for ((gid, t) in plan.remoteUpdates) patch(gid, t)
            for (gid in plan.remoteDeletes) delete(gid)
            val at = System.currentTimeMillis()
            main.post {
                store.dispatch(Action.ApplyGTasks(plan, at, created))
                store.dispatch(Action.GTasksStatus(
                    if (plan.isEmpty) "Up to date with Google Tasks" else "Synced with Google Tasks just now"))
            }
        } catch (e: HttpError) {
            if (e.code == 401) token = null
            status(explain(e.body))
        } catch (e: Exception) {
            status("Google Tasks: couldn't sync (${e.message ?: "network"}). Will retry.")
        } finally {
            running.set(false)
        }
    }

    // ---------------- REST ----------------

    private class HttpError(val code: Int, val body: String) : Exception("HTTP $code")

    private fun call(method: String, url: String, body: String? = null): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Authorization", "Bearer ${token ?: throw HttpError(401, "no token")}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = 15_000; readTimeout = 20_000
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toByteArray()) } }
        }
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, text)
            return text
        } finally { c.disconnect() }
    }

    private val API = "https://tasks.googleapis.com/tasks/v1"
    /** Tasks of the one list the user chose to sync. */
    private val BASE get() = "$API/lists/${store.state.value.data.prefs.gListId}/tasks"

    private fun fetchAll(): List<GTasksPlan.GTask> {
        val out = ArrayList<GTasksPlan.GTask>()
        var page: String? = null
        do {
            val url = "$BASE?showCompleted=true&showHidden=true&showDeleted=true&maxResults=100" +
                (page?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val root = Json.parse(call("GET", url)) as? Map<*, *> ?: break
            (root["items"] as? List<*>)?.forEach { i ->
                val m = i as? Map<*, *> ?: return@forEach
                val id = m["id"] as? String ?: return@forEach
                out += GTasksPlan.GTask(
                    id = id,
                    title = (m["title"] as? String ?: "").trim(),
                    notes = (m["notes"] as? String ?: "").trim(),
                    done = m["status"] == "completed",
                    updated = runCatching { Instant.parse(m["updated"] as String).toEpochMilli() }.getOrDefault(0L),
                    deleted = m["deleted"] == true,
                )
            }
            page = root["nextPageToken"] as? String
        } while (page != null)
        return out
    }

    private fun bodyOf(t: Task) = Json.write(mapOf("title" to t.title, "notes" to t.notes,
        "status" to if (t.done) "completed" else "needsAction"))

    private fun create(t: Task): String {
        val res = Json.parse(call("POST", BASE, bodyOf(t))) as? Map<*, *>
        return res?.get("id") as? String ?: throw HttpError(500, "Google didn't return an id")
    }

    private fun patch(gid: String, t: Task) { call("PATCH", "$BASE/$gid", bodyOf(t)) }
    private fun delete(gid: String) {
        try { call("DELETE", "$BASE/$gid") } catch (e: HttpError) { if (e.code != 404 && e.code != 410) throw e }
    }
}
