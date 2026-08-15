package ninja.notnot.countdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** The Event the app draws until there is a config screen to set one. */
private val HARDCODED_EVENT = Event(
    eventDate = LocalDate.of(2026, 12, 25),
    anchorDate = LocalDate.of(2026, 1, 1),
    title = "Christmas",
    accent = Accent.BLUE,
)

/**
 * Stands in for the wallpaper. The Dial's background is transparent and its
 * title is white, because on the home screen it sits on whatever is behind it;
 * a white screen would hide both.
 */
private val WALLPAPER_STAND_IN = Color(0xFF37474F)

/** The app's single screen: the Dial, drawn by the renderer the widget will use. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Dial(dialState(HARDCODED_EVENT, LocalDate.now()))
            }
        }
    }
}

@Composable
private fun Dial(state: DialState) {
    Surface(modifier = Modifier.fillMaxSize(), color = WALLPAPER_STAND_IN) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            // Drawn at the pixel size it is shown at, so it is sharp rather than
            // scaled.
            val sizePx = with(density) { minOf(maxWidth, maxHeight).roundToPx() }
                .coerceAtLeast(1)
            val bitmap = remember(state, sizePx) { renderDial(state, sizePx).asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = spokenAs(state),
                modifier = Modifier.size(with(density) { sizePx.toDp() }),
            )
        }
    }
}

/** The Dial is a bitmap, so it has to say out loud what it shows. */
private fun spokenAs(state: DialState): String =
    listOfNotNull(state.primaryText, state.labelText, state.title).joinToString(" ")
