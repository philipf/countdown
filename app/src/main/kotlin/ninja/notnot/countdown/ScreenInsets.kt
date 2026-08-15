package ninja.notnot.countdown

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * The edges of the window the app keeps its content out of.
 *
 * A window is edge-to-edge whether or not it asks to be — Android gives an app
 * targeting this SDK no other kind — so a screen that names no insets starts at
 * the top of the display and its first pixel is behind the status bar. That was
 * #10: the Dial preview was the first thing on the screen and the status bar sat
 * over the top of it.
 *
 * Material's own default stops at the system bars, which is the whole of it only
 * while the phone is upright. Turn it sideways and the camera hole is beside the
 * screen rather than inside the status bar, so the cutout is named here too and
 * a row's title cannot end up underneath it.
 *
 * The keyboard is deliberately not in this. It comes and goes over a screen that
 * is already laid out, and the window resizes for it by itself.
 *
 * The widget has none of this to do. It is drawn onto the home screen, which the
 * launcher has already laid out around the bars.
 */
val screenInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * The same edges, for a bar that is itself the top of the screen. It stands in
 * the status bar's space and holds its own content below it, so it takes the top
 * and the sides and leaves the bottom to whatever is under it.
 */
val topBarInsets: WindowInsets
    @Composable get() = screenInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
