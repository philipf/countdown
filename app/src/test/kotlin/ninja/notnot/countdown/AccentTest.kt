package ninja.notnot.countdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * An Accent: any colour, the seven the editor offers by name, how one is written
 * down and read back, and how they are fitted onto a screen with no room for all
 * of them on one line.
 *
 * What a colour has to look like on the Dial is next door in `DialLayoutTest`,
 * with the rest of the Dial's colours.
 */
class AccentTest {

    @Nested
    @DisplayName("Any colour")
    inner class AnyColour {

        @Test
        fun `is the colour it was made from`() {
            for (argb in SOME_COLOURS) {
                assertEquals(argb, Accent.of(argb).argb, hex(argb))
            }
        }

        @Test
        fun `is opaque, whatever alpha it was handed`() {
            // The arc is drawn on the white disc and not through it, so a
            // see-through Accent would come out paler than the colour picked.
            for (alpha in listOf(0x00, 0x01, 0x7F, 0xFE, 0xFF)) {
                val accent = Accent.of((alpha shl 24) or 0x2E7D32)

                assertEquals(0xFF2E7D32.toInt(), accent.argb, "alpha ${hex(alpha)}")
            }
        }

        @Test
        fun `is the same Accent as another of the same colour`() {
            assertEquals(Accent.of(0xFF123456.toInt()), Accent.of(0xFF123456.toInt()))
            assertEquals(NamedAccent.RED.accent, Accent.of(0xFFD32F2F.toInt()))
            assertNotEquals(Accent.of(0xFF123456.toInt()), Accent.of(0xFF123457.toInt()))
        }

        @Test
        fun `says which colour it is when a test names it`() {
            assertEquals("Accent(#2E7D32)", Accent.of(0xFF2E7D32.toInt()).toString())
        }
    }

    @Nested
    @DisplayName("What is offered")
    inner class WhatIsOffered {

        @Test
        fun `is seven colours, in the order they are shown in`() {
            assertEquals(
                listOf(
                    NamedAccent.BLUE,
                    NamedAccent.BLACK,
                    NamedAccent.MID_GREY,
                    NamedAccent.RED,
                    NamedAccent.GREEN,
                    NamedAccent.PINK,
                    NamedAccent.YELLOW,
                ),
                NamedAccent.entries.toList(),
            )
        }

        @Test
        fun `is the shades the palette has always had`() {
            // Written out rather than read off the enum, so a colour cannot be
            // nudged without someone saying so here.
            val shades = mapOf(
                NamedAccent.BLUE to 0xFF0288D1.toInt(),
                NamedAccent.BLACK to 0xFF000000.toInt(),
                NamedAccent.MID_GREY to 0xFF757575.toInt(),
                NamedAccent.RED to 0xFFD32F2F.toInt(),
                NamedAccent.GREEN to 0xFF2E7D32.toInt(),
                NamedAccent.PINK to 0xFFE91E63.toInt(),
                NamedAccent.YELLOW to 0xFF8A7500.toInt(),
            )

            for (offered in NamedAccent.entries) {
                assertEquals(Accent.of(shades.getValue(offered)), offered.accent, "$offered")
            }
        }

        @Test
        fun `is blue until the owner picks something else`() {
            assertEquals(NamedAccent.BLUE.accent, Accent.DEFAULT)
        }

        @Test
        fun `holds no colour twice, so no two are the same choice`() {
            val colours = NamedAccent.entries.map { it.accent }

            assertEquals(colours.size, colours.distinct().size, "two Accents are the same colour")
        }

        @Test
        fun `is opaque throughout, since the arc is drawn on the disc and not through it`() {
            for (offered in NamedAccent.entries) {
                assertEquals(0xFF, (offered.accent.argb ushr 24) and 0xFF, "$offered is see-through")
            }
        }
    }

    @Nested
    @DisplayName("Saying which is which")
    inner class Names {

        @Test
        fun `every Accent offered has a name a screen reader can say`() {
            for (offered in NamedAccent.entries) {
                assertTrue(offered.label.isNotBlank(), "$offered is offered with nothing to call it")
                assertFalse(offered.label.contains('_'), "${offered.label} is a constant, not a name")
                assertNotEquals(offered.name, offered.label, "$offered is only spelt out")
            }
        }

        @Test
        fun `no two of them answer to the same name`() {
            val names = NamedAccent.entries.map { it.label }

            assertEquals(names.size, names.distinct().size, "two Accents share a name")
        }
    }

