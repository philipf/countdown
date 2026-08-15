package ninja.notnot.countdown

import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The two pictures of the Dial that are drawn as vectors rather than rendered:
 * the launcher icon and the widget picker's preview.
 *
 * Neither can be seen from a test, so what is checked is what a wrong one would
 * get wrong: the colours, which have to stay the Dial's own; the preview's
 * proportions, which come from [dialLayout] so the picker cannot promise
 * something the widget does not draw; and the launcher icon's safe zone, which
 * is the one rule a mask enforces and a file viewer hides.
 */
class IconTest {

    @Nested
    @DisplayName("The launcher icon")
    inner class LauncherIcon {

        @Test
        fun `is what both icon attributes ask for`() {
            // One adaptive icon serves both: a launcher masks it to whatever
            // shape it wants, so there is no separate round drawing to point at.
            for (attribute in listOf("icon", "roundIcon")) {
                assertTrue(
                    manifestXml().contains("""android:$attribute="@mipmap/ic_launcher""""),
                    "the manifest declares no android:$attribute",
                )
            }
        }

        @Test
        fun `is adaptive, in three layers`() {
            val icon = withoutComments(appFile("src/main/res/mipmap-anydpi/ic_launcher.xml"))

            assertTrue(icon.contains("<adaptive-icon"), "the icon is not adaptive")
            for (layer in LAYERS) {
                assertTrue(
                    icon.contains("""<$layer android:drawable="@drawable/ic_launcher_$layer""""),
                    "the icon has no $layer layer",
                )
            }
        }

        @Test
        fun `is drawn as vectors, so there is no density bucket to miss`() {
            for (layer in LAYERS) {
                assertEquals("vector", rootTag(layerXml(layer)), "the $layer layer is not a vector")
            }
        }

        @Test
        fun `keeps what it draws inside the safe zone every mask leaves alone`() {
            for (layer in listOf("foreground", "monochrome")) {
                for (path in paths(layerXml(layer))) {
                    val extent = path.extentFrom(ICON_CENTRE)

                    assertTrue(
                        extent <= SAFE_ZONE_RADIUS,
                        "the $layer layer reaches $extent, past the safe zone " +
                            "at $SAFE_ZONE_RADIUS: ${path.data}",
                    )
                }
            }
        }

        @Test
        fun `covers its whole background, so a mask has something to cut from`() {
            val background = paths(layerXml("background")).single()

            assertEquals(hex(DialColours.DISC), background.fillColour)
            assertEquals(
                listOf(0f to 0f, ICON_VIEWPORT to 0f, ICON_VIEWPORT to ICON_VIEWPORT, 0f to ICON_VIEWPORT),
                background.points,
                "the background does not fill the layer",
            )
        }

        @Test
        fun `is drawn in the Dial's own colours`() {
            val drawn = paths(layerXml("foreground"))

            assertEquals(
                setOf(
                    hex(trackColour(Accent.DEFAULT)),
                    hex(Accent.DEFAULT.argb),
                    hex(DialColours.PRIMARY_TEXT),
                ),
                drawn.mapNotNull { it.strokeColour }.toSet(),
            )
            assertEquals(emptyList<String>(), drawn.mapNotNull { it.fillColour })
        }

        @Test
        fun `leaves its monochrome layer black, because the system tints it`() {
            val drawn = paths(layerXml("monochrome"))
            val colours = drawn.flatMap { listOfNotNull(it.fillColour, it.strokeColour) }

            assertTrue(drawn.isNotEmpty(), "a themed icon with nothing in it is a blank tile")
            assertEquals(setOf("#FF000000"), colours.toSet())
        }

        @Test
        fun `sweeps its arc from twelve o'clock, like the Dial`() {
            val (x, y) = paths(layerXml("foreground")).progressArc().points.first()

            assertEquals(ICON_CENTRE.first, x, TOLERANCE, "the arc does not start above the centre")
            assertTrue(y < ICON_CENTRE.second, "the arc starts below the centre")
        }
    }

    @Nested
    @DisplayName("The widget picker's preview")
    inner class WidgetPreview {

        @Test
        fun `is offered to every launcher that will take one`() {
            // previewLayout is Android 12 and up. minSdk is 26, so previewImage
            // is what the older half of that range reads.
            assertTrue(widgetInfoXml().contains("""android:previewImage="@drawable/dial_preview""""))
            assertTrue(widgetInfoXml().contains("""android:previewLayout="@layout/widget_dial_preview""""))
        }

        @Test
        fun `is shown the way the widget itself is`() {
            assertEquals(listOf("ImageView"), tagsIn(previewLayout))
            assertTrue(previewLayout.contains("""android:src="@drawable/dial_preview""""))
            assertTrue(previewLayout.contains("""android:scaleType="fitCenter""""))
            assertNull(
                Regex("""android:background""").find(previewLayout)?.value,
                "the preview would promise a card the widget does not draw",
            )
        }

        @Test
        fun `has the Dial's proportions rather than a second opinion about them`() {
            val layout = dialLayout(ICON_VIEWPORT.toInt(), hasLabel = false, hasTitle = false)
            val drawn = paths(preview)
            val arcRadius = (layout.arcRight - layout.arcLeft) / 2

            assertEquals(layout.discRadius, drawn.disc().radius, TOLERANCE, "the disc is the wrong size")
            for (arc in listOf(drawn.track(), drawn.progressArc())) {
                assertEquals(arcRadius, arc.radius, TOLERANCE, "the Progress Arc is off the disc's rim")
                assertEquals(layout.arcStrokeWidth, arc.strokeWidth, TOLERANCE, "the arc is the wrong weight")
            }

            val (x, y) = drawn.progressArc().points.first()
            assertEquals(layout.centreX, x, TOLERANCE, "the arc does not start at twelve o'clock")
            assertEquals(layout.arcTop, y, TOLERANCE, "the arc does not start at twelve o'clock")
        }

        @Test
        fun `is drawn in the Dial's own colours`() {
            val drawn = paths(preview)

            assertEquals(hex(DialColours.DISC), drawn.disc().fillColour)
            assertEquals(hex(trackColour(Accent.DEFAULT)), drawn.track().strokeColour)
            assertEquals(hex(Accent.DEFAULT.argb), drawn.progressArc().strokeColour)
            assertEquals(1, drawn.count { it.strokeColour == hex(DialColours.PRIMARY_TEXT) }, "no number on it")
        }
    }

    private companion object {
        /** An adaptive icon is 108 units square, whatever density it is drawn at. */
        const val ICON_VIEWPORT = 108f

        val ICON_CENTRE = ICON_VIEWPORT / 2 to ICON_VIEWPORT / 2

        /**
         * The 66-unit circle at the centre of those 108 that every mask leaves
         * alone. Outside it a round, square or squircle mask may cut, so nothing
         * that has to be seen is drawn there.
         */
        const val SAFE_ZONE_RADIUS = 33f

        const val TOLERANCE = 0.01f

        val LAYERS = listOf("background", "foreground", "monochrome")

        fun hex(argb: Int): String = "#%08X".format(argb)

        fun layerXml(layer: String): String = appFile("src/main/res/drawable/ic_launcher_$layer.xml")

        /** The name of the outermost element, past the declaration and any comment. */
        fun rootTag(xml: String): String =
            Regex("""<([A-Za-z][\w-]*)""").find(withoutComments(xml).substringAfter("?>"))
                ?.groupValues?.get(1)
                .orEmpty()

        /** Each shape is found by what it is painted with rather than by its place in the file. */
        fun List<VectorPath>.disc(): VectorPath = single { it.fillColour == hex(DialColours.DISC) }

        fun List<VectorPath>.track(): VectorPath =
            single { it.strokeColour == hex(trackColour(Accent.DEFAULT)) }

        fun List<VectorPath>.progressArc(): VectorPath =
            single { it.strokeColour == hex(Accent.DEFAULT.argb) }

        val preview: String get() = appFile("src/main/res/drawable/dial_preview.xml")

        val previewLayout: String get() = layoutXml("widget_dial_preview")
    }
}

/**
 * One `<path>` from a vector drawable: what it is painted with, the points it is
 * drawn through, and the radius of the arcs between them.
 */
private class VectorPath(
    val data: String,
    val fillColour: String?,
    val strokeColour: String?,
    val strokeWidth: Float,
    val points: List<Pair<Float, Float>>,
    val radius: Float,
) {
    /**
     * How far this path reaches from [centre], stroke included. An arc bulges
     * past its own ends, so it counts as its radius: every arc drawn here is
     * centred on the drawing's centre, which is what makes that true.
     */
    fun extentFrom(centre: Pair<Float, Float>): Float =
        (points.map { hypot(it.first - centre.first, it.second - centre.second) } + radius).max() +
            strokeWidth / 2
}

/**
 * The paths of a vector drawable, in the order they are painted.
 *
 * Enough of the path grammar to read what this project draws: absolute moves,
 * lines and arcs. Anything else is a shape this cannot measure, so it fails
 * rather than passing on silence.
 */
private fun paths(xml: String): List<VectorPath> =
    Regex("""<path\b[^>]*/>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { element ->
        val data = attribute(element.value, "pathData") ?: error("a path with no pathData")
        val points = mutableListOf<Pair<Float, Float>>()
        var radius = 0f

        for (command in Regex("""([A-Za-z])([^A-Za-z]*)""").findAll(data)) {
            // A leading dot is legal in path data, so -.5 has to read as a
            // number rather than as nothing at all.
            val numbers = Regex("""-?(?:\d+(?:\.\d+)?|\.\d+)""").findAll(command.groupValues[2])
                .map { it.value.toFloat() }
                .toList()

            when (val name = command.groupValues[1]) {
                "M", "L" -> numbers.chunked(2).forEach { points += it[0] to it[1] }
                // Radius across, radius down, rotation, large-arc, sweep, x, y.
                "A" -> numbers.chunked(7).forEach {
                    radius = maxOf(radius, it[0])
                    points += it[5] to it[6]
                }
                "Z" -> Unit
                else -> error("'$name' in '$data' is not a command this test can measure")
            }
        }

        VectorPath(
            data = data,
            fillColour = attribute(element.value, "fillColor"),
            strokeColour = attribute(element.value, "strokeColor"),
            strokeWidth = attribute(element.value, "strokeWidth")?.toFloat() ?: 0f,
            points = points,
            radius = radius,
        )
    }.toList()

private fun attribute(element: String, name: String): String? =
    Regex("""android:$name="([^"]*)"""").find(element)?.groupValues?.get(1)
