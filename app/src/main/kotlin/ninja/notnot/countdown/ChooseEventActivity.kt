package ninja.notnot.countdown

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Where a copy of the widget is bound to the Event it will show, which is the
 * one thing that happens between dropping it and it appearing (ADR-0009).
 * Placement is two steps now, and this is the second one.
 *
 * It is not the app's list screen. The rows read the same, because what a row
 * says about an Event is decided once in `EventList.kt` and both screens ask it,
 * but they do a different thing: there is nothing to add here and nothing to
 * edit, and choosing a row ends the screen rather than opening another.
 */
class ChooseEventActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Before anything else, so every way out of this screen that is not a
        // choice leaves no widget on the home screen: the launcher drops the
        // copy unless it is told otherwise, and backing out has to say nothing.
        setResult(RESULT_CANCELED, resultFor(appWidgetId))

        // Started by something other than a launcher placing a widget. There is
        // no copy to bind, so there is nothing to choose for.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Read once, as the screen is built. The app is not in front while this
        // is, so no Event changes behind it.
        val events = eventsInOrder(EventStore(this).read())

        setContent {
            MaterialTheme {
                ChooseEventScreen(
                    events = events,
                    today = LocalDate.now(),
                    onChoose = { chosen -> bindTo(appWidgetId, chosen) },
                )
            }
        }
    }

    /**
     * Binds the copy and lets the launcher keep it.
     *
     * The order is the whole point: the binding is on disk before the OK, so
     * there is no instant at which the home screen holds a widget pointing at
     * nothing. The Dial is drawn here too — a configuration activity is told to
     * do that first update itself, and nothing else will.
     */
    private fun bindTo(appWidgetId: Int, chosen: EventId) {
        WidgetBindingStore(this).bind(appWidgetId, chosen)
        drawDialForToday(this, justPlaced = appWidgetId)
        setResult(RESULT_OK, resultFor(appWidgetId))
        finish()
    }

    /** What the launcher is answered with, which has to name the copy either way. */
    private fun resultFor(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

/**
 * The Events, in the order the app shows them, to pick the one this copy of the
 * widget will count to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseEventScreen(
    events: List<ListedEvent>,
    today: LocalDate,
    onChoose: (EventId) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Which Event?") }, windowInsets = topBarInsets) },
        contentWindowInsets = screenInsets,
    ) { insets ->
        if (events.isEmpty()) {
            // Placing a widget before there is anything to count to is a dead
            // end: there is nothing to offer, so it says where to go instead,
            // and backing out places nothing.
            Box(
                modifier = Modifier.fillMaxSize().padding(insets).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing to count to yet.\nAdd an Event in the app first,\n" +
                        "then place the widget again.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
                items(events, key = { it.id.value }) { listed ->
                    ChoosableEvent(listed = listed, today = today, onClick = { onChoose(listed.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/** One Event to choose: what it is called, and how soon it is. */
@Composable
private fun ChoosableEvent(listed: ListedEvent, today: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rowTitle(listed.stored),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            howSoon(listed.stored, today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
