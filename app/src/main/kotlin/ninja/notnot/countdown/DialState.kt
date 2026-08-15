package ninja.notnot.countdown

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Everything the Dial shows. The renderer draws exactly this and decides
 * nothing, so the widget and the config screen preview cannot disagree.
 *
 * @param primaryText the big text: a day count, "Today", or "Set a date".
 * @param labelText the text under it, or null when there is nothing to label.
 * @param arcFraction how much of the Progress Arc is filled, 0f..1f.
 * @param title the Event's title, or null when it has none.
 * @param accent the colour of the Progress Arc.
 */
data class DialState(
    val primaryText: String,
    val labelText: String?,
    val arcFraction: Float,
    val title: String?,
    val accent: Accent,
)

/**
 * The whole of the app's logic: the stored Event, or its absence, plus today's
 * date, in; everything the Dial shows, out.
 *
 * Today's date is a parameter rather than something looked up, so this function
 * depends on nothing but its arguments.
 *
 * Days Remaining is whole calendar days between two [LocalDate]s, not a
 * difference between two points in time. That is what makes daylight saving
 * irrelevant: a 23-hour day is still a day.
 */
fun dialState(event: Event?, today: LocalDate): DialState {
    if (event == null) {
        return DialState(
            primaryText = "Set a date",
            labelText = null,
            arcFraction = 0f,
            title = null,
            accent = Accent.DEFAULT,
        )
    }

    val daysRemaining = ChronoUnit.DAYS.between(today, event.eventDate)
    val (primaryText, labelText) = when {
        daysRemaining > 1L -> daysRemaining.toString() to "Days"
        daysRemaining == 1L -> "1" to "Day"
        daysRemaining == 0L -> "Today" to null
        else -> (-daysRemaining).toString() to "Days ago"
    }

    return DialState(
        primaryText = primaryText,
        labelText = labelText,
        arcFraction = arcFraction(event, today),
        title = event.title?.takeIf { it.isNotBlank() },
        accent = event.accent,
    )
}

/**
 * What the Dial says out loud. The Dial is a bitmap, so nothing in it reaches a
 * screen reader unless it is spelled out — once here, for the widget and the
 * config screen preview alike.
 */
fun spokenAs(state: DialState): String =
    listOfNotNull(state.primaryText, state.labelText, state.title).joinToString(" ")

/**
 * Elapsed days over the Anchor Date to Event Date span, clamped to 0f..1f. A
 * span of zero or less — the Event was set for today or for a past date — is
 * full, which also keeps the division away from zero.
 */
private fun arcFraction(event: Event, today: LocalDate): Float {
    val span = ChronoUnit.DAYS.between(event.anchorDate, event.eventDate)
    if (span <= 0L) return 1f
    val elapsed = ChronoUnit.DAYS.between(event.anchorDate, today)
    return (elapsed.toDouble() / span).toFloat().coerceIn(0f, 1f)
}
