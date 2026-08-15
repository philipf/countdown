package ninja.notnot.countdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The widget's one real decision — how big the Dial may be drawn — and the
 * declarations the launcher reads.
 *
 * The size matters because a bitmap too big for the Binder transaction throws in
 * the launcher's process, where no test and no build can see it. So the cap is a
 * number here, checked against the buffer rather than eyeballed.
 *
 * The declarations are checked as text because there is no other way to see them
 * without a device, and because two of the acceptance criteria — no
 * configuration activity, a single ImageView — are entirely decided by them.
 */
class WidgetTest {

    @Nested
    @DisplayName("The transaction limit")
    inner class TransactionLimit {

        @Test
        fun `the largest Dial costs what the cap says it does`() {
            assertEquals(409_600L, dialBitmapBytes(MAX_DIAL_SIZE_PX))
        }

        @Test
        fun `the largest Dial leaves most of the buffer free`() {
            val bytes = dialBitmapBytes(MAX_DIAL_SIZE_PX)

            assertTrue(
                bytes <= BUDGET_BYTES,
                "the Dial takes $bytes bytes of a ${TRANSACTION_BUFFER_BYTES}-byte buffer, " +
                    "over the $BUDGET_BYTES it is allowed",
            )
            assertTrue(
                TRANSACTION_BUFFER_BYTES - bytes >= HALF_THE_BUFFER,
                "less than half the buffer is left for the rest of the transaction",
            )
        }

        @Test
        fun `no screen density can push the Dial over the budget`() {
            for (density in DENSITIES) {
                val bytes = dialBitmapBytes(dialPixelSize(WIDGET_SIZE_DP, density))

                assertTrue(bytes <= BUDGET_BYTES, "density $density gave $bytes bytes")
            }
        }
    }

    @Nested
    @DisplayName("The Dial's pixel size")
    inner class PixelSize {

        @Test
        fun `follows the screen density while it is under the cap`() {
            assertEquals(110, dialPixelSize(WIDGET_SIZE_DP, density = 1f))
            assertEquals(165, dialPixelSize(WIDGET_SIZE_DP, density = 1.5f))
            assertEquals(220, dialPixelSize(WIDGET_SIZE_DP, density = 2f))
        }

        @Test
        fun `stops at the cap on a dense screen`() {
            assertEquals(MAX_DIAL_SIZE_PX, dialPixelSize(WIDGET_SIZE_DP, density = 3f))
            assertEquals(MAX_DIAL_SIZE_PX, dialPixelSize(WIDGET_SIZE_DP, density = 4f))
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
    @DisplayName("What the launcher is told")
    inner class LauncherDeclarations {

        @Test
        fun `the widget is offered at the size the bitmap is drawn for`() {
            val declared = "${WIDGET_SIZE_DP.toInt()}dp"

            assertTrue(
                widgetInfo.contains("""android:minWidth="$declared""""),
                "widget_info.xml and WIDGET_SIZE_DP disagree",
            )
            assertTrue(
                widgetInfo.contains("""android:minHeight="$declared""""),
                "widget_info.xml and WIDGET_SIZE_DP disagree",
            )
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
    }

    private companion object {
        /** Densities from the lowest Android ships to well past the densest phone. */
        val DENSITIES = listOf(0.75f, 1f, 1.5f, 2f, 2.625f, 3f, 3.5f, 4f, 8f, 100f)

        val BUDGET_BYTES = (TRANSACTION_BUFFER_BYTES * DIAL_BUFFER_SHARE).toLong()

        val HALF_THE_BUFFER = TRANSACTION_BUFFER_BYTES / 2

        val widgetInfo: String get() = withoutComments(appFile("src/main/res/xml/widget_info.xml"))

        val layout: String get() = withoutComments(appFile("src/main/res/layout/widget_dial.xml"))

        val manifest: String get() = withoutComments(appFile("src/main/AndroidManifest.xml"))
    }
}
