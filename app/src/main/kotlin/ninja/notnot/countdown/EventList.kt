package ninja.notnot.countdown

import java.time.LocalDate

/**
 * One row of the list: an Event and the id it is stored under, so a tapped row
 * knows which Event to open and every edit is written back where it came from.
 */
data class ListedEvent(val id: EventId, val stored: StoredEvent)

/**
 * The Events in the order the owner sees them: soonest first, so the next thing
 * is at the top, with an Event that has no date yet above all of them — it is
 * the one still waiting to be told what it is for, and burying it at the bottom
 * would hide the row that needs the owner most.
 *
 * The order is worked out here every time rather than kept anywhere, so a
 * changed date reorders the list by itself and there is no stored order to go
 * wrong (ADR-0008).
 *
 * Two Events on the same date are separated by title and then by id. Which of
 * them comes first does not matter; that it is the same answer every time does,
 * because a list that reshuffled between two reads of the same Events would move
 * a row out from under the owner's finger.
 */
fun eventsInOrder(events: Map<EventId, StoredEvent>): List<ListedEvent> =
    events.map { (id, stored) -> ListedEvent(id, stored) }.sortedWith(BY_HOW_SOON)

/**
 * What a row says about how soon its Event is. These are the Dial's own words,
 * so the list and the widget can never describe the same Event differently —
 * "Set a date" included, which is what a row says when its Event has just been
 * added and has no date yet.
 */
fun howSoon(stored: StoredEvent, today: LocalDate): String =
    dialState(stored.toEvent(), today).let { state ->
        listOfNotNull(state.primaryText, state.labelText).joinToString(" ")
    }

/**
 * What a row calls its Event. A title is optional — the Dial simply leaves it
 * off — but a row with nothing on it could not be told from the next one, so an
 * Event with no title is named as having none.
 */
fun rowTitle(stored: StoredEvent): String = stored.title.ifBlank { UNTITLED }

private const val UNTITLED = "Untitled"

private val BY_HOW_SOON: Comparator<ListedEvent> =
    compareBy<ListedEvent, LocalDate?>(nullsFirst(naturalOrder())) { it.stored.eventDate }
        .thenBy { it.stored.title }
        .thenBy { it.id.value }
