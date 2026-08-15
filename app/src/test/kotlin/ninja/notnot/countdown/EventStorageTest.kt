package ninja.notnot.countdown

import java.time.LocalDate
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What is stored, what is read back, what each edit does to the Anchor Date, and
 * how many Events share one file. Everything here is a value in and a value out;
 * nothing touches a disk.
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
        fun `every Accent is written under its own name and reads back as itself`() {
            for (accent in Accent.entries) {
                val values = anchoredEvent().withAccent(accent).toValues()

                assertEquals(accent.name, values[EventKeys.ACCENT])
                assertEquals(accent, storedEventFrom(values).accent, "$accent came back as something else")
            }
        }

        @Test
        fun `the names in use before the palette grew still read as the colours they did`() {
            // An Event stored by an older build names its Accent, not its place
            // in the list, so adding colours to the end of the list cannot
            // recolour an Event already on someone's phone.
            for (name in listOf("BLUE", "BLACK", "MID_GREY", "RED")) {
                val stored = storedEventFrom(mapOf(EventKeys.ACCENT to name))

                assertEquals(name, stored.accent.name)
            }
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

    @Nested
    @DisplayName("Many Events in one file")
    inner class ManyEvents {

        @Test
        fun `several Events are read back, each with its own fields`() {
            assertEquals(
                mapOf(CHRISTMAS_ID to christmas(), HOLIDAY_ID to holiday()),
                read(twoEvents()),
            )
        }

        @Test
        fun `editing one Event leaves the other's fields and Anchor Date alone`() {
            val edited = twoEvents().withEvent(
                HOLIDAY_ID,
                holiday().withEventDate(date("2026-10-01"), today = date("2026-08-16")),
            )

            assertEquals(christmas(), read(edited)[CHRISTMAS_ID])
            assertEquals(date("2026-08-16"), read(edited)[HOLIDAY_ID]?.anchorDate)
        }

        @Test
        fun `renaming one Event does not rename another`() {
            val edited = twoEvents().withEvent(HOLIDAY_ID, holiday().withTitle("Two weeks off"))

            assertEquals("Christmas", read(edited)[CHRISTMAS_ID]?.title)
            assertEquals("Two weeks off", read(edited)[HOLIDAY_ID]?.title)
        }

        @Test
        fun `writing an Event again does not list it twice`() {
            val written = twoEvents().withEvent(CHRISTMAS_ID, christmas().withAccent(Accent.BLACK))

            assertEquals(listOf(CHRISTMAS_ID, HOLIDAY_ID), eventIdsFrom(written))
            assertEquals(Accent.BLACK, read(written)[CHRISTMAS_ID]?.accent)
        }

        @Test
        fun `each Event's fields are kept under its own id`() {
            val values = twoEvents()

            assertEquals("2027-12-25", values["event.${CHRISTMAS_ID.value}.eventDate"])
            assertEquals("2026-09-01", values["event.${HOLIDAY_ID.value}.eventDate"])
        }

        @Test
        fun `fields belonging to an id that is not listed read as nothing`() {
            // What a write that was interrupted leaves behind: the fields are
            // there and the id never made it into the list.
            val orphaned = twoEvents() + (EventKeys.EVENTS to CHRISTMAS_ID.value)

            assertEquals(mapOf(CHRISTMAS_ID to christmas()), read(orphaned))
        }

        @Test
        fun `nothing listed is no Events at all, whatever fields are lying about`() {
            assertEquals(emptyMap<EventId, StoredEvent>(), read(twoEvents() - EventKeys.EVENTS))
        }

        @Test
        fun `an id listed with no fields is an Event with nothing set`() {
            val values = mapOf<String, String?>(EventKeys.EVENTS to CHRISTMAS_ID.value)

            assertEquals(mapOf(CHRISTMAS_ID to StoredEvent.NOTHING_SET), read(values))
        }
    }

    @Nested
    @DisplayName("Deleting an Event")
    inner class Deleting {

        @Test
        fun `the Event is gone`() {
            val left = twoEvents().withoutEvent(HOLIDAY_ID)

            assertNull(read(left)[HOLIDAY_ID])
            assertEquals(listOf(CHRISTMAS_ID), eventIdsFrom(left))
        }

        @Test
        fun `its keys go with it`() {
            val left = twoEvents().withoutEvent(HOLIDAY_ID)

            for (field in EventKeys.ALL) {
                assertNull(left[EventKeys.keyFor(HOLIDAY_ID, field)], "$field was left behind")
            }
        }

        @Test
        fun `every other Event is readable and unchanged`() {
            val before = twoEvents()

            val left = before.withoutEvent(HOLIDAY_ID)

            assertEquals(mapOf(CHRISTMAS_ID to christmas()), read(left))
            for (field in EventKeys.ALL) {
                val key = EventKeys.keyFor(CHRISTMAS_ID, field)
                assertEquals(before[key], left[key], key)
            }
        }

        @Test
        fun `deleting the last Event leaves the file as it was before there were any`() {
            val left = twoEvents().withoutEvent(CHRISTMAS_ID).withoutEvent(HOLIDAY_ID)

            assertEquals(emptyMap<String, String?>(), left)
        }

        @Test
        fun `deleting the last Event leaves nothing to list, so the list says what to do`() {
            val left = twoEvents().withoutEvent(CHRISTMAS_ID).withoutEvent(HOLIDAY_ID)

            assertEquals(emptyList<ListedEvent>(), eventsInOrder(read(left)))
        }

        @Test
        fun `deleting an Event that is not there changes nothing`() {
            assertEquals(twoEvents(), twoEvents().withoutEvent(EventId("neverstored")))
        }

        @Test
        fun `nothing is left for an Event stored under the same id to pick up`() {
            // Ids are never handed out twice, so this is not something the app
            // does. It is what says the fields are gone rather than merely
            // unlisted.
            val left = twoEvents().withoutEvent(HOLIDAY_ID)

            val again = left.withEvent(HOLIDAY_ID, StoredEvent.NOTHING_SET)

            assertEquals(StoredEvent.NOTHING_SET, read(again)[HOLIDAY_ID])
        }
    }

    @Nested
    @DisplayName("Event ids")
    inner class Ids {

        @Test
        fun `are safe to put in the middle of a key`() {
            val random = Random(SEED)

            repeat(DRAWS) {
                val id = newEventId(random = random).value

                assertTrue(id.matches(Regex("[a-z0-9]+")), "<$id> cannot be part of a key")
            }
        }

        @Test
        fun `name the keys the Event's fields are kept under`() {
            assertEquals("event.abc.title", EventKeys.keyFor(EventId("abc"), EventKeys.TITLE))
        }

        @Test
        fun `are never handed out twice, even when nothing remembers the last one`() {
            // Nothing is taken on any of these draws, which is how it is once the
            // Event that held an id has gone: what keeps ids apart is the size of
            // the draw, not a record of the ones handed out.
            val random = Random(SEED)

            val drawn = List(DRAWS) { newEventId(random = random) }

            assertEquals(DRAWS, drawn.toSet().size, "an id came up twice")
        }

        @Test
        fun `are never the id of an Event that already exists`() {
            // The same seed would hand out the same id again, so the only way
            // past this is to draw until the id is free.
            val taken = newEventId(random = Random(SEED))

            val next = newEventId(taken = setOf(taken), random = Random(SEED))

            assertNotEquals(taken, next)
        }
    }

    @Nested
    @DisplayName("Upgrading from the one Event v1 stored")
    inner class UpgradingFromV1 {

        @Test
        fun `keeps the Event, with its date, title, Accent and Anchor Date`() {
            val carried = carriedOver()

            assertEquals(mapOf(FRESH_ID to christmas()), read(carried))
        }

        @Test
        fun `leaves the Dial showing the same thing`() {
            val today = date("2026-08-20")
            val carried = carriedOver()

            assertEquals(
                dialState(storedEventFrom(v1Store()).toEvent(), today),
                dialState(read(carried).getValue(FRESH_ID).toEvent(), today),
            )
        }

        @Test
        fun `takes the old unprefixed keys away`() {
            val carried = carriedOver()

            for (key in EventKeys.ALL) {
                assertNull(carried[key], "$key was left behind")
            }
        }

        @Test
        fun `happens once, because afterwards there is nothing of v1 to read`() {
            val carried = carriedOver()

            assertNull(v1EventCarriedOver(carried, EventId("second")))
        }

        @Test
        fun `is not attempted on a first run, where there is nothing at all`() {
            assertNull(v1EventCarriedOver(emptyMap(), FRESH_ID))
        }

        @Test
        fun `is not attempted for a v1 store that never got as far as a date`() {
            val started = mapOf<String, String?>(
                EventKeys.TITLE to "Christmas",
                EventKeys.ACCENT to "RED",
            )

            assertNull(v1EventCarriedOver(started, FRESH_ID))
        }

        @Test
        fun `leaves the Events that are already stored the new way alone`() {
            val carried = carriedOver(v1Store() + twoEvents())

            assertEquals(christmas(), read(carried)[CHRISTMAS_ID])
            assertEquals(holiday(), read(carried)[HOLIDAY_ID])
        }
    }

    private companion object {
        val CHRISTMAS_ID = EventId("one")
        val HOLIDAY_ID = EventId("two")
        val FRESH_ID = EventId("fresh")

        /** Fixed, so a test that draws ids says the same thing on every run. */
        const val SEED = 20260816

        /** Enough draws that two the same would show up. */
        const val DRAWS = 10_000

        fun date(iso: String): LocalDate = LocalDate.parse(iso)

        fun anchoredEvent(title: String = "Christmas") = StoredEvent(
            eventDate = date("2027-12-25"),
            anchorDate = date("2026-08-15"),
            title = title,
            accent = Accent.BLUE,
        )

        fun christmas() = StoredEvent(
            eventDate = date("2027-12-25"),
            anchorDate = date("2026-08-15"),
            title = "Christmas",
            accent = Accent.RED,
        )

        fun holiday() = StoredEvent(
            eventDate = date("2026-09-01"),
            anchorDate = date("2026-08-01"),
            title = "Holiday",
            accent = Accent.BLACK,
        )

        /** A file holding two Events, as it would be after writing them one at a time. */
        fun twoEvents(): Map<String, String?> = emptyMap<String, String?>()
            .withEvent(CHRISTMAS_ID, christmas())
            .withEvent(HOLIDAY_ID, holiday())

        /** A file as v1 left it: the one Event, under keys with no id in them. */
        fun v1Store(): Map<String, String?> = mapOf(
            EventKeys.EVENT_DATE to "2027-12-25",
            EventKeys.ANCHOR_DATE to "2026-08-15",
            EventKeys.TITLE to "Christmas",
            EventKeys.ACCENT to "RED",
        )

        fun read(values: Map<String, String?>): Map<EventId, StoredEvent> =
            storedEventsFrom(values)

        /** [values] after v1's Event is carried over, which here there always is. */
        fun carriedOver(values: Map<String, String?> = v1Store()): Map<String, String?> =
            checkNotNull(v1EventCarriedOver(values, FRESH_ID)) { "nothing was carried over" }
    }
}
