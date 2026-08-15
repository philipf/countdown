package ninja.notnot.countdown

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The order the list is shown in, and what each row says. Events in, rows out;
 * nothing here touches a disk or a screen.
 */
class EventListTest {

    @Nested
    @DisplayName("The order the list is shown in")
    inner class Order {

        @Test
        fun `the soonest Event is first`() {
            val events = events(
                dated("christmas", "2027-12-25"),
                dated("holiday", "2026-09-01"),
                dated("exam", "2026-10-15"),
            )

            assertEquals(listOf("holiday", "exam", "christmas"), idsInOrder(events))
        }

        @Test
        fun `an Event with no date yet is above every dated one`() {
            val events = events(
                dated("holiday", "2026-09-01"),
                undated("new"),
                dated("christmas", "2027-12-25"),
            )

            assertEquals(listOf("new", "holiday", "christmas"), idsInOrder(events))
        }

        @Test
        fun `an Event whose date has passed sits where its date puts it`() {
            // The order is the dates, and nothing else: it does not need today,
            // so it cannot change while nobody is looking at it.
            val events = events(
                dated("coming", "2026-09-01"),
                dated("gone", "2026-07-01"),
            )

            assertEquals(listOf("gone", "coming"), idsInOrder(events))
        }

        @Test
        fun `two Events on the same date always come out in the same order`() {
            val christmas = dated("christmas", "2026-12-25", title = "Christmas")
            val party = dated("party", "2026-12-25", title = "Party")

            assertEquals(
                idsInOrder(events(christmas, party)),
                idsInOrder(events(party, christmas)),
            )
        }

        @Test
        fun `two Events on the same date are separated by their titles`() {
            val events = events(
                dated("second", "2026-12-25", title = "Party"),
                dated("first", "2026-12-25", title = "Christmas"),
            )

            assertEquals(listOf("first", "second"), idsInOrder(events))
        }

        @Test
        fun `two Events on the same date with the same title are separated by their ids`() {
            val events = events(
                dated("b", "2026-12-25", title = "Christmas"),
                dated("a", "2026-12-25", title = "Christmas"),
            )

            assertEquals(listOf("a", "b"), idsInOrder(events))
        }

        @Test
        fun `Events with no date are ordered between themselves the same way`() {
            val events = events(
                undated("b", title = "Something"),
                undated("a", title = "Something"),
                undated("c", title = "Another thing"),
            )

            assertEquals(listOf("c", "a", "b"), idsInOrder(events))
        }

        @Test
        fun `the order says nothing about the order they were stored in`() {
            val christmas = dated("christmas", "2027-12-25")
            val holiday = dated("holiday", "2026-09-01")
            val new = undated("new")

            val listed = listOf(
                idsInOrder(events(christmas, holiday, new)),
                idsInOrder(events(new, christmas, holiday)),
                idsInOrder(events(holiday, new, christmas)),
            )

            assertEquals(1, listed.toSet().size, "the same Events were listed differently")
        }

        @Test
        fun `every Event is listed once, with the Event stored under its id`() {
            val holiday = dated("holiday", "2026-09-01", title = "Holiday")
            val events = events(holiday, dated("christmas", "2027-12-25"))

            val listed = eventsInOrder(events)

            assertEquals(2, listed.size)
            assertEquals(holiday, listed.first())
            assertTrue(listed.all { events[it.id] == it.stored })
        }

        @Test
        fun `no Events is an empty list`() {
            assertEquals(emptyList<ListedEvent>(), eventsInOrder(emptyMap()))
        }
    }

    @Nested
    @DisplayName("What a row says")
    inner class Rows {

        @Test
        fun `a dated Event says how far away it is, as the Dial would`() {
            val stored = dated("holiday", "2026-08-22").stored

            assertEquals("7 Days", howSoon(stored, today = date("2026-08-15")))
            assertEquals("1 Day", howSoon(stored, today = date("2026-08-21")))
        }

        @Test
        fun `an Event on its day says Today`() {
            val stored = dated("holiday", "2026-08-15").stored

            assertEquals("Today", howSoon(stored, today = date("2026-08-15")))
        }

        @Test
        fun `an Event that has passed counts up`() {
            val stored = dated("holiday", "2026-08-12").stored

            assertEquals("3 Days ago", howSoon(stored, today = date("2026-08-15")))
        }

        @Test
        fun `an Event with no date yet asks for one`() {
            val stored = undated("new").stored

            assertEquals("Set a date", howSoon(stored, today = date("2026-08-15")))
        }

        @Test
        fun `a row is called what its Event is called`() {
            assertEquals("Holiday", rowTitle(dated("holiday", "2026-09-01", "Holiday").stored))
        }

        @Test
        fun `an Event with no title is still named, so its row is not blank`() {
            for (title in listOf("", " ", "\t \n")) {
                assertEquals(
                    "Untitled",
                    rowTitle(dated("holiday", "2026-09-01", title).stored),
                    "title <$title>",
                )
            }
        }
    }

    private companion object {

        fun date(iso: String): LocalDate = LocalDate.parse(iso)

        fun dated(id: String, eventDate: String, title: String = "") = ListedEvent(
            id = EventId(id),
            stored = StoredEvent(
                eventDate = date(eventDate),
                anchorDate = date("2026-08-01"),
                title = title,
            ),
        )

        fun undated(id: String, title: String = "") =
            ListedEvent(EventId(id), StoredEvent(title = title))

        /** The Events as they come out of the store: by id, in no order at all. */
        fun events(vararg listed: ListedEvent): Map<EventId, StoredEvent> =
            listed.associate { it.id to it.stored }

        fun idsInOrder(events: Map<EventId, StoredEvent>): List<String> =
            eventsInOrder(events).map { it.id.value }
    }
}
