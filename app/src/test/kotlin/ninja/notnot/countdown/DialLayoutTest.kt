package ninja.notnot.countdown

import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The Dial's layout maths and palette. Everything the renderer decides is here,
 * where it can be checked without a Canvas; the drawing itself is left thin.
 */
class DialLayoutTest {

    @Nested
    @DisplayName("The disc")
    inner class Disc {

        @Test
        fun `fills the bitmap when there is no title`() {
            val layout = dialLayout(sizePx = 400, hasLabel = true, hasTitle = false)

            assertEquals(200f, layout.discRadius)
            assertEquals(200f, layout.centreX)
            assertEquals(200f, layout.centreY)
        }

        @Test
        fun `sits above the title when there is one`() {
            val layout = dialLayout(sizePx = 400, hasLabel = true, hasTitle = true)

            assertTrue(layout.discRadius < 200f, "the disc must give the title room")
            assertEquals(200f, layout.centreX)
            assertEquals(layout.discRadius, layout.centreY, "the disc is flush with the top")
            assertTrue(
                layout.discRadius * 2 + layout.titleTextSize <= 400f,
                "the disc and the title must fit in the bitmap",
            )
        }

        @Test
        fun `stays inside the bitmap`() {
            for (size in SIZES) {
                val layout = dialLayout(size, hasLabel = true, hasTitle = true)

                assertTrue(layout.centreX - layout.discRadius >= 0f, "size $size overflows left")
                assertTrue(layout.centreX + layout.discRadius <= size, "size $size overflows right")
                assertTrue(layout.centreY - layout.discRadius >= 0f, "size $size overflows top")
                assertTrue(layout.centreY + layout.discRadius <= size, "size $size overflows bottom")
            }
        }
    }

    @Nested
    @DisplayName("The Progress Arc")
    inner class ProgressArc {

        @Test
        fun `starts from the top`() {
            assertEquals(-90f, ARC_START_ANGLE)
        }

        @Test
        fun `sweeps in proportion to the fraction`() {
            assertEquals(0f, sweepAngle(0f))
            assertEquals(90f, sweepAngle(0.25f))
            assertEquals(180f, sweepAngle(0.5f))
            assertEquals(360f, sweepAngle(1f))
        }

        @Test
        fun `never sweeps past a full turn`() {
            assertEquals(360f, sweepAngle(1.5f))
            assertEquals(0f, sweepAngle(-0.5f))
            assertEquals(0f, sweepAngle(Float.NaN))
        }

        @Test
        fun `is drawn inside the disc, so its stroke is never clipped`() {
            for (size in SIZES) {
                val layout = dialLayout(size, hasLabel = true, hasTitle = true)
                val radius = (layout.arcRight - layout.arcLeft) / 2

                assertEquals(
                    layout.arcBottom - layout.arcTop,
                    layout.arcRight - layout.arcLeft,
                    0.001f,
                    "size $size: the arc must be swept inside a square",
                )
                assertEquals(layout.centreX, (layout.arcLeft + layout.arcRight) / 2, 0.001f)
                assertEquals(layout.centreY, (layout.arcTop + layout.arcBottom) / 2, 0.001f)
                assertEquals(
                    layout.discRadius,
                    radius + layout.arcStrokeWidth / 2,
                    0.001f,
                    "size $size: the arc must sit on the rim of the disc",
                )
            }
        }

        @Test
        fun `is thick enough to see and thin enough to leave the disc white`() {
            for (size in SIZES) {
                val layout = dialLayout(size, hasLabel = true, hasTitle = false)
                val thickness = layout.arcStrokeWidth / (layout.discRadius * 2)

                assertTrue(thickness in 0.04f..0.12f, "size $size gave a stroke of $thickness")
            }
        }
    }

    @Nested
    @DisplayName("The palette")
    inner class Palette {

        @Test
        fun `every Accent stands out against the white disc`() {
            for (accent in Accent.entries) {
                val contrast = contrastAgainstWhite(accent.argb)

                assertTrue(contrast >= 3.0, "$accent is only $contrast against white")
            }
        }

        @Test
        fun `the disc is opaque white`() {
            assertEquals(0xFFFFFFFF.toInt(), DialColours.DISC)
        }

        @Test
        fun `the number is readable on the disc`() {
            assertTrue(contrastAgainstWhite(DialColours.PRIMARY_TEXT) >= 7.0)
            assertTrue(contrastAgainstWhite(DialColours.LABEL_TEXT) >= 4.5)
        }

        @Test
        fun `the arc track is the Accent, faded`() {
            for (accent in Accent.entries) {
                val track = trackColour(accent)

                assertEquals(accent.argb and 0x00FFFFFF, track and 0x00FFFFFF, "$accent changed hue")
                assertTrue(alpha(track) < alpha(accent.argb), "$accent track is not faded")
                assertTrue(alpha(track) > 0, "$accent track is invisible")
            }
        }
    }

