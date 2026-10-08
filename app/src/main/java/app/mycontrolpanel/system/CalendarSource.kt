package app.mycontrolpanel.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.mycontrolpanel.core.CalEvent
import app.mycontrolpanel.core.CalendarStatus

/**
 * Reads events from the calendars synced to this device (Google Calendar included) through Android's
 * CalendarContract. Read-only, no network, no Google API keys.
 */
object CalendarSource {
    fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events overlapping [from, to). Returns status + events; never throws. */
    fun load(ctx: Context, from: Long, to: Long): Pair<CalendarStatus, List<CalEvent>> {
        if (!allowed(ctx)) return CalendarStatus.NEEDS_PERMISSION to emptyList()
        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to)
            }.build()
            val cols = arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.STATUS, CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            )
            val out = ArrayList<CalEvent>()
            ctx.contentResolver.query(uri, cols, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(6) == CalendarContract.Instances.STATUS_CANCELED) continue
                    if (c.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(2); var end = c.getLong(3)
                    if (allDay) { // all-day events are stored in UTC midnight; shift to local midnight
                        val tz = java.util.TimeZone.getDefault()
                        begin -= tz.getOffset(begin); end -= tz.getOffset(end)
                    }
                    out += CalEvent("${c.getLong(0)}-$begin", c.getString(1) ?: "(No title)", begin, end, allDay, c.getString(5) ?: "")
                }
            }
            CalendarStatus.OK to out
        } catch (e: Exception) {
            CalendarStatus.ERROR to emptyList()
        }
    }
}
