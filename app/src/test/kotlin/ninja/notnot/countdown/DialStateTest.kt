package ninja.notnot.countdown

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Every case in the PRD's testing section. Each states an input and an expected
 * output and says nothing about how the answer is reached.
 */
class DialStateTest {

    @Nested
    @DisplayName("A future Event")
    inner class FutureEvent {

        @Test
        fun `shows the day count and the plural label`() {
            val state = dialState(
                event = eventOn("2026-08-22"),
                today = date("2026-08-15"),
            )

            assertEquals("7", state.primaryText)
            assertEquals("Days", state.labelText)
        }

        @Test
        fun `shows the singular label one day out`() {
            val state = dialState(
                event = eventOn("2026-08-16"),
                today = date("2026-08-15"),
            )

            assertEquals("1", state.primaryText)
            assertEquals("Day", state.labelText)
        }

        @Test
        fun `carries the title and the Accent through`() {
            val state = dialState(
                event = Event(
                    eventDate = date("2026-08-22"),
                    anchorDate = date("2026-08-15"),
                    title = "Holiday",
                    accent = NamedAccent.RED.accent,
                ),
                today = date("2026-08-15"),
            )

            assertEquals("Holiday", state.title)
            assertEquals(NamedAccent.RED.accent, state.accent)
        }
    }

    @Nested
    @DisplayName("The Event Date itself")
    inner class OnTheDay {

        @Test
        fun `reads Today with no label`() {
            val state = dialState(
                event = eventOn("2026-08-15"),
                today = date("2026-08-15"),
            )

            assertEquals("Today", state.primaryText)
            assertNull(state.labelText)
        }

        @Test
        fun `fills the Progress Arc`() {
            val state = dialState(
                event = Event(
                    eventDate = date("2026-08-15"),
                    anchorDate = date("2026-01-01"),
                ),
                today = date("2026-08-15"),
            )

            assertEquals(1f, state.arcFraction)
        }
    }

    @Nested
    @DisplayName("After the Event Date")
    inner class AfterTheDay {

        @Test
        fun `counts upwards and reads Days ago`() {
            val state = dialState(
                event = eventOn("2026-08-12"),
                today = date("2026-08-15"),
            )

            assertEquals("3", state.primaryText)
            assertEquals("Days ago", state.labelText)
        }

        @Test
        fun `leaves the Progress Arc full`() {
            val state = dialState(
                event = Event(
                    eventDate = date("2026-08-12"),
                    anchorDate = date("2026-01-01"),
                ),
                today = date("2026-08-15"),
            )

            assertEquals(1f, state.arcFraction)
        }

        @Test
        fun `keeps counting a long way past the Event Date`() {
            val state = dialState(
                event = eventOn("2020-01-01"),
                today = date("2026-08-15"),
            )

            assertEquals("2418", state.primaryText)
            assertEquals("Days ago", state.labelText)
        }
    }

    @Nested
    @DisplayName("No Event")
    inner class NoEvent {

        @Test
        fun `reads Set a date, with an empty arc and no title`() {
            val state = dialState(event = null, today = date("2026-08-15"))

            assertEquals("Set a date", state.primaryText)
            assertNull(state.labelText)
            assertEquals(0f, state.arcFraction)
            assertNull(state.title)
        }

        @Test
        fun `uses the default Accent`() {
            val state = dialState(event = null, today = date("2026-08-15"))

            assertEquals(Accent.DEFAULT, state.accent)
            assertEquals(NamedAccent.BLUE.accent, state.accent)
        }
    }

    @Nested
    @DisplayName("The title")
    inner class Title {

        @Test
        fun `an absent title stays absent`() {
            val state = dialState(eventOn("2026-08-22"), date("2026-08-15"))

            assertNull(state.title)
        }

        @Test
        fun `an empty title reads as no title`() {
            val state = dialState(eventOn("2026-08-22", title = ""), date("2026-08-15"))

            assertNull(state.title)
        }

        @Test
        fun `a whitespace title reads as no title`() {
            val state = dialState(eventOn("2026-08-22", title = "  \t \n "), date("2026-08-15"))

            assertNull(state.title)
        }
    }

