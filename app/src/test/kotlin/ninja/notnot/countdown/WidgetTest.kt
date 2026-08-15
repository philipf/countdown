package ninja.notnot.countdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The widget's two real decisions — how big a square the widget gives the Dial,
 * and how many pixels of it are worth drawing — and the declarations the
 * launcher reads.
 *
 * The size matters because a bitmap the framework refuses fails in the
 * launcher's process, where no test and no build can see it. So the ceiling is
 * arithmetic here rather than an eyeballed number.
 *
 * The declarations are checked as text because there is no other way to see them
 * without a device, and because several of the acceptance criteria — resizable,
 * 2x2 minimum and default, no configuration activity, a single centred
 * ImageView — are entirely decided by them.
 */
class WidgetTest {

    @Nested
    @DisplayName("The framework's bitmap ceiling")
    inner class BitmapCeiling {

        @Test
        fun `is one and a half screens' worth of pixels`() {
            assertEquals(15_552_000L, widgetBitmapCeilingBytes(1080, 2400))
            assertEquals(5_529_600L, widgetBitmapCeilingBytes(720, 1280))
        }

        @Test
        fun `a Dial as big as the screen allows still fits inside it`() {
            for ((width, height) in SCREENS) {
                // The widget cannot be bigger than the screen, so this is the
                // largest square any launcher on this screen could ask for.
                val bytes = dialBitmapBytes(minOf(width, height))
                val ceiling = widgetBitmapCeilingBytes(width, height)

                assertTrue(
                    bytes <= ceiling * 2 / 3,
                    "${width}x$height: a full-screen Dial takes $bytes of $ceiling bytes",
                )
            }
        }

        @Test
        fun `the largest Dial drawn costs what the cap says it does`() {
            assertEquals(4_665_600L, dialBitmapBytes(MAX_DIAL_SIZE_PX))
        }

        @Test
        fun `no widget size and no screen density can push the Dial past the cap`() {
            for (density in DENSITIES) {
                for (sizeDp in SIZES_DP) {
                    val sizePx = dialPixelSize(sizeDp, density)

                    assertTrue(
                        sizePx in MIN_DIAL_SIZE_PX..MAX_DIAL_SIZE_PX,
                        "${sizeDp}dp at density $density gave $sizePx",
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("The square the widget gives the Dial")
    inner class WidgetSquare {

        @Test
        fun `is the short side of the box the widget ends up with`() {
            // A widget three cells wide and two tall, in portrait.
            assertEquals(180f, widgetSquareDp(180, 250, 110, 180))
        }

        @Test
        fun `follows the widget as it is dragged bigger`() {
            val small = widgetSquareDp(110, 180, 110, 110)
            val large = widgetSquareDp(250, 320, 250, 250)

            assertTrue(large > small, "a bigger widget must ask for a bigger Dial")
            assertEquals(250f, large)
        }

        @Test
        fun `takes whichever orientation gives the Dial the most room`() {
            // Rotating the phone does not change the options, so nothing
            // redraws: the Dial has to be drawn for the roomier of the two.
            val landscapeIsRoomier = widgetSquareDp(
                minWidthDp = 150,
                maxWidthDp = 300,
                minHeightDp = 260,
                maxHeightDp = 160,
            )

            assertEquals(260f, landscapeIsRoomier)
        }

        @Test
        fun `is never smaller than the size declared to the launcher`() {
            assertEquals(MIN_WIDGET_SIZE_DP, widgetSquareDp(0, 0, 0, 0))
            assertEquals(MIN_WIDGET_SIZE_DP, widgetSquareDp(-1, -1, -1, -1))
            assertEquals(MIN_WIDGET_SIZE_DP, widgetSquareDp(40, 40, 40, 40))
        }
    }

    @Nested
    @DisplayName("The Dial's pixel size")
    inner class PixelSize {

        @Test
        fun `follows the screen density while it is under the cap`() {
            assertEquals(110, dialPixelSize(MIN_WIDGET_SIZE_DP, density = 1f))
            assertEquals(165, dialPixelSize(MIN_WIDGET_SIZE_DP, density = 1.5f))
            assertEquals(220, dialPixelSize(MIN_WIDGET_SIZE_DP, density = 2f))
        }

        @Test
        fun `is drawn pixel for pixel on the phone this is built for`() {
            // 1080 x 2400 at a density of 2.625 is 411dp across, so the widest
            // widget that phone can hold is still under the cap.
            assertEquals(1078, dialPixelSize(411f, density = 2.625f))
        }

        @Test
        fun `stops at the cap beyond that`() {
            assertEquals(MAX_DIAL_SIZE_PX, dialPixelSize(411f, density = 3.5f))
            assertEquals(MAX_DIAL_SIZE_PX, dialPixelSize(800f, density = 2f))
        }

        @Test
        fun `survives a size the launcher should never report`() {
            for (nonsense in listOf(0f, -1f, Float.NaN, Float.MAX_VALUE, Float.POSITIVE_INFINITY)) {
                val size = dialPixelSize(nonsense, density = 3f)

                assertTrue(size in MIN_DIAL_SIZE_PX..MAX_DIAL_SIZE_PX, "$nonsense gave $size")
            }
        }

        @Test
        fun `is always a size the renderer will accept`() {
            assertTrue(MIN_DIAL_SIZE_PX > 0)
            assertTrue(MIN_DIAL_SIZE_PX <= MAX_DIAL_SIZE_PX)
        }

        @Test
        fun `the sizing imports nothing from Android, so it can be tested on the JVM`() {
            assertNull(Regex("""import\s+android[x.]""").find(appSource("WidgetSizing.kt"))?.value)
        }
    }

    @Nested
    @DisplayName("A refused update")
    inner class Refusal {

        @Test
        fun `is retried smaller until there is nothing smaller to try`() {
            var size: Int? = MAX_DIAL_SIZE_PX
            val tried = mutableListOf<Int>()
            while (size != null) {
                tried += size
                size = smallerDialSize(size)
            }

            assertEquals(MIN_DIAL_SIZE_PX, tried.last(), "the last try must be the smallest Dial")
            assertTrue(tried.size in 2..8, "${tried.size} tries is not a sensible retry")
            assertTrue(tried.zipWithNext().all { (a, b) -> b < a }, "each try must be smaller: $tried")
        }

        @Test
        fun `gives up at the smallest Dial rather than shrinking to nothing`() {
            assertNull(smallerDialSize(MIN_DIAL_SIZE_PX))
            assertNull(smallerDialSize(1))
        }
    }

    @Nested
    @DisplayName("What the launcher is told")
    inner class LauncherDeclarations {

        @Test
        fun `the widget can be dragged both ways`() {
            assertTrue(widgetInfo.contains("""android:resizeMode="horizontal|vertical""""))
        }

        @Test
        fun `it lands at two cells square and goes no smaller`() {
            val declared = "${MIN_WIDGET_SIZE_DP.toInt()}dp"

            for (attribute in listOf("minWidth", "minHeight", "minResizeWidth", "minResizeHeight")) {
                assertTrue(
                    widgetInfo.contains("""android:$attribute="$declared""""),
                    "widget_info.xml and MIN_WIDGET_SIZE_DP disagree on $attribute",
                )
            }
            assertTrue(widgetInfo.contains("""android:targetCellWidth="2""""))
            assertTrue(widgetInfo.contains("""android:targetCellHeight="2""""))
        }

        @Test
        fun `there is no configuration activity, so dropping the widget just works`() {
            assertNull(Regex("""android:configure""").find(widgetInfo)?.value)
        }

        @Test
        fun `nothing is on a timer, because the app redraws the widget itself`() {
            assertTrue(widgetInfo.contains("""android:updatePeriodMillis="0""""))
        }

        @Test
        fun `the layout is a single ImageView`() {
            val tags = Regex("""<([A-Za-z][\w.]*)""").findAll(layout).map { it.groupValues[1] }

            assertEquals(listOf("ImageView"), tags.toList())
        }

        @Test
        fun `the square Dial is centred in a widget that is not square`() {
            assertTrue(layout.contains("""android:scaleType="fitCenter""""))
            assertTrue(layout.contains("""android:layout_width="match_parent""""))
            assertTrue(layout.contains("""android:layout_height="match_parent""""))
        }

        @Test
        fun `the layout has no background, so the Dial sits on the wallpaper`() {
            assertNull(Regex("""android:background""").find(layout)?.value)
        }

        @Test
        fun `the widget provider is declared with the layout it starts from`() {
            assertTrue(manifest.contains("""android:name=".CountdownWidget""""))
            assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"))
            assertTrue(manifest.contains("""android:resource="@xml/widget_info""""))
            assertTrue(widgetInfo.contains("""android:initialLayout="@layout/widget_dial""""))
        }

        @Test
        fun `the widget redraws itself when it is resized`() {
            val source = appSource("CountdownWidget.kt")

            assertTrue(
                source.contains("override fun onAppWidgetOptionsChanged"),
                "nothing would redraw the Dial after a resize",
            )
            assertTrue(
                source.contains("OPTION_APPWIDGET_MIN_WIDTH"),
                "the new size has to come from the widget's options",
            )
        }
    }

    private companion object {
        /** Densities from the lowest Android ships to well past the densest phone. */
        val DENSITIES = listOf(0.75f, 1f, 1.5f, 2f, 2.625f, 3f, 3.5f, 4f, 8f, 100f)

        /** Widget sizes from two cells to a widget filling a tablet. */
        val SIZES_DP = listOf(110f, 180f, 250f, 320f, 411f, 600f, 800f, 4000f)

        /** Screens in pixels: an old phone, current phones, a tablet, a foldable. */
        val SCREENS = listOf(
            720 to 1280,
            1080 to 1920,
            1080 to 2400,
            1440 to 3120,
            1600 to 2560,
            2208 to 1840,
        )

        val widgetInfo: String get() = withoutComments(appFile("src/main/res/xml/widget_info.xml"))

        val layout: String get() = withoutComments(appFile("src/main/res/layout/widget_dial.xml"))

        val manifest: String get() = withoutComments(appFile("src/main/AndroidManifest.xml"))
    }
}
