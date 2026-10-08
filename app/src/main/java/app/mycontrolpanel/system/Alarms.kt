package app.mycontrolpanel.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mycontrolpanel.App
import app.mycontrolpanel.MainActivity
import app.mycontrolpanel.R
import app.mycontrolpanel.core.AppData
import app.mycontrolpanel.core.NotifyStyle
import app.mycontrolpanel.core.Scheduler
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Scheduled-routine reminders. Only routines set to "card + quiet notification" use alarms.
 * One inexact-while-idle alarm for the soonest routine; it reschedules itself after firing.
 * The channel is LOW importance: no sound, no pop-up — it should never startle you.
 */
object Alarms {
    const val CHANNEL = "routine_reminders"
    const val EXTRA_ROUTINE = "routineId"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Routine reminders", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Quiet reminders for scheduled routines"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun scheduleNext(ctx: Context, data: AppData) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val next = data.routines
            .filter { it.enabled && it.schedule?.notify == NotifyStyle.NOTIFY }
            .mapNotNull { r -> Scheduler.nextRoutineFire(r, now)?.let { r to it } }
            .minByOrNull { it.second }
        val pi = pending(ctx, next?.first?.id)
        am.cancel(pi)
        if (next != null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Scheduler.toEpoch(next.second, zone), pending(ctx, next.first.id))
        }
    }

    private fun pending(ctx: Context, routineId: String?): PendingIntent {
        val i = Intent(ctx, RoutineAlarmReceiver::class.java).putExtra(EXTRA_ROUTINE, routineId)
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun notificationsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, routineId: String, name: String) {
        if (!notificationsAllowed(ctx)) return
        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_ROUTINE, routineId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, routineId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_panel)
            .setContentTitle(name)
            .setContentText("Time for this routine — tap to start.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(routineId.hashCode(), n) } catch (_: SecurityException) { }
    }
}

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val data = app.store.state.value.data
        val id = intent.getStringExtra(Alarms.EXTRA_ROUTINE)
        val routine = data.routines.find { it.id == id }
        // If the panel is on screen, the dashboard card is enough — no notification.
        if (routine != null && !app.inForeground) Alarms.notify(context, routine.id, routine.name)
        Alarms.scheduleNext(context, data)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        Alarms.scheduleNext(context, app.store.state.value.data)
    }
}
