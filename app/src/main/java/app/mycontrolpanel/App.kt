package app.mycontrolpanel

import android.app.Application
import app.mycontrolpanel.data.Repository
import app.mycontrolpanel.data.Store
import app.mycontrolpanel.system.Alarms

class App : Application() {
    lateinit var store: Store
        private set

    /** True while the dashboard is on screen; reminders then show as a card instead of a notification. */
    @Volatile var inForeground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Alarms.createChannel(this)
        store = Store(Repository(filesDir), onRoutinesChanged = { Alarms.scheduleNext(this, it) })
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
