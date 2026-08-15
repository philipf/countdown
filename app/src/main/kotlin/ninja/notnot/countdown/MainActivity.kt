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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Stands in for the wallpaper behind the preview. The Dial's background is
 * transparent and its title is white, because on the home screen it sits on
 * whatever is behind it; a white panel would hide both.
 */
private val WALLPAPER_STAND_IN = Color(0xFF37474F)

/** How big the preview Dial is drawn. The Dial looks the same at any size. */
private val PREVIEW_SIZE = 240.dp

/** The same Dial inside the mixer, where a dialog has less room to give it. */
private val MIXER_PREVIEW_SIZE = 140.dp

/**
 * The mixer's circle when the Event is on a named colour: every hue, once round,
 * which says that anything can be made here better than a word would. Red is at
 * both ends so the sweep meets itself.
 */
private val SPECTRUM = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

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
        topBar = { TopAppBar(title = { Text("Countdown") }, windowInsets = topBarInsets) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("Add an Event") }
        },
        contentWindowInsets = screenInsets,
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
    // The colour being mixed, while one is. It is held apart from the Event and
    // nothing is written until the owner says so, which is what makes backing
    // out of the mixer leave the Accent exactly as it was. Everything else on
    // this screen is saved as it is typed; a colour half way between two colours
    // is the one thing that is not worth saving.
    var mixing by remember { mutableStateOf<Accent?>(null) }

    fun edit(change: (StoredEvent) -> StoredEvent) {
        stored = change(stored).also(onChange)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rowTitle(stored)) },
                // The arrow every other app puts here, rather than the word: it
                // is what a phone's owner already reads as the way back, and it
                // is the AutoMirrored one, so a right-to-left locale points it
                // the other way instead of at the wrong edge of the screen.
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = topBarInsets,
            )
        },
        contentWindowInsets = screenInsets,
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
                    // The circles do not all fit across a narrow phone, so the
                    // width they have is measured here and how it is filled is
                    // decided by accentChoicesInLines. A single line would run
                    // the last of them off the edge, where they cannot be
                    // tapped.
                    BoxWithConstraints {
                        val lines = accentChoicesInLines(maxWidth.value.toInt())
                        Column(verticalArrangement = Arrangement.spacedBy(ACCENT_GAP_DP.dp)) {
                            for (line in lines) {
                                Row(horizontalArrangement = Arrangement.spacedBy(ACCENT_GAP_DP.dp)) {
                                    for (choice in line) {
                                        AccentCircle(
                                            choice = choice,
                                            inUse = stored.accent,
                                            onClick = {
                                                when (choice) {
                                                    is AccentChoice.Named ->
                                                        edit { it.withAccent(choice.named.accent) }
                                                    // The mixer opens on the
                                                    // colour the Event already
                                                    // has, so a colour that is
                                                    // nearly right is nudged
                                                    // rather than started again.
                                                    AccentChoice.Mixed -> mixing = stored.accent
                                                }
                                            },
                                        )
                                    }
                                }
                            }
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

    mixing?.let { mixed ->
        AccentMixer(
            mixed = mixed,
            dial = dialState(stored.toEvent(), today),
            onMix = { mixing = it },
            onSettle = {
                edit { event -> event.withAccent(mixed) }
                mixing = null
            },
            onDismiss = { mixing = null },
        )
    }
}

/** The Dial as the widget will draw it, redrawn whenever the Event changes. */
@Composable
private fun DialPreview(state: DialState, size: Dp = PREVIEW_SIZE) {
    Box(
        modifier = Modifier.fillMaxWidth().background(WALLPAPER_STAND_IN).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn at the pixel size it is shown at, so it is sharp rather than
        // scaled.
        val sizePx = with(LocalDensity.current) { size.roundToPx() }
        val bitmap = remember(state, sizePx) { renderDial(state, sizePx).asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = spokenAs(state),
            modifier = Modifier.size(size),
        )
    }
}

/**
 * Where a colour is mixed: three channels, and the Dial they make above them.
 *
 * The Dial is here rather than only behind the dialog because the point of
 * mixing a colour is watching it arrive on the white disc, and what is behind a
 * dialog is dimmed and half covered. It is the Event's own Dial with the colour
 * swapped, so what is being looked at is the widget.
 *
 * Every colour is allowed and none of them is argued with, white included. See
 * ADR-0011.
 *
 * @param mixed the colour as it stands.
 * @param dial the Event's Dial, whose Accent this replaces.
 * @param onMix a channel was moved.
 * @param onSettle the colour is the one, so the Event takes it.
 * @param onDismiss the mixing is over and the Event keeps the Accent it had.
 */
@Composable
private fun AccentMixer(
    mixed: Accent,
    dial: DialState,
    onMix: (Accent) -> Unit,
    onSettle: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mix a colour") },
        text = {
            // Scrolled, because three sliders and a Dial are taller than a
            // dialog on a phone held sideways.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialPreview(dial.copy(accent = mixed), size = MIXER_PREVIEW_SIZE)
                Text(
                    mixed.hex,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChannelSlider("Red", mixed.red) { onMix(mixed.withRed(it)) }
                ChannelSlider("Green", mixed.green) { onMix(mixed.withGreen(it)) }
                ChannelSlider("Blue", mixed.blue) { onMix(mixed.withBlue(it)) }
            }
        },
        confirmButton = { TextButton(onClick = onSettle) { Text("Use this colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One channel of the colour being mixed. The slider is continuous and the value
 * is rounded, rather than a slider of 256 steps: Material draws a tick for every
 * step it is given, and 255 ticks is a striped bar.
 */
@Composable
private fun ChannelSlider(label: String, value: Int, onValue: (Int) -> Unit) {
    Column {
        Text("$label $value", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = 0f..CHANNEL_MAX.toFloat(),
            // The number is in the label above, which a slider does not say by
            // itself, and out loud the two would otherwise be one anonymous bar.
            modifier = Modifier.semantics { contentDescription = label },
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

/**
 * One circle in the palette, named out loud because a circle of colour is only a
 * colour.
 *
 * The mixer's circle holds the colour that was mixed when the Event has one, and
 * the spectrum when it does not, so it shows either the colour in use or what it
 * is for. Every circle is drawn with an outline, which is what keeps a white one
 * a circle rather than a hole.
 *
 * @param choice which circle this is.
 * @param inUse the Accent the Event has, which decides which circle is ringed.
 */
@Composable
private fun AccentCircle(choice: AccentChoice, inUse: Accent, onClick: () -> Unit) {
    val selected = accentChoiceOf(inUse) == choice
    val filled = when (choice) {
        is AccentChoice.Named -> SolidColor(Color(choice.named.accent.argb))
        AccentChoice.Mixed ->
            if (selected) SolidColor(Color(inUse.argb)) else Brush.sweepGradient(SPECTRUM)
    }
    Box(
        modifier = Modifier
            .size(ACCENT_CIRCLE_DP.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = accentChoiceLabel(choice, inUse) }
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
            .background(filled),
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
