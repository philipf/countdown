package ninja.notnot.countdown

import java.time.LocalDate

/**
 * One of the dated things being counted towards. Which Event this is comes from
 * the [EventId] it is stored under, so nothing that only draws an Event has to
 * carry one.
 *
 * @param eventDate the date being counted towards. A date, with no time of day.
 * @param anchorDate the date the Progress Arc measures from: the day the Event
 *   Date was last chosen. It is rewritten when the Event Date changes, and only
 *   then, so renaming or recolouring does not restart the countdown.
 * @param title what the Event is called. Blank or absent means it has no title.
 * @param accent the colour of the Progress Arc.
 */
data class Event(
    val eventDate: LocalDate,
    val anchorDate: LocalDate,
    val title: String? = null,
    val accent: Accent = Accent.DEFAULT,
)
