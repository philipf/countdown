package ninja.notnot.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId

/**
 * The alarm that brings the Dial to the next Day Rollover, and the receiver that
 * hears it.
 *
 * Inexact, per ADR-0003: nobody looks at the widget at 00:00, so a few minutes'
 * drift costs nothing, and an exact alarm would need `SCHEDULE_EXACT_ALARM`.
 * `setAndAllowWhileIdle` is what gets through Doze without that permission.
 * `updatePeriodMillis` is not used at all — its floor is half an hour and it
 * wakes the phone 48 times a day to redraw a number that changes once.
 *
 * The alarm sets itself again when it fires, because there is no inexact
 * repeating alarm that can follow local midnight: the interval would have to
 * change with every daylight-saving shift and every flight. Each arming asks
 * [nextDayRollover] afresh, so the time is right for the zone the phone is in
 * now.
 *
 * Set alarms do not survive a reboot, and one set before a flight points at the
 * old zone's midnight, so [DayRolloverReceiver] listens for the three system
 * broadcasts that mean either of those has happened.
 */
class DayRolloverReceiver : BroadcastReceiver() {
    /**
     * The Day Rollover alarm, `BOOT_COMPLETED`, `TIME_SET` and
     * `TIMEZONE_CHANGED` all mean the same two things: the Dial on the home
     * screen may be showing the wrong day, and the alarm is either gone or aimed
     * at the wrong instant. [drawDialForToday] answers both, so there is nothing
     * here to tell them apart.
     */
    override fun onReceive(context: Context, intent: Intent) {
        drawDialForToday(context)
    }
}

/**
 * Sets the alarm for the next Day Rollover, replacing any alarm already set.
 *
 * Replacing rather than stacking is [dayRolloverAlarm]'s doing: the same request
 * code and an equal `Intent` give the same `PendingIntent`, and `AlarmManager`
 * cancels an alarm holding that before setting the new one. So arming twice, or
 * a hundred times while a title is typed, still leaves exactly one alarm
 * pending.
 */
fun armDayRollover(context: Context) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val rollover = nextDayRollover(Instant.now(), ZoneId.systemDefault())
    // RTC, because it is a wall clock time rather than an interval, and waking
    // for it so the number is already right when the screen comes on. Once a
    // day is a wakeup worth spending.
    alarms.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        rollover.toEpochMilli(),
        dayRolloverAlarm(context),
    )
}

/** Drops the alarm, for when there is no widget left to redraw. */
fun cancelDayRollover(context: Context) {
    context.getSystemService(AlarmManager::class.java)?.cancel(dayRolloverAlarm(context))
}

/**
 * What the alarm delivers, and what cancelling it names.
 *
 * Both go through here so the two cannot describe different alarms. Two
 * `PendingIntent`s are the same one when the request code matches and the
 * `Intent`s match on everything but their extras — action, data, type,
 * component. This one carries no extras at all and its action is a constant, so
 * every call builds the same alarm. The action is the app's own rather than one
 * of the system's: the receiver is named directly, so nothing routes on it, but
 * it keeps this alarm distinct from anything else and tells whoever reads
 * `dumpsys alarm` what it is.
 */
private fun dayRolloverAlarm(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context.applicationContext,
        DAY_ROLLOVER_REQUEST_CODE,
        Intent(context.applicationContext, DayRolloverReceiver::class.java)
            .setAction(ACTION_DAY_ROLLOVER),
        // Immutable: nobody may edit the alarm this app set. Update current, so
        // the one PendingIntent is reused rather than a second one made.
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private const val ACTION_DAY_ROLLOVER = "ninja.notnot.countdown.DAY_ROLLOVER"

/** The app's only alarm, so its request code is the only one there is. */
private const val DAY_ROLLOVER_REQUEST_CODE = 0
