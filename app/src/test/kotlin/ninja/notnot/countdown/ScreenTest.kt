package ninja.notnot.countdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What the app's screens keep out from under.
 *
 * A window is edge-to-edge whether it asks to be or not, so a screen that names
 * no insets puts its first pixel behind the status bar. That is what #10 was,
 * and nothing about it is visible without a device: the status bar is drawn by
 * the system over the top of the app, so a screenshot taken from the app's own
 * side of the glass looks perfect.
 *
 * So the rule is read out of the source instead, the way the widget's
 * declarations are. It is one rule and it is about counting: every Scaffold the
 * app has asks for the app's insets, and every bar at the top of one asks for
 * the top bar's. A fourth screen added without them fails here rather than on
 * someone's phone.
 *
 * What a top bar puts in its navigation slot is read the same way and for the
 * same reason: which glyph is drawn there, and which way it points in a
 * right-to-left locale, is another thing only a device shows.
 */
class ScreenTest {

    @Nested
    @DisplayName("Every screen")
    inner class EveryScreen {

        @Test
        fun `keeps its content clear of the bars and the cutout`() {
            for (screen in SCREENS) {
                val source = appSource(screen)
                val scaffolds = count("""Scaffold\(""", source)

                assertTrue(scaffolds > 0, "$screen has no screen in it any more")
                assertEquals(
                    scaffolds,
                    count("""contentWindowInsets = screenInsets""", source),
                    "$screen has a screen that would draw under the status bar",
                )
            }
        }

        @Test
        fun `holds its top bar clear of them as well`() {
            for (screen in SCREENS) {
                val source = appSource(screen)
                val bars = count("""TopAppBar\(""", source)

                assertTrue(bars > 0, "$screen has no top bar in it any more")
                assertEquals(
                    bars,
                    count("""windowInsets = topBarInsets""", source),
                    "$screen has a top bar whose own title could go under the cutout",
                )
            }
        }
    }

    @Nested
    @DisplayName("The way back")
    inner class TheWayBack {

        @Test
        fun `is the arrow the rest of Android draws, and says so out loud`() {
            // A word in the navigation slot works and reads wrong: the arrow is
            // what a phone's owner already knows, and only a device shows that.
            // AutoMirrored because a right-to-left locale wants it the other way
            // round, which no test on this side of the glass can see either.
            var slots = 0
            for (screen in SCREENS) {
                val source = appSource(screen)
                val backs = count("""navigationIcon = """, source)
                slots += backs

                assertEquals(
                    backs,
                    count("""Icons\.AutoMirrored\.Filled\.ArrowBack""", source),
                    "$screen goes back by something other than the standard arrow",
                )
                assertEquals(
                    backs,
                    count("""contentDescription = "Back"""", source),
                    "$screen has an arrow that is only an arrow out loud",
                )
            }
            assertTrue(slots > 0, "no screen has a way back in its top bar any more")
        }
    }

    @Nested
    @DisplayName("The insets themselves")
    inner class TheInsets {

        @Test
        fun `name the camera cutout, which the system bars do not cover`() {
            // Held sideways, the camera hole is beside the screen's content
            // rather than inside the status bar, so the system bars alone leave
            // a row's title underneath it.
            assertTrue(insets.contains("WindowInsets.systemBars"))
            assertTrue(
                insets.contains("WindowInsets.displayCutout"),
                "landscape would put content under the camera hole",
            )
        }

        @Test
        fun `leave the bottom of the screen to whatever is under the top bar`() {
            // Taking the bottom as well would reserve the navigation bar's
            // height at the top of the screen, under the status bar.
            assertTrue(insets.contains("WindowInsetsSides.Horizontal + WindowInsetsSides.Top"))
        }
    }

    private companion object {
        /** Every source with a screen in it. */
        val SCREENS = listOf("MainActivity.kt", "ChooseEventActivity.kt")

        val insets: String get() = appSource("ScreenInsets.kt")

        fun count(pattern: String, source: String): Int = Regex(pattern).findAll(source).count()
    }
}
