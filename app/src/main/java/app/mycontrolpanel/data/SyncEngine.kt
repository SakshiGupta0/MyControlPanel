package app.mycontrolpanel.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.mycontrolpanel.core.Account
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppData
import app.mycontrolpanel.core.Space
import app.mycontrolpanel.core.Sync
import app.mycontrolpanel.core.SyncStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/**
 * Firestore sync. Layout:
 *   users/{uid}/items/{key}          personal items   {k, mt, del, j, by}
 *   spaces/{spaceId}                 {name, owner, members[], memberNames{uid:name}, inviteCode}
 *   spaces/{spaceId}/items/{key}     shared tasks
 *   invites/{code}                   {spaceId}
 *
 * Nothing uploads until the first server snapshot has been merged, so a fresh install can never
 * overwrite an existing account with defaults. Security rules reject writes older than what the
 * server has (mt only moves forward), so a device that was offline for a week can't clobber newer edits.
 */
class SyncEngine(private val ctx: Context, private val store: Store) {
    private val main = Handler(Looper.getMainLooper())
    val ready: Boolean = FirebaseApp.getApps(ctx).isNotEmpty()
    private val auth: FirebaseAuth? = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = if (ready) FirebaseFirestore.getInstance() else null

    private var uid: String? = null
    private var userItems: ListenerRegistration? = null
    private var spacesReg: ListenerRegistration? = null
    private val spaceItemRegs = HashMap<String, ListenerRegistration>()
    private var merged = false                       // first server snapshot applied
    private val inFlight = HashMap<String, Long>()   // key → mt currently being written
    private var spaceIds: Set<String> = emptySet()

    fun start() {
        if (auth == null) { store.dispatch(Action.AccountChanged(null, false)); return }
        // Known right away (no waiting for Firebase's first callback): accounts are available, and who is signed in.
        val current = auth.currentUser
        if (current == null) store.dispatch(Action.AccountChanged(null, true)) else onUser(current)
        auth.addAuthStateListener { a -> onUser(a.currentUser) }
        store.dataListeners += { scheduleUpload() }
    }

    private fun onUser(user: FirebaseUser?) {
        if (user?.uid == uid) return
        detach()
        uid = user?.uid
        if (user == null) { store.dispatch(Action.AccountChanged(null, true)); return }
        val name = user.displayName ?: user.email?.substringBefore('@') ?: "Me"
        store.dispatch(Action.AccountChanged(Account(user.uid, user.email ?: "", name), true))
        attach(user.uid)
    }

    private fun detach() {
        userItems?.remove(); userItems = null
        spacesReg?.remove(); spacesReg = null
        spaceItemRegs.values.forEach { it.remove() }; spaceItemRegs.clear()
        merged = false; inFlight.clear(); spaceIds = emptySet()
    }

