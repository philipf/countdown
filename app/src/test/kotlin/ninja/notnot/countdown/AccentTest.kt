package ninja.notnot.countdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The palette: which colours are offered, what each one is called, and how they
 * are fitted onto a screen with no room for all of them on one line.
 *
 * What a colour has to look like on the Dial is next door in `DialLayoutTest`,
 * with the rest of the Dial's colours.
 */
class AccentTest {

    @Nested
    @DisplayName("What is offered")
    inner class WhatIsOffered {

        @Test
        fun `is seven colours`() {
            assertEquals(
                listOf(
                    Accent.BLUE,
                    Accent.BLACK,
                    Accent.MID_GREY,
                    Accent.RED,
                    Accent.GREEN,
                    Accent.PINK,
                    Accent.YELLOW,
                ),
                Accent.entries.toList(),
            )
        }

        @Test
        fun `is blue until the owner picks something else`() {
            assertEquals(Accent.BLUE, Accent.DEFAULT)
        }

        @Test
        fun `holds no colour twice, so no two are the same choice`() {
            val colours = Accent.entries.map { it.argb }

            assertEquals(colours.size, colours.distinct().size, "two Accents are the same colour")
        }

        @Test
        fun `is opaque throughout, since the arc is drawn on the disc and not through it`() {
            for (accent in Accent.entries) {
                assertEquals(0xFF, (accent.argb ushr 24) and 0xFF, "$accent is see-through")
            }
        }
    }

    @Nested
    @DisplayName("Saying which is which")
    inner class Names {

        @Test
        fun `every Accent has a name a screen reader can say`() {
            for (accent in Accent.entries) {
                assertTrue(accent.label.isNotBlank(), "$accent is offered with nothing to call it")
                assertFalse(accent.label.contains('_'), "${accent.label} is a constant, not a name")
                assertNotEquals(accent.name, accent.label, "$accent is only spelt out")
            }
        }

        @Test
        fun `no two of them answer to the same name`() {
            val names = Accent.entries.map { it.label }

            assertEquals(names.size, names.distinct().size, "two Accents share a name")
        }
    }

    @Nested
    @DisplayName("Fitting them across the editor")
    inner class Fitting {

        @Test
        fun `puts them all on one line when there is room`() {
            assertEquals(listOf(Accent.entries.toList()), accentsInLines(1000))
        }

        @Test
        fun `wraps on the narrowest phone rather than running off the edge`() {
            val lines = accentsInLines(NARROWEST_EDITOR_DP)

            assertTrue(lines.size > 1, "seven colours were claimed to fit across $NARROWEST_EDITOR_DP dp")
            for (line in lines) {
                assertTrue(
                    lineWidth(line.size) <= NARROWEST_EDITOR_DP,
                    "a line of ${line.size} needs ${lineWidth(line.size)} dp of $NARROWEST_EDITOR_DP",
                )
            }
        }

        @Test
        fun `offers every colour once, whatever the width`() {
            for (widthDp in WIDTHS) {
                assertEquals(
                    Accent.entries.toList(),
                    accentsInLines(widthDp).flatten(),
                    "at $widthDp dp",
                )
            }
        }

        @Test
        fun `fills each line and no more`() {
            for (widthDp in 0..1200) {
                val across = accentsAcross(widthDp)

                assertTrue(
                    across == 1 || lineWidth(across) <= widthDp,
                    "at $widthDp dp, $across across would need ${lineWidth(across)} dp",
                )
                assertTrue(
                    lineWidth(across + 1) > widthDp,
                    "at $widthDp dp there was room for one more than $across",
                )
            }
        }

        @Test
        fun `keeps one colour on the line however little room there is`() {
            for (widthDp in listOf(0, 1, 55, -100)) {
                assertEquals(1, accentsAcross(widthDp), "at $widthDp dp")
                assertEquals(Accent.entries.size, accentsInLines(widthDp).size, "at $widthDp dp")
            }
        }
    }

    private companion object {
        /**
         * What the editor has to fill on the narrowest phone worth supporting: a
         * 320 dp screen, less the 24 dp the fields are inset by on each side.
         */
        const val NARROWEST_EDITOR_DP = 320 - 48

        val WIDTHS = listOf(0, 1, 56, 100, NARROWEST_EDITOR_DP, 320, 411, 600, 1200)

        /** How wide a line of [count] Accents is drawn, gaps included. */
        fun lineWidth(count: Int): Int =
            count * ACCENT_CIRCLE_DP + (count - 1) * ACCENT_GAP_DP
    }
}
