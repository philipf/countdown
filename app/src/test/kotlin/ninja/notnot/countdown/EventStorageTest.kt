package ninja.notnot.countdown

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What is stored, what is read back, and what each edit does to the Anchor Date.
 * Everything here is a value in and a value out; nothing touches a disk.
 */
class EventStorageTest {

    @Nested
    @DisplayName("First run")
    inner class FirstRun {

        @Test
        fun `an empty store holds no Event`() {
            val stored = storedEventFrom(emptyMap())

            assertEquals(StoredEvent.NOTHING_SET, stored)
            assertNull(stored.toEvent())
        }

        @Test
        fun `no Event means the Dial says to set a date`() {
            val state = dialState(storedEventFrom(emptyMap()).toEvent(), today = date("2026-08-15"))

            assertEquals("Set a date", state.primaryText)
        }

        @Test
        fun `the Accent starts blue`() {
            assertEquals(Accent.BLUE, StoredEvent.NOTHING_SET.accent)
            assertEquals(Accent.BLUE, storedEventFrom(emptyMap()).accent)
        }
    }

    @Nested
    @DisplayName("The Anchor Date")
    inner class AnchorDate {

        @Test
        fun `choosing the first Event Date anchors it to today`() {
            val stored = StoredEvent.NOTHING_SET
                .withEventDate(date("2027-12-25"), today = date("2026-08-15"))

            assertEquals(date("2026-08-15"), stored.anchorDate)
        }

        @Test
        fun `choosing a different Event Date moves the anchor to today`() {
            val stored = anchoredEvent()
                .withEventDate(date("2028-01-01"), today = date("2026-08-15"))

            assertEquals(date("2026-08-15"), stored.anchorDate)
        }

        @Test
        fun `choosing the same Event Date again leaves the anchor alone`() {
            val before = anchoredEvent()

            val after = before.withEventDate(before.eventDate!!, today = date("2026-08-15"))

            assertEquals(before, after)
        }

        @Test
        fun `renaming leaves the anchor alone`() {
            val before = anchoredEvent()

            val after = before.withTitle("Something else")

            assertEquals(before.anchorDate, after.anchorDate)
            assertEquals("Something else", after.title)
        }

        @Test
        fun `recolouring leaves the anchor alone`() {
            val before = anchoredEvent()

            val after = before.withAccent(Accent.RED)

            assertEquals(before.anchorDate, after.anchorDate)
            assertEquals(Accent.RED, after.accent)
        }

        @Test
        fun `renaming does not restart the Progress Arc`() {
            val before = StoredEvent.NOTHING_SET
                .withEventDate(date("2026-08-25"), today = date("2026-08-15"))
            val today = date("2026-08-20")

            val arcBefore = dialState(before.toEvent(), today).arcFraction
            val arcAfter = dialState(before.withTitle("Holiday").toEvent(), today).arcFraction

            assertEquals(arcBefore, arcAfter)
        }
    }

    @Nested
    @DisplayName("Reading the Event out")
    inner class ReadingTheEvent {

        @Test
        fun `there is no Event until an Event Date is chosen`() {
            val stored = StoredEvent.NOTHING_SET.withTitle("Holiday").withAccent(Accent.RED)

            assertNull(stored.toEvent())
        }

        @Test
        fun `an Event Date is enough for an Event`() {
            val stored = StoredEvent.NOTHING_SET
                .withEventDate(date("2027-12-25"), today = date("2026-08-15"))

            assertEquals(
                Event(
                    eventDate = date("2027-12-25"),
                    anchorDate = date("2026-08-15"),
                    title = null,
                    accent = Accent.BLUE,
                ),
                stored.toEvent(),
            )
        }

        @Test
        fun `a blank title is no title`() {
            for (title in listOf("", " ", "\t \n")) {
                assertNull(anchoredEvent(title = title).toEvent()?.title, "title <$title>")
            }
        }

        @Test
        fun `a lost Anchor Date falls back to the Event Date`() {
            val stored = StoredEvent(eventDate = date("2027-12-25"), anchorDate = null)

            assertEquals(date("2027-12-25"), stored.toEvent()?.anchorDate)
        }
    }

    @Nested
    @DisplayName("What is written")
    inner class Writing {

        @Test
        fun `a whole Event survives a round trip`() {
            val stored = StoredEvent(
                eventDate = date("2027-12-25"),
                anchorDate = date("2026-08-15"),
                title = "Christmas",
                accent = Accent.RED,
            )

            assertEquals(stored, storedEventFrom(stored.toValues()))
        }

        @Test
        fun `an untouched store writes no dates and no title`() {
            val values = StoredEvent.NOTHING_SET.toValues()

            assertNull(values[EventKeys.EVENT_DATE])
            assertNull(values[EventKeys.ANCHOR_DATE])
            assertNull(values[EventKeys.TITLE])
            assertEquals("BLUE", values[EventKeys.ACCENT])
        }

        @Test
        fun `a blank title is stored as no title at all`() {
            assertNull(anchoredEvent(title = "   ").toValues()[EventKeys.TITLE])
        }

        @Test
        fun `an Anchor Date with no Event Date is not stored`() {
            val values = StoredEvent(eventDate = null, anchorDate = date("2026-08-15")).toValues()

            assertNull(values[EventKeys.ANCHOR_DATE])
        }

        @Test
        fun `dates are written in a form that reads back`() {
            val values = anchoredEvent().toValues()

            assertEquals("2027-12-25", values[EventKeys.EVENT_DATE])
            assertEquals("2026-08-15", values[EventKeys.ANCHOR_DATE])
        }

        @Test
        fun `every key written is a key read`() {
            assertEquals(EventKeys.ALL.toSet(), anchoredEvent().toValues().keys)
        }
    }

    @Nested
    @DisplayName("A store that makes no sense")
    inner class Nonsense {

        @Test
        fun `an unreadable Event Date reads as no Event`() {
            val stored = storedEventFrom(mapOf(EventKeys.EVENT_DATE to "next Tuesday"))

            assertNull(stored.eventDate)
            assertNull(stored.toEvent())
        }

        @Test
        fun `an unreadable Anchor Date leaves the Event standing`() {
            val stored = storedEventFrom(
                mapOf(
                    EventKeys.EVENT_DATE to "2027-12-25",
                    EventKeys.ANCHOR_DATE to "",
                ),
            )

            assertEquals(date("2027-12-25"), stored.toEvent()?.anchorDate)
        }

        @Test
        fun `an unknown Accent reads as the default`() {
            val stored = storedEventFrom(mapOf(EventKeys.ACCENT to "PUCE"))

            assertEquals(Accent.DEFAULT, stored.accent)
        }

        @Test
        fun `an Anchor Date with no Event Date is dropped on the way in`() {
            val stored = storedEventFrom(mapOf(EventKeys.ANCHOR_DATE to "2026-08-15"))

            assertNull(stored.anchorDate)
        }
    }

    private companion object {
        fun date(iso: String): LocalDate = LocalDate.parse(iso)

        fun anchoredEvent(title: String = "Christmas") = StoredEvent(
            eventDate = date("2027-12-25"),
            anchorDate = date("2026-08-15"),
            title = title,
            accent = Accent.BLUE,
        )
    }
}