    @Nested
    @DisplayName("Text")
    inner class Text {

        @Test
        fun `scales with the pixel size, so the Dial looks the same at any size`() {
            val small = dialLayout(200, hasLabel = true, hasTitle = true)
            val large = dialLayout(600, hasLabel = true, hasTitle = true)

            assertEquals(3f, large.primaryTextSize / small.primaryTextSize, 0.001f)
            assertEquals(3f, large.labelTextSize / small.labelTextSize, 0.001f)
            assertEquals(3f, large.titleTextSize / small.titleTextSize, 0.001f)
            assertEquals(3f, large.discRadius / small.discRadius, 0.001f)
        }

        @Test
        fun `puts the number above the label and both inside the disc`() {
            val layout = dialLayout(400, hasLabel = true, hasTitle = false)

            assertTrue(layout.primaryCentreY < layout.centreY, "the number sits above centre")
            assertTrue(layout.labelCentreY > layout.primaryCentreY, "the label sits under the number")
            assertTrue(
                layout.labelCentreY + layout.labelTextSize < layout.centreY + layout.discRadius,
                "the label must stay inside the disc",
            )
        }

        @Test
        fun `centres the number when there is no label`() {
            val layout = dialLayout(400, hasLabel = false, hasTitle = false)

            assertEquals(layout.centreY, layout.primaryCentreY)
        }

        @Test
        fun `keeps the number bigger than the label`() {
            val layout = dialLayout(400, hasLabel = true, hasTitle = false)

            assertTrue(layout.primaryTextSize > layout.labelTextSize * 2)
        }

        @Test
        fun `keeps text inside the arc`() {
            val layout = dialLayout(400, hasLabel = true, hasTitle = false)
            val insideTheArc = (layout.discRadius - layout.arcStrokeWidth) * 2

            assertTrue(layout.primaryMaxWidth < insideTheArc, "the number would run under the arc")
            assertTrue(layout.labelMaxWidth < insideTheArc, "the label would run under the arc")
        }
    }

    @Nested
    @DisplayName("The title")
    inner class Title {

        @Test
        fun `is drawn below the disc when there is one`() {
            val layout = dialLayout(400, hasLabel = true, hasTitle = true)

            assertTrue(layout.drawTitle)
            assertTrue(
                layout.titleCentreY > layout.centreY + layout.discRadius,
                "the title must clear the disc",
            )
            assertTrue(layout.titleCentreY + layout.titleTextSize / 2 <= 400f, "the title runs off the bottom")
        }

        @Test
        fun `is not drawn when there is none`() {
            assertFalse(dialLayout(400, hasLabel = true, hasTitle = false).drawTitle)
        }

        @Test
        fun `is dropped when the Dial is too small to show it legibly`() {
            assertFalse(
                dialLayout(60, hasLabel = true, hasTitle = true).drawTitle,
                "a title too small to read should be dropped, not drawn as specks",
            )
        }

        @Test
        fun `gives the disc the whole bitmap once it is dropped`() {
            val dropped = dialLayout(60, hasLabel = true, hasTitle = true)
            val noTitle = dialLayout(60, hasLabel = true, hasTitle = false)

            assertEquals(noTitle.discRadius, dropped.discRadius, "a dropped title must not leave a gap")
        }

        @Test
        fun `leaves the Dial centred in the bitmap once it is dropped`() {
            for (size in SIZES) {
                val layout = dialLayout(size, hasLabel = true, hasTitle = true)
                if (layout.drawTitle) continue

                val centre = size / 2f
                assertEquals(centre, layout.centreX, "size $size: the Dial shifted sideways")
                assertEquals(centre, layout.centreY, "size $size: the Dial shifted up or down")
            }
        }

        @Test
        fun `is kept once the Dial is big enough to read it`() {
            assertTrue(
                dialLayout(288, hasLabel = true, hasTitle = true).drawTitle,
                "the smallest widget on a dense screen is big enough for a title",
            )
        }

        @Test
        fun `fits across the bitmap`() {
            val layout = dialLayout(400, hasLabel = true, hasTitle = true)

            assertTrue(layout.titleMaxWidth <= 400f)
            assertTrue(layout.titleMaxWidth > layout.discRadius, "the title may be wider than the disc")
        }
    }

    @Nested
    @DisplayName("Fitting text to the space")
    inner class Fitting {

        @Test
        fun `leaves text that already fits alone`() {
            assertEquals(40f, fittedTextSize(textSize = 40f, measuredWidth = 90f, maxWidth = 100f))
        }

        @Test
        fun `shrinks text in proportion to the overflow`() {
            assertEquals(20f, fittedTextSize(textSize = 40f, measuredWidth = 200f, maxWidth = 100f))
        }

        @Test
        fun `survives an unmeasurable string`() {
            assertEquals(40f, fittedTextSize(textSize = 40f, measuredWidth = 0f, maxWidth = 100f))
        }
    }

    @Nested
    @DisplayName("Layout is decided here, not in the drawing")
    inner class NoAndroid {

        @Test
        fun `the layout source imports nothing from Android, so it can be tested on the JVM`() {
            val source = appSource("DialLayout.kt")

            assertNull(Regex("""import\s+android[x.]""").find(source)?.value)
        }

        @Test
        fun `the same size always gives the same layout`() {
            assertEquals(
                dialLayout(400, hasLabel = true, hasTitle = true),
                dialLayout(400, hasLabel = true, hasTitle = true),
            )
            assertNotEquals(
                dialLayout(400, hasLabel = true, hasTitle = true),
                dialLayout(401, hasLabel = true, hasTitle = true),
            )
        }
    }

    private companion object {
        val SIZES = listOf(1, 2, 17, 64, 128, 300, 512, 1080, 4096)

        fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF

        /** WCAG relative luminance, so "visible against white" is a number, not an opinion. */
        fun contrastAgainstWhite(argb: Int): Double {
            val channels = listOf(16, 8, 0).map { shift ->
                val value = ((argb ushr shift) and 0xFF) / 255.0
                if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            val luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
            return 1.05 / (luminance + 0.05)
        }
    }
}
