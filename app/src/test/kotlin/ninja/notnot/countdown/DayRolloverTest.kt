package ninja.notnot.countdown

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * When the next Day Rollover is, which is the one decision in this ticket that
 * can be wrong quietly. Everything else — the alarm, the receiver, the redraw —
 * is a few lines of Android that either run or do not, and a wrong Dial is on
 * the home screen within a day.
 *
 * The hard cases are all days that are not 24 hours long, or that do not start
 * at midnight, and every one of them is a real date in a real zone rather than
 * an invented one.
 *
 * The declarations are checked as text, as in [WidgetTest], because without them
 * nothing reaches the receiver at all and there is no way to see that without a
 * device.
 */
class DayRolloverTest {

    @Nested
    @DisplayName("An ordinary day")
    inner class OrdinaryDay {

        @Test
        fun `rolls over at the next local midnight`() {
            assertEquals(
                instantAt("UTC", "2026-08-16T00:00"),
                nextDayRollover(instantAt("UTC", "2026-08-15T09:30"), ZoneId.of("UTC")),
            )
        }

        @Test
        fun `is twenty-four hours long`() {
            assertEquals(
                Duration.ofHours(24),
                gapFrom("Europe/London", "2026-06-15T00:00"),
            )
        }

        @Test
        fun `rolls over a millisecond after the last millisecond of the day`() {
            assertEquals(
                instantAt("Europe/London", "2026-06-16T00:00"),
                nextDayRollover(
                    instantAt("Europe/London", "2026-06-15T23:59:59.999"),
                    ZoneId.of("Europe/London"),
                ),
            )
        }

        @Test
        fun `at midnight itself means tomorrow's midnight, not this one`() {
            // Whatever arms the alarm at 00:00:00 exactly must be given a time
            // to come, or the alarm fires at once and again and again.
            assertEquals(
                Duration.ofHours(24),
                gapFrom("UTC", "2026-08-15T00:00"),
            )
        }
    }

    @Nested
    @DisplayName("A daylight-saving change")
    inner class DaylightSaving {

        @Test
        fun `makes the day the clocks go forward twenty-three hours long`() {
            assertEquals(Duration.ofHours(23), gapFrom("America/New_York", "2026-03-08T00:00"))
            assertEquals(Duration.ofHours(23), gapFrom("Europe/London", "2026-03-29T00:00"))
        }

        @Test
        fun `makes the day the clocks go back twenty-five hours long`() {
            assertEquals(Duration.ofHours(25), gapFrom("America/New_York", "2026-11-01T00:00"))
            assertEquals(Duration.ofHours(25), gapFrom("Europe/London", "2026-10-25T00:00"))
        }

        @Test
        fun `still lands on midnight, at the offset the new day has`() {
            assertEquals(
                ZonedDateTime.parse("2026-03-09T00:00-04:00[America/New_York]"),
                rolloverFrom("America/New_York", "2026-03-08T00:00"),
            )
            assertEquals(
                ZonedDateTime.parse("2026-11-02T00:00-05:00[America/New_York]"),
                rolloverFrom("America/New_York", "2026-11-01T00:00"),
            )
        }
    }

    @Nested
    @DisplayName("A day with no midnight")
    inner class SkippedMidnight {

        // São Paulo's clocks went from 23:59:59 on 3 November 2018 straight to
        // 01:00 on the 4th. There is no 00:00 that day to set an alarm for.

        @Test
        fun `rolls over at the first time the day actually has`() {
            assertEquals(
                ZonedDateTime.parse("2018-11-04T01:00-02:00[America/Sao_Paulo]"),
                rolloverFrom("America/Sao_Paulo", "2018-11-03T23:30"),
            )
        }

        @Test
        fun `is a day like any other, an hour short and an hour late`() {
            assertEquals(Duration.ofHours(24), gapFrom("America/Sao_Paulo", "2018-11-03T00:00"))
        }

        @Test
        fun `does not roll over twice`() {
            // Half past midnight on the 4th does not exist, so the first moment
            // after the skipped midnight is 01:00, and from there the next Day
            // Rollover is the 5th rather than the 4th over again.
            assertEquals(
                ZonedDateTime.parse("2018-11-05T00:00-02:00[America/Sao_Paulo]"),
                rolloverFrom("America/Sao_Paulo", "2018-11-04T01:00"),
            )
        }
    }

    @Nested
    @DisplayName("A flight to another timezone")
    inner class ZoneChange {

        @Test
        fun `moves the next Day Rollover, from the same instant`() {
            val now = Instant.parse("2026-06-15T20:00:00Z")

            assertEquals(
                Instant.parse("2026-06-15T23:00:00Z"),
                nextDayRollover(now, ZoneId.of("Europe/London")),
            )
            assertEquals(
                Instant.parse("2026-06-16T12:00:00Z"),
                nextDayRollover(now, ZoneId.of("Pacific/Auckland")),
            )
            assertEquals(
                Instant.parse("2026-06-16T10:00:00Z"),
                nextDayRollover(now, ZoneId.of("Pacific/Kiritimati")),
            )
        }

        @Test
        fun `can be hours away in a zone where the old alarm had just fired`() {
            // Landing in Auckland just after midnight in London: the alarm set
            // before the flight is spent, and the next one is fifteen hours off,
            // not twenty-four.
            assertEquals(
                Duration.ofHours(15),
                Duration.between(
                    Instant.parse("2026-06-15T21:00:00Z"),
                    nextDayRollover(
                        Instant.parse("2026-06-15T21:00:00Z"),
                        ZoneId.of("Pacific/Auckland"),
                    ),
                ),
            )
        }
    }

