package app.mycontrolpanel.core

/**
 * Two-way sync between personal built-in tasks and the Google Tasks default list ("My Tasks").
 * Pure planning: the Android side fetches Google's list, asks plan() what to do, performs the Google
 * calls, then dispatches ApplyGTasks so the local side is updated in one state change.
 * Shared-space tasks never go to Google.
 */
object GTasksPlan {

    data class GTask(val id: String, val title: String, val notes: String, val done: Boolean, val updated: Long, val deleted: Boolean = false)

    data class Result(
        val localUpserts: List<Task> = emptyList(),
        val localDeletes: Set<String> = emptySet(),
        val remoteCreates: List<Task> = emptyList(),
        val remoteUpdates: List<Pair<String, Task>> = emptyList(),
        val remoteDeletes: Set<String> = emptySet(),
        /** Pending Google deletions this plan accounted for (done now, or already gone at Google). */
        val handledGDeletes: Set<String> = emptySet(),
    ) {
        val isEmpty get() = localUpserts.isEmpty() && localDeletes.isEmpty() && remoteCreates.isEmpty() && remoteUpdates.isEmpty() && remoteDeletes.isEmpty()
    }

    private fun same(t: Task, r: GTask) = t.title == r.title && t.notes == r.notes && t.done == r.done

    fun plan(d: AppData, remote: List<GTask>, newId: () -> String): Result {
        val last = d.gLastSync
        val byId = remote.associateBy { it.id }
        val personal = d.tasks.filter { it.spaceId == null }
        val linked = personal.mapNotNull { it.googleId }.toSet()

        val upserts = ArrayList<Task>(); val localDel = HashSet<String>()
        val creates = ArrayList<Task>(); val updates = ArrayList<Pair<String, Task>>()
        val remoteDel = d.gDeletes.filter { id -> byId[id]?.deleted == false }.toSet()

        // Google → here: tasks we've never seen
        for (r in remote) {
            if (r.deleted || r.id in linked || r.id in d.gDeletes || r.title.isBlank()) continue
            upserts += Task(newId(), r.title, r.notes, done = r.done, doneAt = if (r.done) r.updated else null,
                createdAt = r.updated, order = r.updated, googleId = r.id)
        }
        for (t in personal) {
            val gid = t.googleId
            if (gid == null) { if (!t.done && t.createdAt >= d.gPushAfter) creates += t; continue }
            val r = byId[gid]
            if (r == null || r.deleted) {
                if (last > 0) localDel += t.id                       // deleted at Google since we last synced
                else if (!t.done) creates += t.copy(googleId = null) // first sync and the link is stale: re-create
                continue
            }
            if (same(t, r)) continue
            val localMt = d.meta["task:${t.id}"] ?: 0
            val remoteChanged = r.updated > last
            val localChanged = localMt > last
            if (remoteChanged && (!localChanged || r.updated >= localMt)) {
                upserts += t.copy(title = r.title, notes = r.notes, done = r.done,
                    doneAt = if (r.done) (t.doneAt ?: r.updated) else null)
            } else if (localChanged) updates += gid to t
        }
        return Result(upserts, localDel, creates, updates, remoteDel, d.gDeletes)
    }

    /**
     * Apply the local half after Google calls succeeded. [created] maps local task id → new Google id.
     * Returns data with gLastSync = at and the processed Google deletions cleared.
     */
    fun applyLocal(d: AppData, r: Result, at: Long, created: Map<String, String> = emptyMap()): AppData {
        var tasks = d.tasks.filter { it.id !in r.localDeletes }
        for (u in r.localUpserts) tasks = if (tasks.any { it.id == u.id }) tasks.map { if (it.id == u.id) u else it } else tasks + u
        if (created.isNotEmpty()) tasks = tasks.map { t -> created[t.id]?.let { t.copy(googleId = it) } ?: t }
        return d.copy(tasks = tasks, gDeletes = d.gDeletes - r.handledGDeletes, gLastSync = at)
    }
}
