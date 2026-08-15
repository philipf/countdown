package ninja.notnot.countdown

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.random.Random

/**
 * An Event's identity: what a key says the Event it belongs to is, and what a
 * widget will name the Event it shows. The owner never sees it and never types
 * it.
 *
 * It ends up inside a preference key, so it is made of a fixed alphabet by
 * [newEventId] rather than being anything the owner chose.
 */
@JvmInline
value class EventId(val value: String)

/**
 * One Event as it is stored, which is not always a whole Event: the owner can
 * type a title or pick an Accent before choosing a date, and until there is an
 * Event Date there is no Event. Nothing is invented to fill the gap.
 *
 * Every rule about what is stored, what an edit does to the Anchor Date, and how
 * many Events share one file is in this file, so it can be checked on the JVM.
 * Reaching the disk is [EventStore]'s job and it decides nothing.
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

/**
 * The keys everything is stored under: one [EVENTS] key saying which Events
 * exist, and four fields per Event under [keyFor]. See ADR-0008.
 *
 * The four field names are also the keys v1 used, back when there was one Event
 * and it needed no prefix. That is what [v1EventCarriedOver] reads.
 */
object EventKeys {
    const val EVENT_DATE = "eventDate"
    const val ANCHOR_DATE = "anchorDate"
    const val TITLE = "title"
    const val ACCENT = "accent"

    /** Every field, for a reader that has to ask for them one at a time. */
    val ALL = listOf(EVENT_DATE, ANCHOR_DATE, TITLE, ACCENT)

    /**
     * The key listing the ids that exist. This is what says an Event exists, not
     * the presence of its fields: a write that was interrupted leaves fields
     * behind with no id listed, and those read as nothing rather than as half an
     * Event.
     */
    const val EVENTS = "events"

    /** Where [field] is kept for the Event with [id]. */
    fun keyFor(id: EventId, field: String): String = "$PREFIX${id.value}.$field"

    private const val PREFIX = "event."
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
        accent = accentFrom(values[EventKeys.ACCENT]),
    )
}

/** What to store. A null value means the key is removed rather than written. */
fun StoredEvent.toValues(): Map<String, String?> = mapOf(
    EventKeys.EVENT_DATE to eventDate?.toString(),
    EventKeys.ANCHOR_DATE to eventDate?.let { anchorDate?.toString() },
    // A blank title is no title, so nothing is kept for it.
    EventKeys.TITLE to title.takeIf { it.isNotBlank() },
    EventKeys.ACCENT to accent.toStoredValue(),
)

/**
 * Every Event stored, by id. The whole file goes in, since a read of one Event
 * and a read of all of them cost the same: [EventKeys.EVENTS] says which ids
 * exist and each one's fields are read through [storedEventFrom] with the prefix
 * taken off.
 *
 * Fields left behind by an id that is not listed are not read, so a write that
 * was interrupted reads as nothing rather than as a partial Event.
 */
fun storedEventsFrom(values: Map<String, String?>): Map<EventId, StoredEvent> =
    eventIdsFrom(values).associateWith { id ->
        storedEventFrom(EventKeys.ALL.associateWith { values[EventKeys.keyFor(id, it)] })
    }

/**
 * The ids that exist, in whatever order they are listed. That order says
 * nothing: the order the owner sees is worked out from the Event Dates, so there
 * is no stored order to keep correct.
 */
fun eventIdsFrom(values: Map<String, String?>): List<EventId> =
    values[EventKeys.EVENTS].orEmpty()
        .split(ID_SEPARATOR)
        .filter { it.isNotBlank() }
        .distinct()
        .map(::EventId)

/**
 * The whole file with [stored] written as the Event with [id], and the id listed
 * if it was not listed already. Every key that is not this Event's is left as it
 * was, so an edit cannot reach another Event's fields or its Anchor Date.
 *
 * A null value means the key is removed rather than written, as it does in
 * [toValues].
 */
fun Map<String, String?>.withEvent(id: EventId, stored: StoredEvent): Map<String, String?> {
    val ids = eventIdsFrom(this)
    return this +
        stored.toValues().mapKeys { (field, _) -> EventKeys.keyFor(id, field) } +
        (EventKeys.EVENTS to listedAs(if (id in ids) ids else ids + id))
}

/**
 * The whole file with the Event with [id] taken away: its id off the list and
 * its four keys removed. Every key that is not this Event's is left as it was,
 * so a delete cannot reach another Event's fields.
 *
 * Being listed is what makes an Event exist (ADR-0008), so a delete that was
 * interrupted leaves fields nothing reads rather than half an Event, the same
 * way an interrupted write does.
 *
 * Deleting the last Event takes the list of ids away with it, so a file with no
 * Events in it looks the way it did before there were any.
 */
fun Map<String, String?>.withoutEvent(id: EventId): Map<String, String?> {
    val left = eventIdsFrom(this) - id
    val fieldsGone = this - EventKeys.ALL.map { EventKeys.keyFor(id, it) }.toSet()
    return if (left.isEmpty()) {
        fieldsGone - EventKeys.EVENTS
    } else {
        fieldsGone + (EventKeys.EVENTS to listedAs(left))
    }
}

/**
 * The file with v1's one Event carried over: the unprefixed fields become the
 * Event with [id] and are taken away, so the read after this one finds it stored
 * the new way and never looks at that shape again. Null when there is nothing to
 * carry over, which is every run but the first one after the upgrade.
 *
 * An Event Date is what says v1 had an Event. Without one there was nothing to
 * count to and so nothing worth keeping.
 */
fun v1EventCarriedOver(values: Map<String, String?>, id: EventId): Map<String, String?>? {
    if (values[EventKeys.EVENT_DATE] == null) return null
    val carried = storedEventFrom(values)
    return values.minus(EventKeys.ALL.toSet()).withEvent(id, carried)
}

/**
 * An id that no Event in [taken] has.
 *
 * Ids are drawn rather than counted. A widget holds the id of the Event it shows
 * (ADR-0009), so an id handed out twice would point a widget already on the home
 * screen at a different Event with nothing looking wrong. Out of this many
 * places of this alphabet, a draw does not come up twice — including after the
 * Event that held an id has gone and nothing remembers that it was ever used.
 */
fun newEventId(taken: Set<EventId> = emptySet(), random: Random = Random.Default): EventId {
    while (true) {
        val drawn = EventId(
            (1..ID_LENGTH).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }
                .joinToString(""),
        )
        if (drawn !in taken) return drawn
    }
}

/** Letters and digits only, so an id is safe to put in the middle of a key. */
private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/** Long enough that a draw is not worth worrying about, short enough to read. */
private const val ID_LENGTH = 12

/** No id can contain it, so the list of them needs no escaping. */
private const val ID_SEPARATOR = ","

private fun listedAs(ids: List<EventId>): String = ids.joinToString(ID_SEPARATOR) { it.value }

private fun parseDate(value: String?): LocalDate? =
    value?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }
