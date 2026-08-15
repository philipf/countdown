package ninja.notnot.countdown

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Which Event each copy of the widget shows, and what that means it draws.
 * Bindings and Events in, Dials out; nothing here touches a disk or a home
 * screen.
 */
class WidgetBindingTest {

    @Nested
    @DisplayName("What is stored for a copy of the widget")
    inner class Stored {

        @Test
        fun `a bound copy reads back the Event it was bound to`() {
            val values = emptyValues().withBinding(7, EventId("christmas"))

            assertEquals(mapOf(7 to EventId("christmas")), boundEventsFrom(values))
        }

        @Test
        fun `an empty file binds nothing`() {
            assertEquals(emptyMap<Int, EventId>(), boundEventsFrom(emptyMap()))
        }

        @Test
        fun `binding one copy leaves the others where they were`() {
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withBinding(8, EventId("holiday"))

            assertEquals(
                mapOf(7 to EventId("christmas"), 8 to EventId("holiday")),
                boundEventsFrom(values),
            )
        }

        @Test
        fun `a copy has one Event, so binding it again replaces what was there`() {
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withBinding(7, EventId("holiday"))

            assertEquals(mapOf(7 to EventId("holiday")), boundEventsFrom(values))
        }

        @Test
        fun `a removed copy takes only its own binding away`() {
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withBinding(8, EventId("holiday"))
                .withoutBindings(listOf(7))

            assertEquals(mapOf(8 to EventId("holiday")), boundEventsFrom(values))
            assertNull(values[WidgetKeys.keyFor(7)], "the key has to go, not just its value")
        }

        @Test
        fun `several copies can be removed at once, as the framework reports them`() {
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withBinding(8, EventId("holiday"))
                .withBinding(9, EventId("exam"))
                .withoutBindings(listOf(7, 9))

            assertEquals(mapOf(8 to EventId("holiday")), boundEventsFrom(values))
        }

        @Test
        fun `removing a copy that was never bound changes nothing that is bound`() {
            val values = emptyValues().withBinding(7, EventId("christmas")).withoutBindings(listOf(8))

            assertEquals(mapOf(7 to EventId("christmas")), boundEventsFrom(values))
        }

        @Test
        fun `a copy that is no longer on the home screen is forgotten`() {
            // The sweep behind onDeleted, for the copies whose removal the app
            // never heard about.
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withBinding(8, EventId("holiday"))
                .withoutBindingsBeyond(stillPlaced = setOf(8))

            assertEquals(mapOf(8 to EventId("holiday")), boundEventsFrom(values))
            assertNull(values[WidgetKeys.keyFor(7)], "the key has to go, not just its value")
        }

        @Test
        fun `a copy that is still on the home screen keeps its binding`() {
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withoutBindingsBeyond(stillPlaced = setOf(7, 8, 9))

            assertEquals(mapOf(7 to EventId("christmas")), boundEventsFrom(values))
        }

        @Test
        fun `nothing on the home screen leaves no bindings behind`() {
            val values = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withBinding(8, EventId("holiday"))
                .withoutBindingsBeyond(stillPlaced = emptySet())

            assertEquals(emptyMap<Int, EventId>(), boundEventsFrom(values))
        }

        @Test
        fun `an appWidgetId handed out again inherits nothing from the copy before it`() {
            // The copy on 7 has gone without onDeleted being heard. The next
            // redraw sweeps it, so the copy Android gives 7 to next starts with
            // no Event rather than with one it never chose.
            val swept = emptyValues()
                .withBinding(7, EventId("christmas"))
                .withoutBindingsBeyond(stillPlaced = emptySet())

            assertNull(boundEventsFrom(swept)[7])
            assertEquals(
                mapOf(7 to EventId("holiday")),
                boundEventsFrom(swept.withBinding(7, EventId("holiday"))),
            )
        }

        @Test
        fun `the sweep leaves everything that is not a binding alone`() {
            val values = mapOf<String, String?>("settings" to "something", "widget.7" to "christmas")
                .withoutBindingsBeyond(stillPlaced = emptySet())

            assertEquals("something", values["settings"])
        }

        @Test
        fun `a binding left half-written is swept like any other`() {
            val values = mapOf<String, String?>("widget.7" to "")
                .withoutBindingsBeyond(stillPlaced = emptySet())

            assertNull(values[WidgetKeys.keyFor(7)])
        }

        @Test
        fun `a sweep with nothing to forget hands back the file it was given`() {
            // Every redraw sweeps, so the usual answer has to be unchanged: the
            // store writes only when this comes back different.
            val values = emptyValues().withBinding(7, EventId("christmas"))

            assertEquals(values, values.withoutBindingsBeyond(stillPlaced = setOf(7)))
        }

        @Test
        fun `a key that is not a binding is left alone rather than read as one`() {
            val values = mapOf(
                "widget.7" to "christmas",
                "events" to "christmas,holiday",
                "event.christmas.title" to "Christmas",
                "widget." to "nonsense",
                "widget.seven" to "nonsense",
                "widgets.7" to "nonsense",
            )

            assertEquals(mapOf(7 to EventId("christmas")), boundEventsFrom(values))
        }

        @Test
        fun `a binding with nothing behind it reads as no binding`() {
            val values = mapOf("widget.7" to null, "widget.8" to "", "widget.9" to "christmas")

            assertEquals(mapOf(9 to EventId("christmas")), boundEventsFrom(values))
        }

        @Test
        fun `every copy has a key of its own`() {
            assertNotEquals(WidgetKeys.keyFor(7), WidgetKeys.keyFor(8))
            assertEquals(7, WidgetKeys.appWidgetIdIn(WidgetKeys.keyFor(7)))
        }

        @Test
        fun `a binding cannot be mistaken for one of the Events' own keys`() {
            // The two live in different files, but the key names have to differ
            // anyway: reading one file as the other must not invent a binding.
            val key = WidgetKeys.keyFor(7)

            assertNull(WidgetKeys.appWidgetIdIn(EventKeys.EVENTS))
            assertNull(WidgetKeys.appWidgetIdIn(EventKeys.keyFor(EventId("abc"), EventKeys.TITLE)))
            assertTrue(eventIdsFrom(mapOf(key to "christmas")).isEmpty())
        }
    }

    @Nested
    @DisplayName("Which Event a copy of the widget shows")
    inner class Resolving {

        @Test
        fun `a bound copy shows the Event it is bound to`() {
            val copy = PlacedWidget(7, EventId("christmas"), dialSizePx = 220)

            assertEquals(events.getValue(EventId("christmas")).toEvent(), eventShownBy(copy, events))
        }

        @Test
        fun `a copy with no binding shows no Event`() {
            assertNull(eventShownBy(PlacedWidget(7, null, dialSizePx = 220), events))
        }

        @Test
        fun `a copy bound to an Event that has been deleted shows no Event`() {
            // Not an error and not distinguishable from never having been bound:
            // what matters is that it stops showing the Event that is gone.
            assertNull(eventShownBy(PlacedWidget(7, EventId("gone"), 220), events))
        }

        @Test
        fun `an Event being deleted reaches only the copies bound to it`() {
            val christmas = PlacedWidget(7, EventId("christmas"), dialSizePx = 220)
            val holiday = PlacedWidget(8, EventId("holiday"), dialSizePx = 220)
            val afterDelete = events - EventId("christmas")

            assertNull(eventShownBy(christmas, afterDelete))
            assertEquals(eventShownBy(holiday, events), eventShownBy(holiday, afterDelete))
        }

        @Test
        fun `resolving is what the drawing is built on, so they cannot disagree`() {
            for (boundTo in listOf(EventId("christmas"), EventId("gone"), null)) {
                val copy = PlacedWidget(7, boundTo, dialSizePx = 220)
                val dials = dialsToDraw(listOf(copy), events, TODAY)

                assertEquals(dialState(eventShownBy(copy, events), TODAY), dials.single().state)
            }
        }
    }

    @Nested
    @DisplayName("What every copy of the widget draws")
    inner class Drawing {

        @Test
        fun `a copy draws the Event it is bound to`() {
            val dials = dialsToDraw(
                listOf(PlacedWidget(7, EventId("christmas"), dialSizePx = 220)),
                events,
                TODAY,
            )

            assertEquals(1, dials.size)
            assertEquals(listOf(7), dials.single().appWidgetIds)
            assertEquals("Christmas", dials.single().state.title)
            assertEquals("131", dials.single().state.primaryText)
            assertEquals(NamedAccent.RED.accent, dials.single().state.accent)
        }

        @Test
        fun `two copies on different Events show different numbers, titles and Accents`() {
            val dials = dialsToDraw(
                listOf(
                    PlacedWidget(7, EventId("christmas"), dialSizePx = 220),
                    PlacedWidget(8, EventId("holiday"), dialSizePx = 220),
                ),
                events,
                TODAY,
            )

            assertEquals(2, dials.size, "two Events cannot share one Dial")
            val states = dials.map { it.state }
            assertEquals(listOf("131", "16"), states.map { it.primaryText })
            assertEquals(listOf("Christmas", "Holiday"), states.map { it.title })
            assertEquals(listOf(NamedAccent.RED.accent, NamedAccent.BLUE.accent), states.map { it.accent })
        }

        @Test
        fun `two copies on the same Event at the same size are drawn once and share it`() {
            val dials = dialsToDraw(
                listOf(
                    PlacedWidget(7, EventId("christmas"), dialSizePx = 220),
                    PlacedWidget(8, EventId("christmas"), dialSizePx = 220),
                ),
                events,
                TODAY,
            )

            assertEquals(1, dials.size, "one Event at one size is one bitmap")
            assertEquals(listOf(7, 8), dials.single().appWidgetIds)
            assertEquals(220, dials.single().sizePx)
        }

        @Test
        fun `the same Event at two sizes is drawn at each of them`() {
            val dials = dialsToDraw(
                listOf(
                    PlacedWidget(7, EventId("christmas"), dialSizePx = 220),
                    PlacedWidget(8, EventId("christmas"), dialSizePx = 440),
                ),
                events,
                TODAY,
            )

            assertEquals(listOf(220, 440), dials.map { it.sizePx })
            assertEquals(listOf(listOf(7), listOf(8)), dials.map { it.appWidgetIds })
        }

        @Test
        fun `a copy bound to an Event that has been deleted says to set a date`() {
            // The whole Dial goes, not only the number: an arc still standing at
            // three quarters, or the deleted Event's title under it, would say
            // the Event is still being counted to.
            val dials = dialsToDraw(
                listOf(PlacedWidget(7, EventId("gone"), dialSizePx = 220)),
                events,
                TODAY,
            )
            val state = dials.single().state

            assertEquals("Set a date", state.primaryText)
            assertNull(state.labelText)
            assertEquals(0f, state.arcFraction)
            assertNull(state.title)
        }

        @Test
        fun `deleting the Event a copy shows changes that copy and no other`() {
            val placed = listOf(
                PlacedWidget(7, EventId("christmas"), dialSizePx = 220),
                PlacedWidget(8, EventId("holiday"), dialSizePx = 220),
            )

            val after = dialsToDraw(placed, events - EventId("christmas"), TODAY)

            assertEquals(dialState(null, TODAY), after.single { 7 in it.appWidgetIds }.state)
            assertEquals("Holiday", after.single { 8 in it.appWidgetIds }.state.title)
        }

        @Test
        fun `a copy with no binding at all draws the same as a first run`() {
            val unbound = dialsToDraw(listOf(PlacedWidget(7, null, 220)), events, TODAY)
            val firstRun = dialState(null, TODAY)

            assertEquals(firstRun, unbound.single().state)
        }

        @Test
        fun `nothing on the home screen is nothing to draw`() {
            assertEquals(emptyList<DialToDraw>(), dialsToDraw(emptyList(), events, TODAY))
        }

        @Test
        fun `every copy is drawn exactly once`() {
            val placed = listOf(
                PlacedWidget(7, EventId("christmas"), dialSizePx = 220),
                PlacedWidget(8, EventId("holiday"), dialSizePx = 220),
                PlacedWidget(9, EventId("christmas"), dialSizePx = 220),
                PlacedWidget(10, EventId("christmas"), dialSizePx = 440),
                PlacedWidget(11, null, dialSizePx = 220),
                PlacedWidget(12, EventId("gone"), dialSizePx = 220),
            )

            val drawn = dialsToDraw(placed, events, TODAY).flatMap { it.appWidgetIds }

            assertEquals(placed.map { it.appWidgetId }.sorted(), drawn.sorted())
        }

        @Test
        fun `a copy showing an undated Event says to set one`() {
            val dials = dialsToDraw(listOf(PlacedWidget(7, EventId("new"), 220)), events, TODAY)

            assertEquals("Set a date", dials.single().state.primaryText)
        }
    }

    @Nested
    @DisplayName("Where the binding is decided")
    inner class WhereItLives {

        @Test
        fun `the binding rules import nothing from Android, so they can be tested here`() {
            assertNull(Regex("""import\s+android[x.]""").find(appSource("WidgetBinding.kt"))?.value)
        }

        @Test
        fun `the bindings are kept away from the Events, which outlive them`() {
            // Removing a widget must not touch an Event, and deleting an Event
            // must not have to know what is on the home screen (ADR-0009).
            assertTrue(appSource("WidgetBindingStore.kt").contains(""""widget_events""""))
            assertTrue(appSource("EventStore.kt").contains(""""event""""))
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-08-16")

        val events = mapOf(
            EventId("christmas") to StoredEvent(
                eventDate = LocalDate.parse("2026-12-25"),
                anchorDate = LocalDate.parse("2026-08-01"),
                title = "Christmas",
                accent = NamedAccent.RED.accent,
            ),
            EventId("holiday") to StoredEvent(
                eventDate = LocalDate.parse("2026-09-01"),
                anchorDate = LocalDate.parse("2026-08-01"),
                title = "Holiday",
                accent = NamedAccent.BLUE.accent,
            ),
            EventId("new") to StoredEvent.NOTHING_SET,
        )

        fun emptyValues(): Map<String, String?> = emptyMap()
    }
}
