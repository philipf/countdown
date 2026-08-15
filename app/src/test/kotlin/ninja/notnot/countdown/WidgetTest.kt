package ninja.notnot.countdown

import kotlin.text.RegexOption.DOT_MATCHES_ALL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

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
 * 2x2 minimum and default, a chooser on placement, a single centred ImageView —
 * are entirely decided by them.
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
        fun `dropping the widget opens the chooser, which is what binds it to an Event`() {
            assertTrue(
                widgetInfo.contains("""android:configure="$CHOOSER""""),
                "nothing would ask which Event the copy is for",
            )
            assertTrue(chooserDeclaration.contains("android.appwidget.action.APPWIDGET_CONFIGURE"))
            // The launcher starts it, and the launcher is another app.
            assertTrue(chooserDeclaration.contains("""android:exported="true""""))
        }

        @Test
        fun `the widget is not offered as reconfigurable, which minSdk cannot have`() {
            assertNull(Regex("""android:widgetFeatures""").find(widgetInfo)?.value)
        }

        @Test
        fun `nothing is on a timer, because the app redraws the widget itself`() {
            assertTrue(widgetInfo.contains("""android:updatePeriodMillis="0""""))
        }

        @Test
        fun `the layout is a single ImageView`() {
            assertEquals(listOf("ImageView"), tagsIn(layout))
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

        @Test
        fun `a removed copy takes its binding with it`() {
            // Android hands out appWidgetIds again, so a binding left behind
            // would be inherited by whatever is placed next.
            assertTrue(appSource("CountdownWidget.kt").contains("override fun onDeleted"))
        }
    }

    /**
     * What the widget tidies up after itself. All of it is disk and home screen,
     * so it is read out of the source rather than run.
     */
    @Nested
    @DisplayName("Looking after the bindings")
    inner class Housekeeping {

        @Test
        fun `removing a copy reaches the bindings and nothing else`() {
            // What the copy was showing is not the copy's to take with it.
            val onDeleted = Regex("""override fun onDeleted.*?\n    \}""", DOT_MATCHES_ALL)
                .find(widget)?.value
                ?: fail("nothing forgets a copy that has been removed")

            assertTrue(onDeleted.contains("WidgetBindingStore"))
            assertNull(Regex("""EventStore""").find(onDeleted)?.value, "an Event must be untouched")
        }

        @Test
        fun `a removal that was never heard about is swept on the next redraw`() {
            // onDeleted is a broadcast and a broadcast can be missed. The redraw
            // has just asked the framework what is on the home screen, so it is
            // the one place that can tell which bindings are for copies that
            // have gone.
            val redraw = Regex("""fun drawDialForToday.*?\n\}""", DOT_MATCHES_ALL)
                .find(widget)?.value
                ?: fail("there is no redraw to sweep from")

            assertTrue(redraw.contains("keepOnly(ids)"), "a stale binding would live on")
        }

        @Test
        fun `a copy opens the app when it is tapped, whatever it is showing`() {
            // Including a copy whose Event has been deleted: it says "Set a
            // date", and tapping it is how the owner sets one. There is one
            // place a Dial is built and it always sets the tap, so no Dial goes
            // out without it.
            assertEquals(1, Regex("""RemoteViews\(""").findAll(widget).count())
            assertEquals(1, Regex("""setOnClickPendingIntent\(""").findAll(widget).count())
            assertTrue(widget.contains("MainActivity"), "the tap has to open the app")
        }
    }

    /**
     * The order the chooser does things in, which is what decides whether a
     * widget can end up on the home screen showing nothing. There is no way to
     * see it without a launcher, so it is read out of the source.
     */
    @Nested
    @DisplayName("Placing a widget")
    inner class Placing {

        @Test
        fun `backing out of the chooser leaves no widget behind`() {
            // The launcher drops the copy unless it is told otherwise, so the
            // refusal is in place before there is any way out of the screen.
            assertTrue(
                chooser.indexOf("setResult(RESULT_CANCELED") in
                    0 until chooser.indexOf("setContent {"),
                "backing out would leave a widget bound to nothing",
            )
        }

        @Test
        fun `the binding is on disk before the launcher is told to keep the copy`() {
            assertTrue(
                chooser.indexOf(".bind(") in 0 until chooser.indexOf("setResult(RESULT_OK"),
                "an OK with no binding behind it leaves a widget pointing at nothing",
            )
        }

        @Test
        fun `the chooser draws the copy it has just bound`() {
            // A configuration activity is expected to send that first update
            // itself: no APPWIDGET_UPDATE follows it.
            assertTrue(chooser.contains("drawDialForToday"))
        }

        @Test
        fun `the chooser offers the Events in the order the app shows them`() {
            assertTrue(chooser.contains("eventsInOrder"))
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

        /** What android:configure has to name, spelled out as a component name. */
        const val CHOOSER = "ninja.notnot.countdown.ChooseEventActivity"

        val widgetInfo: String get() = widgetInfoXml()

        val layout: String get() = layoutXml("widget_dial")

        val manifest: String get() = manifestXml()

        val chooser: String get() = appSource("ChooseEventActivity.kt")

        val widget: String get() = appSource("CountdownWidget.kt")

        /** What the manifest says about the chooser, and nothing else. */
        val chooserDeclaration: String
            get() = Regex("""<activity\b[^>]*ChooseEventActivity\b.*?</activity>""", DOT_MATCHES_ALL)
                .find(manifest)?.value
                ?: fail("the chooser is not declared in the manifest")
    }
}