    @Nested
    @DisplayName("The Progress Arc")
    inner class ProgressArc {

        @Test
        fun `is empty on the Anchor Date`() {
            assertEquals(0f, arcOver(anchor = "2026-01-01", event = "2026-01-11", today = "2026-01-01"))
        }

        @Test
        fun `is half full midway`() {
            assertEquals(0.5f, arcOver(anchor = "2026-01-01", event = "2026-01-11", today = "2026-01-06"))
        }

        @Test
        fun `is full on the Event Date`() {
            assertEquals(1f, arcOver(anchor = "2026-01-01", event = "2026-01-11", today = "2026-01-11"))
        }

        @Test
        fun `is a fifth a fifth of the way through`() {
            assertEquals(0.2f, arcOver(anchor = "2026-01-01", event = "2026-01-11", today = "2026-01-03"))
        }

        @Test
        fun `is full when the Anchor Date is the Event Date`() {
            val fraction = arcOver(anchor = "2026-08-22", event = "2026-08-22", today = "2026-08-15")

            assertFalse(fraction.isNaN(), "a zero-length span must not divide by zero")
            assertEquals(1f, fraction)
        }

        @Test
        fun `is full when the Anchor Date is after the Event Date`() {
            val fraction = arcOver(anchor = "2026-10-01", event = "2026-09-01", today = "2026-08-15")

            assertFalse(fraction.isNaN(), "a negative span must not produce a NaN")
            assertEquals(1f, fraction)
        }

        @Test
        fun `is never outside 0 to 1`() {
            val anchor = date("2026-01-01")
            val offsets = listOf(-4000L, -365L, -30L, -1L, 0L, 1L, 30L, 365L, 4000L)
            for (eventOffset in offsets) {
                for (todayOffset in offsets) {
                    val fraction = dialState(
                        event = Event(
                            eventDate = anchor.plusDays(eventOffset),
                            anchorDate = anchor,
                        ),
                        today = anchor.plusDays(todayOffset),
                    ).arcFraction

                    assertFalse(fraction.isNaN(), "event $eventOffset, today $todayOffset was NaN")
                    assertTrue(
                        fraction in 0f..1f,
                        "event $eventOffset, today $todayOffset gave $fraction",
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("Counting calendar days")
    inner class CalendarDays {

        @Test
        fun `a span crossing a daylight-saving change counts like any other span`() {
            // The UK moves to summer time on 2026-03-29, so this span holds a
            // 23-hour day. Both spans are seven calendar days.
            val overTheChange = dialState(eventOn("2026-04-04"), date("2026-03-28"))
            val plainWeek = dialState(eventOn("2026-05-09"), date("2026-05-02"))

            assertEquals("7", overTheChange.primaryText)
            assertEquals(plainWeek.primaryText, overTheChange.primaryText)
            assertEquals(plainWeek.labelText, overTheChange.labelText)
        }

        @Test
        fun `the day of a daylight-saving change is still one day`() {
            val state = dialState(eventOn("2026-03-30"), date("2026-03-29"))

            assertEquals("1", state.primaryText)
            assertEquals("Day", state.labelText)
        }

        @Test
        fun `a span crossing a year boundary counts correctly`() {
            val state = dialState(eventOn("2026-01-02"), date("2025-12-30"))

            assertEquals("3", state.primaryText)
        }

        @Test
        fun `a span crossing a leap day counts the extra day`() {
            val leapYear = dialState(eventOn("2024-03-01"), date("2024-02-28"))
            val ordinaryYear = dialState(eventOn("2023-03-01"), date("2023-02-28"))

            assertEquals("2", leapYear.primaryText)
            assertEquals("1", ordinaryYear.primaryText)
        }

        @Test
        fun `a date centuries out still counts`() {
            // 374 years. A nanosecond- or hour-based calculation overflows well
            // before this; calendar days do not.
            val state = dialState(eventOn("2400-08-15"), date("2026-08-15"))

            assertEquals("136601", state.primaryText)
            assertEquals("Days", state.labelText)
        }

        @Test
        fun `a date a millennium out still counts`() {
            val state = dialState(eventOn("2999-01-01"), date("2026-08-15"))

            assertEquals("355155", state.primaryText)
        }

        @Test
        fun `a millennium-long span keeps the arc in range`() {
            val fraction = arcOver(anchor = "2026-08-15", event = "2999-01-01", today = "2500-01-01")

            assertTrue(fraction in 0f..1f, "got $fraction")
        }
    }

    @Nested
    @DisplayName("What the Dial says out loud")
    inner class SpokenAs {

        @Test
        fun `reads the number, the label and the title`() {
            val state = dialState(eventOn("2026-08-22", title = "Holiday"), date("2026-08-15"))

            assertEquals("7 Days Holiday", spokenAs(state))
        }

        @Test
        fun `leaves out what is not there`() {
            assertEquals("Today", spokenAs(dialState(eventOn("2026-08-15"), date("2026-08-15"))))
            assertEquals("Set a date", spokenAs(dialState(event = null, today = date("2026-08-15"))))
        }
    }

    @Nested
    @DisplayName("Purity")
    inner class Purity {

        @Test
        fun `the same input always gives the same answer`() {
            val event = eventOn("2026-08-22", title = "Holiday")

            assertEquals(
                dialState(event, date("2026-08-15")),
                dialState(event, date("2026-08-15")),
            )
        }

        @Test
        fun `today is a parameter, so a different today gives a different answer`() {
            val event = eventOn("2026-08-22")

            assertEquals("7", dialState(event, date("2026-08-15")).primaryText)
            assertEquals("6", dialState(event, date("2026-08-16")).primaryText)
        }

        @Test
        fun `the domain sources import nothing from Android, no clock and no IO`() {
            val banned = mapOf(
                "an Android import" to Regex("""import\s+androidx?\."""),
                "a clock lookup" to Regex("""\.now\(|currentTimeMillis|nanoTime|\b(Clock|Instant|ZoneId|ZonedDateTime|TimeZone)\b"""),
                "I/O" to Regex("""\b(java\.io|java\.nio|kotlin\.io|File|InputStream|Reader|getenv|readText)\b"""),
            )

            for (name in DOMAIN_SOURCES) {
                val source = appSource(name)
                for ((what, pattern) in banned) {
                    val hit = pattern.find(source)
                    assertNull(hit?.value, "$name contains $what: ${hit?.value}")
                }
            }
        }
    }

    private companion object {
        val DOMAIN_SOURCES = listOf(
            "Accent.kt",
            "Event.kt",
            "DialState.kt",
            "EventStorage.kt",
            "EventList.kt",
            "EventDates.kt",
        )

        fun date(iso: String): LocalDate = LocalDate.parse(iso)

        /** An Event whose Anchor Date is far enough back not to matter. */
        fun eventOn(eventDate: String, title: String? = null) = Event(
            eventDate = date(eventDate),
            anchorDate = date(eventDate).minusDays(1000),
            title = title,
        )

        fun arcOver(anchor: String, event: String, today: String): Float = dialState(
            event = Event(eventDate = date(event), anchorDate = date(anchor)),
            today = date(today),
        ).arcFraction
    }
}
