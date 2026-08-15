package ninja.notnot.countdown

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The Event as it is stored, which is not always a whole Event: the owner can
 * type a title or pick an Accent before choosing a date, and until there is an
 * Event Date there is no Event. Nothing is invented to fill the gap.
 *
 * Every rule about what is stored and what an edit does to the Anchor Date is in
 * this file, so it can be checked on the JVM. Reaching the disk is [EventStore]'s
 * job and it decides nothing.
 *
 * @param eventDate the chosen Event Date, or null when none has been chosen.
 * @param anchorDate the day the Event Date was last chosen.
 * @param title what the owner has typed, which may be blank.
 * @param accent the chosen Accent.
 */
data class StoredEvent(
    val eventDate: LocalDate? = null,
    val anchorDate: LocalDate? = null,
    val title: String = "",
    val accent: Accent = Accent.DEFAULT,
) {
    companion object {
        /** What is stored on first run, before the owner has touched anything. */
        val NOTHING_SET = StoredEvent()
    }
}

/** The Event, or null while there is no Event Date and so nothing to count to. */
fun StoredEvent.toEvent(): Event? {
    val eventDate = eventDate ?: return null
    return Event(
        eventDate = eventDate,
        // A store that lost its Anchor Date is read as anchored on the Event
        // Date. That fills the Progress Arc, which is wrong but harmless, and it
        // beats having no Event at all.
        anchorDate = anchorDate ?: eventDate,
        title = title.takeIf { it.isNotBlank() },
        accent = accent,
    )
}

/**
 * Chooses the Event Date. This is the only edit that moves the Anchor Date: the
 * arc measures the wait the owner is actually in, which starts today.
 *
 * Picking the same date again is not a change, so it leaves the Anchor Date
 * where it is rather than restarting a countdown the owner did not alter.
 */
fun StoredEvent.withEventDate(eventDate: LocalDate, today: LocalDate): StoredEvent =
    if (eventDate == this.eventDate) this else copy(eventDate = eventDate, anchorDate = today)

/** Renames the Event. The Anchor Date stays put, so renaming does not restart the arc. */
fun StoredEvent.withTitle(title: String): StoredEvent = copy(title = title)

/** Recolours the Event. The Anchor Date stays put. */
fun StoredEvent.withAccent(accent: Accent): StoredEvent = copy(accent = accent)

/** The keys the Event is stored under. */
object EventKeys {
    const val EVENT_DATE = "eventDate"
    const val ANCHOR_DATE = "anchorDate"
    const val TITLE = "title"
    const val ACCENT = "accent"

    /** Every key, for a reader that has to ask for them one at a time. */
    val ALL = listOf(EVENT_DATE, ANCHOR_DATE, TITLE, ACCENT)
}

/**
 * Reads what is stored. A missing or unreadable value reads as unset rather than
 * as an error: the worst case is the owner picking their date again, which is
 * cheaper than a crash on launch.
 */
fun storedEventFrom(values: Map<String, String?>): StoredEvent {
    val eventDate = parseDate(values[EventKeys.EVENT_DATE])
    return StoredEvent(
        eventDate = eventDate,
        // An Anchor Date with no Event Date measures nothing, so it is ignored.
        anchorDate = eventDate?.let { parseDate(values[EventKeys.ANCHOR_DATE]) },
        title = values[EventKeys.TITLE].orEmpty(),
        accent = Accent.entries.firstOrNull { it.name == values[EventKeys.ACCENT] }
            ?: Accent.DEFAULT,
    )
}

/** What to store. A null value means the key is removed rather than written. */
fun StoredEvent.toValues(): Map<String, String?> = mapOf(
    EventKeys.EVENT_DATE to eventDate?.toString(),
    EventKeys.ANCHOR_DATE to eventDate?.let { anchorDate?.toString() },
    // A blank title is no title, so nothing is kept for it.
    EventKeys.TITLE to title.takeIf { it.isNotBlank() },
    EventKeys.ACCENT to accent.name,
)

private fun parseDate(value: String?): LocalDate? =
    value?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }
