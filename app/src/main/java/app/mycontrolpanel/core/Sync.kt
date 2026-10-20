package app.mycontrolpanel.core

/**
 * Multi-device sync, as pure functions (tested on the JVM in CoreSuite).
 *
 * Every synced item has a key ("mode:<id>", "routine:<id>", "task:<id>", "prefs", "override", "daily").
 * - stamp():       after each local change, records meta[key] = now for items that changed and tombs[key] = now for deletions.
 * - outgoing():    items this device has changed but not yet sent (meta/tomb newer than synced[key]).
 * - applyRemote(): merges documents from the server; per item, the newest timestamp wins, and a deletion
 *                  newer than an edit removes the item (an edit newer than the deletion brings it back).
 * - markSent():    records what the server has accepted.
 *
 * Device-only things never sync: the routine in progress, focus timer, screen/brightness/text-size settings.
 */
object Sync {

    data class Doc(
        val key: String,
        val mt: Long,
        val deleted: Boolean,
        val json: String,          // the item, encoded with Codec ("" when deleted)
        val spaceId: String? = null,
    )

    /** Preference fields that follow you across devices. Everything else in Prefs stays on this device. */
    val SYNCED_PREFS = setOf("use24h", "waterTarget", "glassMl", "mealNames", "breakIntervalMin", "sleepAudioUri",
        "todayLimit", "focusMin", "transitionWarnMin", "chimeOnTransition", "gListId", "gListTitle", "nickname")

    fun kindOf(key: String) = key.substringBefore(':')
    fun idOf(key: String) = key.substringAfter(':', "")

    /** Current synced items as key → (comparable value, spaceId). */
    private fun items(d: AppData): Map<String, Pair<Any, String?>> {
        val out = LinkedHashMap<String, Pair<Any, String?>>()
        d.modes.forEach { out["mode:${it.id}"] = it to null }
        d.routines.forEach { out["routine:${it.id}"] = it to null }
        d.tasks.forEach { out["task:${it.id}"] = it to it.spaceId }
        out["prefs"] = Codec.prefsOut(d.prefs).filterKeys { it in SYNCED_PREFS } to null
        d.override?.let { out["override"] = it to null }
        out["daily"] = d.daily.copy(prompted = emptySet(), dismissed = emptySet(), warnedFor = null) to null
        return out
    }

    /** Record local changes between [old] and [new] at time [now]. */
    fun stamp(old: AppData, new: AppData, now: Long): AppData {
        val a = items(old); val b = items(new)
        if (a == b) return new
        val meta = new.meta.toMutableMap(); val tombs = new.tombs.toMutableMap()
        for ((k, v) in b) if (a[k] != v) { meta[k] = now; tombs.remove(k) }
        for (k in a.keys) if (k !in b) { tombs[k] = now; meta.remove(k) }
        // a deleted Google-linked task must also be deleted at Google
        val gDel = a.keys.filter { it !in b && kindOf(it) == "task" }
            .mapNotNull { (a[it]?.first as? Task)?.googleId }.toSet()
        // remember which shared space a deleted task lived in, so its tombstone goes there
        val spaces = new.tombSpaces.toMutableMap()
        a.keys.filter { it !in b }.forEach { k -> a[k]?.second?.let { spaces[k] = it } }
        return new.copy(meta = meta, tombs = tombs, gDeletes = new.gDeletes + gDel, tombSpaces = spaces)
    }

    /** First sign-in to an empty account: mark everything as changed so it all uploads. */
    fun stampAll(d: AppData, now: Long): AppData =
        d.copy(meta = items(d).keys.associateWith { now } + d.meta.filterValues { it > now })

    fun encode(key: String, d: AppData): String? = when (kindOf(key)) {
        "mode" -> d.modes.find { it.id == idOf(key) }?.let { Json.write(Codec.modeOut(it)) }
        "routine" -> d.routines.find { it.id == idOf(key) }?.let { Json.write(Codec.routineOut(it)) }
        "task" -> d.tasks.find { it.id == idOf(key) }?.let { Json.write(Codec.taskOut(it)) }
        "prefs" -> Json.write(Codec.prefsOut(d.prefs).filterKeys { it in SYNCED_PREFS })
        "override" -> d.override?.let { Json.write(mapOf("modeId" to it.modeId, "until" to it.until, "setAt" to it.setAt)) }
        "daily" -> Json.write(Codec.dailyOut(d.daily))
        else -> null
    }

