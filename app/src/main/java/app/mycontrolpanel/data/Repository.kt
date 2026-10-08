package app.mycontrolpanel.data

import app.mycontrolpanel.core.AppData
import app.mycontrolpanel.core.Codec
import app.mycontrolpanel.core.Defaults
import java.io.File

/**
 * On-device storage: one JSON file, written atomically (temp file + rename), so a crash or
 * power loss mid-write can never leave a half-written config behind.
 */
class Repository(private val dir: File) {
    private val file = File(dir, "panel.json")
    private val tmp = File(dir, "panel.json.tmp")

    data class Loaded(val data: AppData, val message: String?)

    fun load(): Loaded {
        if (!file.exists()) return Loaded(Defaults.data(), null) // first launch: defaults, no setup screen
        return try {
            Loaded(Codec.decode(file.readText()), null)
        } catch (e: Exception) {
            // Keep the unreadable file for recovery instead of overwriting it silently.
            runCatching { file.copyTo(File(dir, "panel.unreadable-${System.currentTimeMillis()}.json"), overwrite = true) }
            Loaded(Defaults.data(), "Your saved settings couldn't be read, so defaults were loaded. A backup of the old file was kept.")
        }
    }

    /** Returns null on success, or a human-readable error. */
    @Synchronized
    fun save(data: AppData): String? = try {
        tmp.writeText(Codec.encode(data))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw java.io.IOException("Couldn't replace the settings file")
        }
        null
    } catch (e: Exception) {
        "Couldn't save your changes (${e.message ?: "storage error"})."
    }
}
