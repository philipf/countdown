package ninja.notnot.countdown

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the config screen's date picker needs, kept apart from the screen so it
 * can be checked on the JVM.
 *
 * The picker counts in milliseconds since the epoch at UTC; the Event Date is a
 * date with no time of day. Converting through epoch days rather than through an
 * instant keeps the device's timezone out of it, so the date the owner taps is
 * the date that is stored wherever they are.
 */
private const val MILLIS_PER_DAY = 86_400_000L

/** The date the picker means by [millis]. */
fun localDateFromPickerMillis(millis: Long): LocalDate =
    LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))

/** [date] as the picker wants it. */
fun pickerMillisOf(date: LocalDate): Long = date.toEpochDay() * MILLIS_PER_DAY

/**
 * The years the picker offers: a century ahead, so a long countdown is only ever
 * a scroll away, and stretched at either end to cover [selected]. The picker
 * refuses to open on a date outside its range, so the Event Date already chosen
 * is always inside it.
 */
fun pickerYearRange(today: LocalDate, selected: LocalDate?): IntRange {
    val selectedYear = selected?.year ?: today.year
    return minOf(today.year, selectedYear)..maxOf(today.year + YEARS_OFFERED, selectedYear)
}

/** The Event Date as the config screen shows it. Labels are English; see the PRD. */
fun formatEventDate(date: LocalDate): String = EVENT_DATE_FORMAT.format(date)

private const val YEARS_OFFERED = 100

private val EVENT_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
