package ninja.notnot.countdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * The app's single screen: the Dial preview on top, the fields that set it
 * below. There is no Save button — every edit is written as it is made.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = EventStore(this)
        // Read once, here rather than in composition: the screen is the only
        // writer, so what it holds and what is on disk cannot drift apart.
        val stored = store.read()
        setContent {
            MaterialTheme {
                ConfigScreen(
                    initial = stored,
                    today = LocalDate.now(),
                    onChange = store::write,
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    initial: StoredEvent,
    today: LocalDate,
    onChange: (StoredEvent) -> Unit,
) {
    var stored by remember { mutableStateOf(initial) }
    var pickingDate by remember { mutableStateOf(false) }

    fun edit(change: (StoredEvent) -> StoredEvent) {
        stored = change(stored).also(onChange)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

/** The Dial is a bitmap, so it has to say out loud what it shows. */
private fun spokenAs(state: DialState): String =
    listOfNotNull(state.primaryText, state.labelText, state.title).joinToString(" ")
