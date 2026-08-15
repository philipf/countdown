package ninja.notnot.countdown

import java.time.Instant
import java.time.ZoneId

/**
 * When the next Day Rollover is: the first instant of the local day after the
 * one [now] falls in, read in [zone].
 *
 * Worked out from the zone every time, never by adding a day to the last answer.
 * A local day is 23 or 25 hours long across a daylight-saving change, and some
 * days have no midnight at all — São Paulo's clocks went from 23:59:59 on
 * 3 November 2018 straight to 01:00 on the 4th, so that day starts at 01:00.
 * `atStartOfDay` gives the first instant a day actually has; arithmetic on
 * instants gives a time that the day may not contain.
 *
 * The answer is always after [now], so an alarm set from it cannot be one that
 * has already passed. The loop is only a guard for a zone whose rules put the
 * start of tomorrow at or before now — moving the clocks back across the date
 * line does that. It runs once otherwise.
 *
 * Nothing here touches Android or reads a clock, so all of it is checked on the
 * JVM. Which zone and which instant is the caller's business.
 */
fun nextDayRollover(now: Instant, zone: ZoneId): Instant {
    var date = now.atZone(zone).toLocalDate()
    while (true) {
        date = date.plusDays(1)
        val rollover = date.atStartOfDay(zone).toInstant()
        if (rollover.isAfter(now)) return rollover
    }
}
