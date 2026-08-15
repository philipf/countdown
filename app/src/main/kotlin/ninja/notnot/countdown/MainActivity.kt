package ninja.notnot.countdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Stands in for the wallpaper behind the preview. The Dial's background is
 * transparent and its title is white, because on the home screen it sits on
 * whatever is behind it; a white panel would hide both.
 */
private val WALLPAPER_STAND_IN = Color(0xFF37474F)

/** How big the preview Dial is drawn. The Dial looks the same at any size. */
private val PREVIEW_SIZE = 240.dp

/**
 * The app's two screens: the list of Events, and the editor behind whichever row
 * is tapped. The editor is the screen the app used to open on, unchanged apart
 * from being reached through a row and having somewhere to go back to.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = EventStore(this)
        setContent {
            MaterialTheme {
                Countdown(store = store, today = LocalDate.now())
            }
        }
    }
}

/**
 * The Events, and which one is open. Both screens are held here rather than in
 * two activities: there is one reader and one writer of the Events, so what is
 * held and what is on disk cannot drift apart, and coming back from the editor
 * shows the change without going to the disk for it.
 */
@Composable
private fun Countdown(store: EventStore, today: LocalDate) {
    // Read once, as the screen is built. Nothing else writes an Event while the
    // app is in front, so there is nothing to notice happening behind this.
    var events by remember { mutableStateOf(store.read()) }
    // Held as a plain string, so a rotation in the editor comes back to the same
    // Event rather than to the list.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    fun write(id: EventId, stored: StoredEvent) {
        store.write(id, stored)
        events = events + (id to stored)
    }

    fun delete(id: EventId) {
        store.delete(id)
        events = events - id
    }

    val open = openId?.let(::EventId)
    if (open == null) {
        EventListScreen(
            events = eventsInOrder(events),
            today = today,
            // An added Event has no date yet. It is written immediately, so it
            // is still there if the owner puts the phone down before dating it,
            // and it waits at the top of the list saying it needs one.
            onAdd = { write(store.newEventId(), StoredEvent.NOTHING_SET) },
            onOpen = { openId = it.value },
            onDelete = { delete(it) },
        )
    } else {
        // Keyed on the Event, so opening a second row starts the editor on that
        // Event rather than on the fields the last one left in place.
        key(open) {
            EventEditor(
                // An id with no Event behind it is one that was never written,
                // which nothing here can produce. It is read as an Event with
                // nothing set rather than as a reason to crash.
                initial = events[open] ?: StoredEvent.NOTHING_SET,
                today = today,
                onChange = { write(open, it) },
                onBack = { openId = null },
            )
        }
        // The editor is a screen rather than an activity, so back has to be told
        // that it leaves the editor and not the app.
        BackHandler { openId = null }
    }
}

/**
 * Every Event, soonest first. Each row is its title and how soon it is, which is
 * enough to find the one being looked for; everything else about an Event is on
 * the screen behind it. A row is also where an Event is taken off the list once
 * it no longer matters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventListScreen(
    events: List<ListedEvent>,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (EventId) -> Unit,
    onDelete: (EventId) -> Unit,
) {
    // Which Event has been asked about, if any. Nothing is deleted until the
    // asking is answered, so backing out leaves the Event exactly as it was.
    var asking by remember { mutableStateOf<EventId?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Countdown") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("Add an Event") }
        },
    ) { insets ->
        if (events.isEmpty()) {
            // A first run has nothing to show, so it says what to do instead of
            // leaving the owner looking at an empty screen.
            Box(
                modifier = Modifier.fillMaxSize().padding(insets).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing to count to yet.\nAdd an Event and pick its date.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = insets) {
                items(events, key = { it.id.value }) { listed ->
                    EventRow(
                        listed = listed,
                        today = today,
                        onClick = { onOpen(listed.id) },
                        onDelete = { asking = listed.id },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Found again rather than held, so the Event that has just gone takes the
    // question with it instead of leaving it asking about nothing.
    val asked = asking?.let { id -> events.firstOrNull { it.id == id } }
    if (asked != null) {
        ConfirmDelete(
            title = rowTitle(asked.stored),
            onConfirm = {
                asking = null
                onDelete(asked.id)
            },
            onDismiss = { asking = null },
        )
    }
}

/** One Event as a row: what it is called, how soon it is, and the way to be rid of it. */
@Composable
private fun EventRow(
    listed: ListedEvent,
    today: LocalDate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
        TextButton(
            onClick = onDelete,
            // Every row's button says the same word, so out loud it says which
            // Event it would take away.
            modifier = Modifier.semantics {
                contentDescription = "Delete ${rowTitle(listed.stored)}"
            },
        ) {
            Text("Delete")
        }
    }
}

/**
 * What is asked before an Event goes. There is no undo and nothing is backed up,
 * so the dialog says so rather than leaving the owner to find out.
 */
@Composable
private fun ConfirmDelete(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $title?") },
        text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One Event's screen: the Dial preview on top, the fields that set it below.
 * There is no Save button — every edit is written as it is made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(
    initial: StoredEvent,
    today: LocalDate,
    onChange: (StoredEvent) -> Unit,
    onBack: () -> Unit,
) {
    var stored by remember { mutableStateOf(initial) }
    var pickingDate by remember { mutableStateOf(false) }

    fun edit(change: (StoredEvent) -> StoredEvent) {
        stored = change(stored).also(onChange)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rowTitle(stored)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState()),
        ) {
            DialPreview(dialState(stored.toEvent(), today))

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Field("Event date") {
                    OutlinedButton(
                        onClick = { pickingDate = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stored.eventDate?.let(::formatEventDate) ?: "Pick a date")
                    }
                }

                OutlinedTextField(
                    value = stored.title,
                    onValueChange = { typed -> edit { it.withTitle(typed) } },
                    label = { Text("Title") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Field("Accent") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (accent in Accent.entries) {
                            AccentSwatch(
                                accent = accent,
                                selected = accent == stored.accent,
                                onClick = { edit { it.withAccent(accent) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        EventDatePicker(
            selected = stored.eventDate,
            today = today,
            onPicked = { picked -> edit { it.withEventDate(picked, today) } },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The Dial as the widget will draw it, redrawn whenever the Event changes. */
@Composable
private fun DialPreview(state: DialState) {
    Box(
        modifier = Modifier.fillMaxWidth().background(WALLPAPER_STAND_IN).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn at the pixel size it is shown at, so it is sharp rather than
        // scaled.
        val sizePx = with(LocalDensity.current) { PREVIEW_SIZE.roundToPx() }
        val bitmap = remember(state, sizePx) { renderDial(state, sizePx).asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = spokenAs(state),
            modifier = Modifier.size(PREVIEW_SIZE),
        )
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = accent.label }
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color(accent.argb)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDatePicker(
    selected: LocalDate?,
    today: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = pickerMillisOf(selected ?: today),
        yearRange = pickerYearRange(today, selected),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(localDateFromPickerMillis(it)) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = state)
    }
}

/** How an Accent is named out loud, since a swatch is only a colour. */
private val Accent.label: String
    get() = when (this) {
        Accent.BLUE -> "Blue"
        Accent.BLACK -> "Black"
        Accent.MID_GREY -> "Mid grey"
        Accent.RED -> "Red"
    }