    @Nested
    @DisplayName("A year of Day Rollovers")
    inner class AYear {

        @Test
        fun `visits every date once, in every zone`() {
            for (zoneName in ZONES) {
                val zone = ZoneId.of(zoneName)
                var instant = instantAt(zoneName, "2018-01-01T12:00")
                var date = LocalDate.parse("2018-01-01")

                repeat(DAYS_IN_2018 - 1) {
                    val next = nextDayRollover(instant, zone)
                    date = date.plusDays(1)

                    assertTrue(next.isAfter(instant), "$zoneName: $next is not after $instant")
                    assertEquals(date, next.atZone(zone).toLocalDate(), "in $zoneName")
                    instant = next
                }
            }
        }

        @Test
        fun `each one is the first instant of the day it lands in`() {
            for (zoneName in ZONES) {
                val zone = ZoneId.of(zoneName)
                var instant = instantAt(zoneName, "2018-01-01T12:00")

                repeat(DAYS_IN_2018 - 1) {
                    instant = nextDayRollover(instant, zone)
                    val landed = instant.atZone(zone)

                    assertEquals(
                        landed.toLocalDate().atStartOfDay(zone),
                        landed,
                        "$zoneName: $landed is not where its day starts",
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("What the system is told")
    inner class SystemDeclarations {

        @Test
        fun `the rollover is worked out with no Android in it, so it can be tested here`() {
            assertNull(Regex("""import\s+android[x.]""").find(appSource("DayRollover.kt"))?.value)
        }

        @Test
        fun `the app may hear that the phone has rebooted`() {
            assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        }

        @Test
        fun `a receiver is declared for the three broadcasts that invalidate the alarm`() {
            assertTrue(manifest.contains("""android:name=".DayRolloverReceiver""""))
            for (action in listOf("BOOT_COMPLETED", "TIME_SET", "TIMEZONE_CHANGED")) {
                assertTrue(
                    manifest.contains("""<action android:name="android.intent.action.$action" />"""),
                    "nothing would re-arm the alarm after $action",
                )
            }
        }

        @Test
        fun `the alarm is inexact, so it needs no permission the owner has to grant`() {
            assertTrue(alarmSource.contains("setAndAllowWhileIdle"))
            assertNull(
                Regex("""setExact|setRepeating|setAlarmClock|setWindow""").find(alarmSource)?.value,
            )
        }

        @Test
        fun `nothing is on the framework's timer`() {
            assertNull(Regex("""updatePeriodMillis="[1-9]""").find(widgetInfo)?.value)
        }

        @Test
        fun `every arming describes the same alarm, so one replaces the last`() {
            // A PendingIntent is the same one when the request code matches and
            // the Intents match on everything but their extras. One builder,
            // one constant request code, and no extras is what makes that hold.
            assertEquals(
                1,
                Regex("""PendingIntent\.getBroadcast""").findAll(alarmSource).count(),
                "an alarm built in two places is two alarms waiting to happen",
            )
            assertTrue(alarmSource.contains("DAY_ROLLOVER_REQUEST_CODE"))
            assertNull(Regex("""putExtra""").find(alarmSource)?.value)
        }

        @Test
        fun `saving a change draws the Dial and sets the alarm`() {
            assertTrue(appSource("EventStore.kt").contains("drawDialForToday("))
        }

        @Test
        fun `nothing can redraw the Dial without setting the alarm`() {
            // One way in, and it does both. Everything else in that file is
            // private to it, so a future caller cannot do half of it.
            val widget = appSource("CountdownWidget.kt")

            assertTrue(widget.contains("armDayRollover("), "the one way in must set the alarm")
            assertEquals(
                listOf("drawDialForToday"),
                Regex("""(?m)^(private )?fun (\w+)\(""").findAll(widget)
                    .filter { it.groupValues[1].isEmpty() }
                    .map { it.groupValues[2] }
                    .toList(),
            )
        }
    }

    private companion object {
        /**
         * Zones that break a naive calculation: clocks forward and back, a day
         * with no midnight, half-hour and three-quarter-hour offsets, and the
         * two ends of the date line. 2018, because that is the year São Paulo
         * skipped a midnight.
         */
        val ZONES = listOf(
            "UTC",
            "Europe/London",
            "America/New_York",
            "America/Sao_Paulo",
            "Australia/Lord_Howe",
            "Asia/Kathmandu",
            "Pacific/Chatham",
            "Pacific/Kiritimati",
        )

        const val DAYS_IN_2018 = 365

        val manifest: String get() = withoutComments(appFile("src/main/AndroidManifest.xml"))

        val widgetInfo: String get() = withoutComments(appFile("src/main/res/xml/widget_info.xml"))

        val alarmSource: String get() = appSource("DayRolloverAlarm.kt")

        /** The instant a local wall clock reading names in a zone. */
        fun instantAt(zone: String, local: String): Instant =
            LocalDateTime.parse(local).atZone(ZoneId.of(zone)).toInstant()

        /** The next Day Rollover from a local wall clock reading, as one. */
        fun rolloverFrom(zone: String, local: String): ZonedDateTime =
            nextDayRollover(instantAt(zone, local), ZoneId.of(zone)).atZone(ZoneId.of(zone))

        /** How long until the next Day Rollover from a local wall clock reading. */
        fun gapFrom(zone: String, local: String): Duration =
            Duration.between(
                instantAt(zone, local),
                nextDayRollover(instantAt(zone, local), ZoneId.of(zone)),
            )
    }
}
