package app.mycontrolpanel

import android.app.Application
import app.mycontrolpanel.data.GoogleTasksSync
import app.mycontrolpanel.data.Repository
import app.mycontrolpanel.data.SyncEngine
import app.mycontrolpanel.data.Store
import app.mycontrolpanel.system.Alarms

class App : Application() {
    lateinit var store: Store
        private set
    lateinit var sync: SyncEngine
        private set
    lateinit var gtasks: GoogleTasksSync
        private set

    /** True while the dashboard is on screen; reminders then show as a card instead of a notification. */
    @Volatile var inForeground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Alarms.createChannel(this)
        store = Store(Repository(filesDir), onRoutinesChanged = { Alarms.scheduleNext(this, it) })
        sync = SyncEngine(this, store).also { it.start() }
        gtasks = GoogleTasksSync(this, store).also { it.start() }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