    private fun attach(uid: String) {
        val db = db ?: return
        userItems = db.collection("users").document(uid).collection("items").addSnapshotListener { snap, err ->
            if (err != null) { fail(err); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            if (!merged) {
                if (snap.metadata.isFromCache) return@addSnapshotListener   // wait for the server's answer before the first merge
                merged = true
                val docs = snap.documents.mapNotNull(::toDoc)
                store.dispatch(Action.ApplyRemote(docs, accountWasEmpty = docs.isEmpty()))
            } else {
                val changed = snap.documentChanges.mapNotNull { toDoc(it.document) }
                if (changed.isNotEmpty()) store.dispatch(Action.ApplyRemote(changed))
            }
            store.dispatch(Action.SyncStatusChanged(if (snap.metadata.isFromCache) SyncStatus.OFFLINE else SyncStatus.SYNCED))
            scheduleUpload()
        }
        spacesReg = db.collection("spaces").whereArrayContains("members", uid).addSnapshotListener { snap, err ->
            if (err != null) { fail(err); return@addSnapshotListener }
            if (snap == null || snap.metadata.isFromCache && !merged) return@addSnapshotListener
            val spaces = snap.documents.mapNotNull(::toSpace)
            spaceIds = spaces.map { it.id }.toSet()
            store.dispatch(Action.SpacesChanged(spaces))
            // follow each space's items
            (spaceItemRegs.keys - spaceIds).forEach { spaceItemRegs.remove(it)?.remove() }
            (spaceIds - spaceItemRegs.keys).forEach { sid ->
                spaceItemRegs[sid] = db.collection("spaces").document(sid).collection("items").addSnapshotListener { s2, e2 ->
                    if (e2 != null) { fail(e2); return@addSnapshotListener }
                    val changed = s2?.documentChanges?.mapNotNull { toDoc(it.document, sid) } ?: return@addSnapshotListener
                    if (changed.isNotEmpty()) store.dispatch(Action.ApplyRemote(changed))
                }
            }
            scheduleUpload()
        }
    }

    private fun fail(e: Exception) {
        val msg = when ((e as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "The database refused access. Are the security rules published?"
            FirebaseFirestoreException.Code.UNAVAILABLE -> null
            else -> e.message
        }
        store.dispatch(Action.SyncStatusChanged(if (msg == null) SyncStatus.OFFLINE else SyncStatus.ERROR, msg))
    }

    // ---------------- upload ----------------

    private val uploadRunnable = Runnable { upload(store.state.value.data) }
    private fun scheduleUpload() { main.removeCallbacks(uploadRunnable); main.postDelayed(uploadRunnable, 700) }

    private fun upload(d: AppData) {
        val db = db ?: return
        val uid = uid ?: return
        if (!merged) return
        val out = Sync.outgoing(d, spaceIds).filter { (inFlight[it.key] ?: -1) < it.mt }
        for (doc in out) {
            inFlight[doc.key] = doc.mt
            val ref = if (doc.spaceId != null) db.collection("spaces").document(doc.spaceId).collection("items").document(doc.key)
                      else db.collection("users").document(uid).collection("items").document(doc.key)
            ref.set(mapOf("k" to doc.key, "mt" to doc.mt, "del" to doc.deleted, "j" to doc.json, "by" to uid))
                .addOnSuccessListener { done(doc) }
                .addOnFailureListener { e ->
                    inFlight.remove(doc.key)
                    // Rejected because the server already holds a newer version: nothing to send; the listener brings the newer one.
                    if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) done(doc) else fail(e)
                }
        }
    }

    private fun done(doc: Sync.Doc) {
        if (inFlight[doc.key] == doc.mt) inFlight.remove(doc.key)
        store.dispatch(Action.MarkSent(listOf(doc)))
    }

    private fun toDoc(s: DocumentSnapshot, spaceId: String? = null): Sync.Doc? {
        val key = s.getString("k") ?: s.id
        val mt = s.getLong("mt") ?: return null
        return Sync.Doc(key, mt, s.getBoolean("del") ?: false, s.getString("j") ?: "", spaceId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun toSpace(s: DocumentSnapshot): Space? {
        val names = (s.get("memberNames") as? Map<String, Any?>)?.mapValues { it.value as? String ?: "" } ?: emptyMap()
        val members = (s.get("members") as? List<String>) ?: return null
        return Space(s.id, s.getString("name") ?: "Shared", s.getString("owner") ?: "",
            members.associateWith { names[it] ?: "Someone" }, s.getString("inviteCode") ?: "")
    }

    // ---------------- shared spaces ----------------

    private val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O/1/I/L

    fun createSpace(name: String, onResult: (String) -> Unit) {
        val db = db ?: return onResult("Accounts aren't available in this build.")
        val user = auth?.currentUser ?: return onResult("Sign in first.")
        val ref = db.collection("spaces").document()
        val code = (1..6).map { CODE_CHARS.random() }.joinToString("")
        val me = user.displayName ?: user.email ?: "Me"
        db.runBatch { b ->
            b.set(ref, mapOf("name" to name.trim().take(60), "owner" to user.uid, "members" to listOf(user.uid),
                "memberNames" to mapOf(user.uid to me), "inviteCode" to code))
            b.set(db.collection("invites").document(code), mapOf("spaceId" to ref.id))
        }.addOnSuccessListener { onResult("Created “${name.trim()}”. Share the code $code to invite people.") }
         .addOnFailureListener { onResult("Couldn't create the space: ${it.message}") }
    }

    fun joinSpace(rawCode: String, onResult: (String) -> Unit) {
        val db = db ?: return onResult("Accounts aren't available in this build.")
        val user = auth?.currentUser ?: return onResult("Sign in first.")
        val code = rawCode.trim().uppercase()
        db.collection("invites").document(code).get().addOnSuccessListener { inv ->
            val sid = inv.getString("spaceId") ?: return@addOnSuccessListener onResult("No space has the code $code. Check it with the person who shared it.")
            db.collection("spaces").document(sid).update(mapOf(
                "members" to FieldValue.arrayUnion(user.uid),
                "memberNames.${user.uid}" to (user.displayName ?: user.email ?: "Someone"),
                "joinCode" to code,
            )).addOnSuccessListener { onResult("Joined! Shared tasks will appear in a moment.") }
              .addOnFailureListener { onResult("Couldn't join: ${it.message}") }
        }.addOnFailureListener { onResult("Couldn't check that code: ${it.message}") }
    }

    fun leaveSpace(spaceId: String, onResult: (String) -> Unit) {
        val db = db ?: return
        val user = auth?.currentUser ?: return
        db.collection("spaces").document(spaceId).update(mapOf(
            "members" to FieldValue.arrayRemove(user.uid),
            "memberNames.${user.uid}" to FieldValue.delete(),
        )).addOnSuccessListener { onResult("You've left the space.") }
          .addOnFailureListener { onResult("Couldn't leave: ${it.message}") }
    }

    fun renameSpace(spaceId: String, name: String) {
        db?.collection("spaces")?.document(spaceId)?.set(mapOf("name" to name.trim().take(60)), SetOptions.merge())
    }

    fun signOut() {
        auth?.signOut()
        store.dispatch(Action.SignedOut)
    }
}