    @Nested
    @DisplayName("Writing one down")
    inner class WritingOneDown {

        @Test
        fun `an offered colour is written under its name`() {
            for (offered in NamedAccent.entries) {
                assertEquals(offered.name, offered.accent.toStoredValue())
            }
        }

        @Test
        fun `a colour nobody named is written as six hex digits`() {
            assertEquals("#0A0B0C", Accent.of(0xFF0A0B0C.toInt()).toStoredValue())
            assertEquals("#FFFFFF", Accent.of(0xFFFFFFFF.toInt()).toStoredValue())
            assertEquals("#000001", Accent.of(0xFF000001.toInt()).toStoredValue())
        }

        @Test
        fun `a written colour can never be read as a name`() {
            // The '#' is what keeps the two apart. A name is letters and
            // underscores, so neither shape can be mistaken for the other.
            for (offered in NamedAccent.entries) {
                assertFalse(offered.name.contains('#'), "${offered.name} looks like a colour")
            }
            for (argb in SOME_COLOURS) {
                val written = Accent.of(argb).toStoredValue()

                assertTrue(
                    written.startsWith('#') || NamedAccent.entries.any { it.name == written },
                    "$written is neither a name nor a colour",
                )
            }
        }

        @Test
        fun `every colour reads back as the colour it was`() {
            for (argb in SOME_COLOURS + EVERY_GREY) {
                val accent = Accent.of(argb)

                assertEquals(accent, accentFrom(accent.toStoredValue()), hex(argb))
            }
        }

        @Test
        fun `a colour is written the same way however it was arrived at`() {
            // Picking red out of the palette and mixing the same red by hand are
            // the same Accent, so they are the same on disk too.
            assertEquals(
                NamedAccent.RED.accent.toStoredValue(),
                Accent.of(0xFFD32F2F.toInt()).toStoredValue(),
            )
        }
    }

    @Nested
    @DisplayName("Reading one back")
    inner class ReadingOneBack {

        @Test
        fun `the names written by every build so far mean the colours they meant`() {
            // The four v1 offered and the three #17 added. An Event on someone's
            // phone names its Accent, so these are the only names on disk.
            val written = mapOf(
                "BLUE" to NamedAccent.BLUE,
                "BLACK" to NamedAccent.BLACK,
                "MID_GREY" to NamedAccent.MID_GREY,
                "RED" to NamedAccent.RED,
                "GREEN" to NamedAccent.GREEN,
                "PINK" to NamedAccent.PINK,
                "YELLOW" to NamedAccent.YELLOW,
            )

            for ((name, offered) in written) {
                assertEquals(offered.accent, accentFrom(name), name)
            }
            assertEquals(NamedAccent.entries.size, written.size, "a colour was added without a name to read")
        }

        @Test
        fun `a hex reads in either case, since a hex digit is a hex digit`() {
            assertEquals(Accent.of(0xFF0A0B0C.toInt()), accentFrom("#0a0b0c"))
            assertEquals(Accent.of(0xFF0A0B0C.toInt()), accentFrom("#0A0B0C"))
        }

        @Test
        fun `nothing stored reads as the default`() {
            assertEquals(Accent.DEFAULT, accentFrom(null))
        }

        @Test
        fun `something that is neither a name nor a colour reads as the default`() {
            val nonsense = listOf(
                "",
                " ",
                "PUCE",
                "blue",
                "Blue",
                "#",
                "#12345",
                "#1234567",
                "#GGGGGG",
                "#12345 ",
                "# 12345",
                "#-01234",
                "#+01234",
                "0xFF0000",
                "FF0000",
                "255,0,0",
            )

            for (value in nonsense) {
                assertEquals(Accent.DEFAULT, accentFrom(value), "'$value' was read as a colour")
            }
        }

        @Test
        fun `an alpha is not a thing a stored colour can carry`() {
            // Eight digits is not a shape that is written, so it is not a shape
            // that is read: a half-transparent arc cannot arrive from a store.
            assertEquals(Accent.DEFAULT, accentFrom("#80FF0000"))
            assertEquals(Accent.DEFAULT, accentFrom("#FFFF0000"))
        }
    }

    @Nested
    @DisplayName("Fitting them across the editor")
    inner class Fitting {

        @Test
        fun `puts them all on one line when there is room`() {
            assertEquals(listOf(NamedAccent.entries.toList()), accentsInLines(1000))
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
                    NamedAccent.entries.toList(),
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
                assertEquals(NamedAccent.entries.size, accentsInLines(widthDp).size, "at $widthDp dp")
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

        /**
         * Colours to try a rule on: the palette's own, the corners of the cube,
         * and a spread of shades nobody named.
         */
        val SOME_COLOURS: List<Int> =
            NamedAccent.entries.map { it.accent.argb } +
                listOf(
                    0xFF000000.toInt(),
                    0xFFFFFFFF.toInt(),
                    0xFFFF0000.toInt(),
                    0xFF00FF00.toInt(),
                    0xFF0000FF.toInt(),
                    0xFF010203.toInt(),
                    0xFF0A0B0C.toInt(),
                    0xFF7B1FA2.toInt(),
                    0xFFC0FFEE.toInt(),
                    0xFFFEDCBA.toInt(),
                )

        /** Every grey there is, so a rule is tried on more than a handful. */
        val EVERY_GREY: List<Int> = (0..0xFF).map { 0xFF shl 24 or (it * 0x010101) }

        /** How wide a line of [count] Accents is drawn, gaps included. */
        fun lineWidth(count: Int): Int =
            count * ACCENT_CIRCLE_DP + (count - 1) * ACCENT_GAP_DP

        fun hex(argb: Int): String = "#%08X".format(argb)
    }
}
