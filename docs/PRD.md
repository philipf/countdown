# Countdown — v1

## Problem Statement

I have one date I care about and I want to know how far away it is without
thinking about it. Checking means opening a calendar and counting, so I don't
check, and the date creeps up on me.

## Solution

A home screen widget showing one number: how many days are left. It is correct
every morning without being opened. A single screen in the app sets the date,
gives it a name, and picks a colour.

## User Stories

### Setting the Event

1. As the owner, I want to open the app and see one screen, so that there is
   nothing to learn.
2. As the owner, I want to pick the Event Date from a date picker, so that I
   don't type dates.
3. As the owner, I want to pick a date years ahead, so that long countdowns work.
4. As the owner, I want to give the Event a title, so that the widget says what
   it is counting towards.
5. As the owner, I want to leave the title blank, so that an obvious Event
   doesn't need naming.
6. As the owner, I want to pick an Accent from four colours, so that the widget
   suits my home screen.
7. As the owner, I want every change saved as I make it, so that there is no
   Save button and nothing to lose.
8. As the owner, I want a live Dial preview above the fields, so that I see the
   result before I leave the screen.
9. As the owner, I want the preview to look exactly like the widget, so that
   there is no surprise when I go back to the home screen.
10. As the owner, I want to change the Event Date later, so that I can reuse the
    app for the next thing.
11. As the owner, I want changing the date to reset the Progress Arc, so that the
    arc measures the wait I am actually in.
12. As the owner, I want changing only the title or Accent to leave the arc
    alone, so that renaming doesn't restart the countdown.

### The widget

13. As the owner, I want to add the widget from the launcher's widget picker, so
    that it goes on my home screen the normal way.
14. As the owner, I want the widget to work as soon as I drop it, so that there
    is no setup step during placement.
15. As the owner, I want the widget to show Days Remaining as a large number, so
    that I can read it at a glance.
16. As the owner, I want the label under the number to read "Days", so that the
    number means something.
17. As the owner, I want the label to read "Day" when one day is left, so that it
    isn't wrong.
18. As the owner, I want the title under the Dial, so that the widget has
    context.
19. As the owner, I want the title dropped when the widget is too small to show
    it legibly, so that it never appears as unreadable specks.
20. As the owner, I want the Progress Arc to fill as the Event approaches, so
    that I feel it getting closer without reading the number.
21. As the owner, I want a transparent background, so that the Dial sits on my
    wallpaper instead of on a card.
22. As the owner, I want the Progress Arc visible against the white disc
    whichever Accent I pick, so that no colour choice is a bad one.
23. As the owner, I want to resize the widget, so that I can make it as big as it
    deserves.
24. As the owner, I want the Dial redrawn at the new size when I resize, so that
    it stays sharp.
25. As the owner, I want to tap the widget to open the app, so that changing the
    date is one tap away.
26. As the owner, I want more than one copy of the widget to show the same thing,
    so that placing a second one is harmless.

### Days passing

27. As the owner, I want the number to drop by one at local midnight, so that it
    is right when I first look at my phone.
28. As the owner, I want the widget correct after a reboot, so that restarting my
    phone doesn't freeze the number.
29. As the owner, I want the widget correct after I change timezone, so that
    travelling doesn't break it.
30. As the owner, I want a day to count as a day across a daylight-saving change,
    so that a 23-hour day doesn't skip or repeat.
31. As the owner, I want the widget to read "Today" on the day itself, so that it
    doesn't say "0 Days".
32. As the owner, I want the Progress Arc full on the day, so that the Dial looks
    finished.
33. As the owner, I want the widget to count upwards after the Event, reading
    "3 Days ago", so that it stays truthful until I get round to changing it.
34. As the owner, I want the widget to redraw the moment I save a change, so that
    I don't wait for midnight to see it.

### First run

35. As the owner, I want a widget placed before I have set anything to say "Set a
    date", so that it isn't a blank square.
36. As the owner, I want that empty widget to open the app when tapped, so that
    it tells me what to do and then lets me do it.
37. As the owner, I want no fake starter Event, so that the widget never shows a
    number I didn't choose.

### Installing it

38. As the owner, I want a signed APK I can copy to my phone, so that I don't
    need an app store.
39. As the owner, I want later builds to install over the top, so that upgrading
    keeps my Event.
40. As the owner, I want the build toolchain confined to the repo, so that my
    machine isn't left with Android tooling on it.

## Implementation Decisions

Vocabulary follows `CONTEXT.md`. Decisions already recorded as ADRs are
referenced rather than repeated.

### Shape

- Native Android, Kotlin. Compose for the config screen, `AppWidgetProvider` and
  `RemoteViews` for the widget. See ADR-0001.
- One module, one app. No library split — the domain core is a package inside the
  app module.
