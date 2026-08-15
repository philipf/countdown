package ninja.notnot.countdown

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The date the owner taps has to be the date that is stored, and it has to read back. */
class EventDatesTest {

    @Nested
    @DisplayName("The picker's milliseconds")
    inner class PickerMillis {

        @Test
        fun `a date survives the trip out and back`() {
            for (iso in listOf("1970-01-01", "2026-08-15", "2027-12-25", "2126-02-28", "1969-07-20")) {
                val date = date(iso)

                assertEquals(date, localDateFromPickerMillis(pickerMillisOf(date)), iso)
            }
        }

        @Test
        fun `the epoch is the epoch`() {
            assertEquals(0L, pickerMillisOf(date("1970-01-01")))
            assertEquals(date("1970-01-01"), localDateFromPickerMillis(0L))
        }

        @Test
        fun `any time of day inside a UTC day is that day`() {
            val day = pickerMillisOf(date("2026-08-15"))

            assertEquals(date("2026-08-15"), localDateFromPickerMillis(day))
            assertEquals(date("2026-08-15"), localDateFromPickerMillis(day + 86_399_999L))
        }

        @Test
        fun `dates before the epoch round down rather than towards zero`() {
            val day = pickerMillisOf(date("1969-12-31"))

            assertEquals(date("1969-12-31"), localDateFromPickerMillis(day))
            assertEquals(date("1969-12-31"), localDateFromPickerMillis(day + 1L))
        }

        @Test
        fun `a date a century out is still a date`() {
            val date = date("2126-08-15")

            assertEquals(date, localDateFromPickerMillis(pickerMillisOf(date)))
        }
    }

    @Nested
    @DisplayName("The years the picker offers")
    inner class YearRange {

        @Test
        fun `reaches a century ahead, so long countdowns can be set`() {
            val years = pickerYearRange(date("2026-08-15"), selected = null)

            assertTrue(2126 in years, "$years should reach 2126")
            assertEquals(2026, years.first)
        }

        @Test
        fun `includes the Event Date already chosen, even after it has passed`() {
            val years = pickerYearRange(date("2026-08-15"), selected = date("2019-04-01"))

            assertTrue(2019 in years, "$years should include 2019")
        }

        @Test
        fun `includes an Event Date further out than a century`() {
            val years = pickerYearRange(date("2026-08-15"), selected = date("2200-01-01"))

            assertTrue(2200 in years, "$years should include 2200")
        }

        @Test
        fun `does not start later than this year for a date years ahead`() {
            val years = pickerYearRange(date("2026-08-15"), selected = date("2030-04-01"))

            assertEquals(2026, years.first)
        }
    }

    @Nested
    @DisplayName("How a date is shown")
    inner class Shown {

        @Test
        fun `reads as a date a person would write`() {
            assertEquals("25 December 2027", formatEventDate(date("2027-12-25")))
            assertEquals("1 March 2026", formatEventDate(date("2026-03-01")))
        }
    }

    private companion object {
        fun date(iso: String): LocalDate = LocalDate.parse(iso)
    }
}