    /** Changes not yet sent. Deleted items go out as tombstones so other devices remove them too. */
    fun outgoing(d: AppData, spacesKnown: Set<String>? = null): List<Doc> {
        val out = ArrayList<Doc>()
        for ((k, mt) in d.meta) {
            if (mt <= (d.synced[k] ?: 0)) continue
            val json = encode(k, d) ?: continue
            val space = if (kindOf(k) == "task") d.tasks.find { it.id == idOf(k) }?.spaceId else null
            if (space != null && spacesKnown != null && space !in spacesKnown) continue
            out += Doc(k, mt, false, json, space)
        }
        for ((k, mt) in d.tombs) {
            if (mt <= (d.synced[k] ?: 0)) continue
            out += Doc(k, mt, true, "", d.tombSpaces[k])
        }
        return out.sortedBy { it.key }
    }

    fun markSent(d: AppData, docs: List<Doc>): AppData {
        val synced = d.synced.toMutableMap()
        docs.forEach { if (it.mt > (synced[it.key] ?: 0)) synced[it.key] = it.mt }
        return d.copy(synced = synced)
    }

    /** Merge server documents into local data. Newer timestamp wins per item. */
    fun applyRemote(d0: AppData, docs: List<Doc>): AppData {
        var d = d0
        val meta = d.meta.toMutableMap(); val tombs = d.tombs.toMutableMap(); val synced = d.synced.toMutableMap()
        for (doc in docs) {
            val local = maxOf(meta[doc.key] ?: 0, tombs[doc.key] ?: 0)
            if (doc.mt <= local) { if (doc.mt == local) synced[doc.key] = maxOf(synced[doc.key] ?: 0, doc.mt); continue }
            val applied = if (doc.deleted) remove(d, doc.key) else put(d, doc.key, doc.json, doc.spaceId) ?: continue
            d = applied
            if (doc.deleted) { tombs[doc.key] = doc.mt; meta.remove(doc.key) } else { meta[doc.key] = doc.mt; tombs.remove(doc.key) }
            synced[doc.key] = doc.mt
        }
        return d.copy(meta = meta, tombs = tombs, synced = synced)
    }

    private fun put(d: AppData, key: String, json: String, spaceId: String?): AppData? {
        val m = runCatching { Json.parse(json) as? Map<*, *> }.getOrNull() ?: return null
        return when (kindOf(key)) {
            "mode" -> Codec.modeIn(m)?.let { x -> d.copy(modes = upsert(d.modes, x) { it.id }.sortedBy { it.order }) }
            "routine" -> Codec.routineIn(m)?.let { x -> d.copy(routines = upsert(d.routines, x) { it.id }) }
            "task" -> Codec.taskIn(m)?.let { x -> d.copy(tasks = upsert(d.tasks, x.copy(spaceId = spaceId ?: x.spaceId)) { it.id }) }
            "prefs" -> {
                val merged: MutableMap<String, Any?> = HashMap(Codec.prefsOut(d.prefs))
                m.forEach { (k, v) -> if (k is String && k in SYNCED_PREFS) merged[k] = v }
                d.copy(prefs = Codec.prefsIn(merged))
            }
            "override" -> (m["modeId"] as? String)?.let { id ->
                d.copy(override = Override(id, (m["until"] as? Number)?.toLong(), (m["setAt"] as? Number)?.toLong() ?: 0))
            }
            "daily" -> {
                val incoming = Codec.dailyIn(m)
                // keep this device's own prompt bookkeeping; take the counters
                d.copy(daily = incoming.copy(prompted = d.daily.prompted + incoming.prompted, dismissed = d.daily.dismissed + incoming.dismissed,
                    warnedFor = d.daily.warnedFor))
            }
            else -> null
        }
    }

    private fun remove(d: AppData, key: String): AppData = when (kindOf(key)) {
        "mode" -> d.copy(modes = d.modes.filter { it.id != idOf(key) })
        "routine" -> d.copy(routines = d.routines.filter { it.id != idOf(key) })
        "task" -> d.copy(tasks = d.tasks.filter { it.id != idOf(key) })
        "override" -> d.copy(override = null)
        else -> d
    }

    private inline fun <T> upsert(list: List<T>, x: T, id: (T) -> String): List<T> =
        if (list.any { id(it) == id(x) }) list.map { if (id(it) == id(x)) x else it } else list + x

    /** After leaving a space (or losing access), drop its tasks locally without sending deletions. */
    fun dropSpaces(d: AppData, keep: Set<String>): AppData {
        val gone = d.tasks.filter { it.spaceId != null && it.spaceId !in keep }
        if (gone.isEmpty()) return d
        val keys = gone.map { "task:${it.id}" }.toSet()
        return d.copy(tasks = d.tasks - gone.toSet(), meta = d.meta - keys, synced = d.synced - keys)
    }
}