- `applicationId` is `ninja.notnot.countdown`. `minSdk` 26, which gives
  `java.time` without desugaring. `targetSdk` is the current platform.

### Domain

- There is exactly one Event, app-wide. It is not a list with one entry.
- The Event holds: Event Date, title (optional), Accent, Anchor Date.
- Days Remaining is calendar days between `LocalDate` values in the device's
  current zone, not a difference between instants. This is what makes
  daylight-saving irrelevant.
- The Anchor Date is written whenever the Event Date changes, and only then.
- Progress Arc fraction is elapsed days over the Anchor Date to Event Date span.
  It is clamped to 0..1. When the span is zero or negative — the Event was set
  for today or the past — the fraction is 1.

### Where the tests attach

One pure function is the whole of the app's logic and the only thing under test.
It takes the stored Event, or its absence, plus today's date, and returns
everything the renderer needs:

```
dialState(event: Event?, today: LocalDate): DialState

DialState(
  primaryText: String,   // "7", "Today", "Set a date"
  labelText: String?,    // "Days", "Day", "Days ago"; null when there is no label
  arcFraction: Float,    // 0f..1f
  title: String?,        // null when blank or absent
  accent: Accent,
)
```

The renderer draws exactly this and decides nothing. Storage, alarms, the Canvas
and Compose all sit outside this function and are not tested.

### Rendering

- One renderer draws the whole Dial to a bitmap. See ADR-0002.
- The widget layout is a single `ImageView`. The config screen preview draws the
  same bitmap.
- The renderer takes a `DialState` and a pixel size, and returns a bitmap. It
  holds no Android context beyond what drawing needs.
- Whether the title is drawn is the renderer's call, based on the pixel size it
  was given.
- The widget is resizable with a 2×2 minimum and a 2×2 default. It redraws on
  `onAppWidgetOptionsChanged`.

### Accent palette

Four fixed colours: blue `#0288D1` (default), black, mid-grey, red. White is not
offered — it would be invisible on the white disc.

### Day Rollover

- An inexact alarm set for the next local midnight, which reschedules itself when
  it fires. See ADR-0003.
- `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` receivers re-arm it, because
  alarms don't survive a reboot and point at the wrong instant after a zone
  change.
- Saving a change in the app redraws the widget immediately and re-arms the alarm.

### Storage

- `SharedPreferences`, read synchronously. The widget's redraw runs in a broadcast
  receiver, where a blocking read is simpler and safer than a coroutine.
- Backup is off. The Event is one date; restoring it onto a new phone is not worth
  the surface.

### Build

- Toolchain via mise, pinned in the repo. See ADR-0004.
- A release keystore is generated once and kept outside the repo. Its path and
  key name are plain build config; its password is kept in `pass`. See ADR-0005.
  The APK is installed over `adb`.

## Testing Decisions

A good test here states an input and an expected output and says nothing about
how the answer was reached. It should survive any rewrite that keeps the
behaviour.

- The only tested module is the domain core, through `dialState`. JVM unit tests,
  no emulator, no Robolectric.
- Today's date is a parameter, not something the code looks up, so there is no
  clock to fake and no flakiness at midnight.
- Cases to cover:
  - A future Event: number, label, and the singular label at one day.
  - The day itself: "Today", no number, arc full.
  - After the Event: counts up, "Days ago", arc stays full.
  - No Event: "Set a date", empty arc, no title.
  - A blank or whitespace title reads as no title.
  - Arc fraction at the Anchor Date, midway, and on the Event Date.
  - Anchor Date equal to or after the Event Date: fraction is 1, not a crash or
    a NaN.
  - A span crossing a daylight-saving change counts the same as any other span.
  - A span crossing a year boundary, and a leap day.
  - Dates far enough out that a naive hours calculation would overflow.
- No prior art in this repo — it is new. These are the first tests.
- The renderer, storage, the alarm chain and the widget provider are not tested.
  They are thin, and a wrong Dial is visible on the home screen within a day.

## Out of Scope

- More than one Event.
- Recurrence.
- A notes field.
- A time of day on the Event. The Event is a date.
- Notifications, alarms or reminders for the user. The widget is passive.
- Play Store release, app signing by Google, update checks.
- Backup, sync, export, or moving the Event between devices.
- A custom colour picker, custom fonts, or a light/dark variant of the Dial.
- Lock screen widgets, Wear OS, tiles, or shortcuts.
- Localisation. Labels are English.
- Widget configuration on placement. There is nothing per-widget to configure.

## Further Notes

- The JDK is pinned to an LTS the Android Gradle Plugin supports, which may not
  be the newest LTS. This is checked when the build first runs, not assumed.
- Text inside the Dial ignores the system font size setting, because the Dial is
  a bitmap. This is accepted, and noted in ADR-0002.
- The four Accent colours were chosen to be visible on a white disc. Any future
  palette change has the same constraint.
