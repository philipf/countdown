# Countdown — v2

## Problem Statement

I have a handful of dates I care about and I want to know how far away they are
without thinking about it. Checking means opening a calendar and counting, so I
don't check, and the dates creep up on me.

## Solution

Home screen widgets, each showing one number: how many days are left until one
Event. They are correct every morning without being opened. The app holds the
list of Events, and a screen behind each one sets its date, gives it a name and
picks a colour. A widget is pointed at an Event when it is placed.

v1 held exactly one Event and every widget showed it. What has changed is that
there are now many, that the app lists them, and that a widget has to be told
which one it is for. The Dial itself, the arithmetic behind it and the way it is
drawn are untouched.

## User Stories

### Keeping several Events

1. As the owner, I want to see all my Events in one list, so that I know what is
   coming.
2. As the owner, I want the list ordered by how soon each Event is, so that the
   next thing is at the top.
3. As the owner, I want to add an Event, so that a new date joins the others.
4. As the owner, I want to tap an Event to change it, so that editing it is where
   I found it.
5. As the owner, I want to delete an Event, so that the list is the things I
   still care about, and I want to be asked first, so that a stray tap doesn't
   lose a date I can't get back.
6. As the owner, I want each Event to keep its own date, title, Accent and
   Progress Arc, so that changing one does nothing to the others.
7. As the owner, I want an Event I have added but not yet dated to say so in the
   list, so that it is not a blank row.

### Setting an Event

8. As the owner, I want to pick the Event Date from a date picker, so that I
   don't type dates.
9. As the owner, I want to pick a date years ahead, so that long countdowns work.
10. As the owner, I want to give an Event a title, so that the widget says what
    it is counting towards.
11. As the owner, I want to leave the title blank, so that an obvious Event
    doesn't need naming.
12. As the owner, I want to pick an Accent from four colours, so that the widget
    suits my home screen.
13. As the owner, I want every change saved as I make it, so that there is no
    Save button and nothing to lose.
14. As the owner, I want a live Dial preview above the fields, so that I see the
    result before I leave the screen.
15. As the owner, I want the preview to look exactly like the widget, so that
    there is no surprise when I go back to the home screen.
16. As the owner, I want to change an Event Date later, so that I can reuse an
    Event for the next thing.
17. As the owner, I want changing the date to reset that Event's Progress Arc, so
    that the arc measures the wait I am actually in.
18. As the owner, I want changing only the title or Accent to leave the arc
    alone, so that renaming doesn't restart the countdown.
19. As the owner, I want to go back to the list from an Event, so that editing
    one thing doesn't trap me on its screen.

### The widget

20. As the owner, I want to add the widget from the launcher's widget picker, so
    that it goes on my home screen the normal way.
21. As the owner, I want to choose which Event a widget shows when I place it, so
    that the widget is for the date I meant.
22. As the owner, I want that chooser to show the same Events in the same order
    as the app, so that I don't have to work out which is which.
23. As the owner, I want to back out of the chooser without a widget appearing,
    so that changing my mind leaves nothing behind.
24. As the owner, I want the widget to show Days Remaining as a large number, so
    that I can read it at a glance.
25. As the owner, I want the label under the number to read "Days", so that the
    number means something.
26. As the owner, I want the label to read "Day" when one day is left, so that it
    isn't wrong.
27. As the owner, I want the Event's title under the Dial, so that the widget has
    context.
28. As the owner, I want the title dropped when the widget is too small to show
    it legibly, so that it never appears as unreadable specks.
29. As the owner, I want the Progress Arc to fill as the Event approaches, so
    that I feel it getting closer without reading the number.
30. As the owner, I want a transparent background, so that the Dial sits on my
    wallpaper instead of on a card.
31. As the owner, I want the Progress Arc visible against the white disc
    whichever Accent I pick, so that no colour choice is a bad one.
32. As the owner, I want to resize the widget, so that I can make it as big as it
    deserves.
33. As the owner, I want the Dial redrawn at the new size when I resize, so that
    it stays sharp.
34. As the owner, I want to tap a widget to open the app, so that changing a date
    is one tap away.
35. As the owner, I want two widgets on two different Events to show two
    different numbers, so that the home screen can hold more than one countdown.
36. As the owner, I want two widgets on the same Event to show the same thing, so
    that placing a second one is harmless.
37. As the owner, I want a widget whose Event I deleted to say "Set a date", so
    that it never leaves a number standing for something that is gone.
38. As the owner, I want removing a widget to leave its Event alone, so that
    tidying the home screen doesn't lose a date.

### Days passing

39. As the owner, I want every number to drop by one at local midnight, so that
    they are right when I first look at my phone.
40. As the owner, I want the widgets correct after a reboot, so that restarting
    my phone doesn't freeze the numbers.
41. As the owner, I want the widgets correct after I change timezone, so that
    travelling doesn't break them.
42. As the owner, I want a day to count as a day across a daylight-saving change,
    so that a 23-hour day doesn't skip or repeat.
43. As the owner, I want a widget to read "Today" on the day itself, so that it
    doesn't say "0 Days".
44. As the owner, I want the Progress Arc full on the day, so that the Dial looks
    finished.
45. As the owner, I want a widget to count upwards after its Event, reading
    "3 Days ago", so that it stays truthful until I get round to deleting it.
46. As the owner, I want every widget showing an Event to redraw the moment I
    save a change to it, so that I don't wait for midnight to see it.

### First run

47. As the owner, I want an empty list to tell me to add an Event, so that it
    isn't a blank screen.
48. As the owner, I want the widget's chooser to tell me to add an Event first
    when I have none, so that I know why there is nothing to pick.
49. As the owner, I want no fake starter Event, so that no widget ever shows a
    number I didn't choose.

### Installing it

50. As the owner, I want a signed APK I can copy to my phone, so that I don't
    need an app store.
51. As the owner, I want later builds to install over the top, so that upgrading
    keeps my Events — including the single Event I had before there was a list.
52. As the owner, I want the build toolchain confined to the repo, so that my
    machine isn't left with Android tooling on it.

## Implementation Decisions

Vocabulary follows `CONTEXT.md`. Decisions already recorded as ADRs are
referenced rather than repeated.

### Shape

- Native Android, Kotlin. Compose for the app's screens, `AppWidgetProvider` and
  `RemoteViews` for the widget. See ADR-0001.
- One module, one app. No library split — the domain core is a package inside the
  app module.
- `applicationId` is `ninja.notnot.countdown`. `minSdk` 26, which gives
  `java.time` without desugaring. `targetSdk` is the current platform.
- Two screens: the Event list and the Event editor. The editor is v1's single
  screen unchanged, now reached by tapping a row instead of by opening the app.
- One more activity, the widget's Event chooser, named by `android:configure`.
  See ADR-0009.

### Domain

- There are many Events. Each holds an id, an Event Date, a title (optional), an
  Accent and an Anchor Date.
- An Event is not required to have a date. One that has just been added has none,
  and until it does there is nothing to count to.
- The id is the app's own and the owner never sees it. It exists so that a widget
  can name the Event it shows. See ADR-0008.
- Days Remaining is calendar days between `LocalDate` values in the device's
  current zone, not a difference between instants. This is what makes
  daylight-saving irrelevant.
- An Event's Anchor Date is written whenever its Event Date changes, and only
  then.
- Progress Arc fraction is elapsed days over the Anchor Date to Event Date span.
  It is clamped to 0..1. When the span is zero or negative — the Event was set
  for today or the past — the fraction is 1.
- The list is ordered by Event Date, soonest first, undated Events at the top,
  ties broken by title and then by id. The order is worked out, never stored.

### Where the tests attach

The function that decides what a Dial shows is unchanged. It takes one Event, or
its absence, plus today's date, and returns everything the renderer needs:

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

Having many Events adds three more decisions worth the same treatment, each a
value in and a value out:

- Turning stored keys into a set of Events and back, including reading v1's
  unprefixed keys once.
- The order the list is shown in.
- Resolving a widget to its Event: an `appWidgetId` and what is stored give an
  Event, or nothing when the binding is missing or names an Event that is gone.

The renderer draws exactly a `DialState` and decides nothing. Storage, alarms,
the Canvas and Compose all sit outside these functions and are not tested.

### Rendering

- One renderer draws the whole Dial to a bitmap. See ADR-0002.
- The widget layout is a single `ImageView`. The editor's preview draws the same
  bitmap.
- The renderer takes a `DialState` and a pixel size, and returns a bitmap. It
  holds no Android context beyond what drawing needs.
- Whether the title is drawn is the renderer's call, based on the pixel size it
  was given.
- A redraw groups widgets by Bound Event and pixel size together, so copies
  showing the same Event at the same size still share one bitmap.
- The widget is resizable with a 2×2 minimum and a 2×2 default. It redraws on
  `onAppWidgetOptionsChanged`.

### Accent palette

Four fixed colours: blue `#0288D1` (default), black, mid-grey, red. White is not
offered — it would be invisible on the white disc.

### Day Rollover

- One inexact alarm set for the next local midnight, which reschedules itself
  when it fires. See ADR-0003. Every Event rolls over at the same midnight, so
  one alarm serves them all however many widgets there are.
- `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` receivers re-arm it, because
  alarms don't survive a reboot and point at the wrong instant after a zone
  change.
- Saving a change in the app redraws the widgets and re-arms the alarm.

### Storage

- `SharedPreferences`, read synchronously. The widget's redraw runs in a broadcast
  receiver, where a blocking read is simpler and safer than a coroutine.
- Events are held under keys namespaced by id, with one key listing the ids that
  exist. See ADR-0008.
- Which Event a widget shows is held in a second file, keyed by `appWidgetId`.
  See ADR-0009.
- Backup is off. The Events are a few dates; restoring them onto a new phone is
  not worth the surface.

### Build

- Toolchain via mise, pinned in the repo. See ADR-0004.
- A release keystore is generated once and kept outside the repo. Its path and
  key name are plain build config; its password is kept in `pass`. See ADR-0005.
  The APK is installed over `adb`.

## Testing Decisions

A good test here states an input and an expected output and says nothing about
how the answer was reached. It should survive any rewrite that keeps the
behaviour.

- The tested part is the domain core: `dialState` and the three decisions named
  above. JVM unit tests, no emulator, no Robolectric.
- Today's date is a parameter, not something the code looks up, so there is no
  clock to fake and no flakiness at midnight.
- Cases to cover for one Event's Dial, unchanged from v1:
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
- Cases to cover for many Events:
  - Several Events written and read back, each keeping its own fields.
  - Editing one Event leaves the others' fields and Anchor Dates alone.
  - Deleting an Event leaves the others readable and takes its keys with it.
  - Keys left behind by an id that is no longer listed read as nothing.
  - An id is never handed out twice.
  - v1's unprefixed keys read as one Event, and the second read finds it stored
    the new way.
  - The list order: by date, undated first, and deterministic on a tie.
- Cases to cover for the widget's binding:
  - An `appWidgetId` with a binding resolves to that Event.
  - An `appWidgetId` with no binding resolves to nothing.
  - A binding naming a deleted Event resolves to nothing.
  - Removing a binding leaves the other widgets' bindings alone.
- The renderer, the alarm chain, the widget provider, the chooser activity and
  the reads and writes to disk are not tested. They are thin, and a wrong Dial is
  visible on the home screen within a day.

## Out of Scope

- Recurrence.
- A notes field.
- A time of day on an Event. An Event is a date.
- Notifications, alarms or reminders for the user. The widget is passive.
- Reordering the list by hand. Order is worked out from the dates.
- Re-pointing a widget at a different Event after it is placed. See ADR-0009.
- Adding an Event from the widget's chooser. It sends you to the app.
- Play Store release, app signing by Google, update checks.
- Backup, sync, export, or moving Events between devices.
- A custom colour picker, custom fonts, or a light/dark variant of the Dial.
- Lock screen widgets, Wear OS, tiles, or shortcuts.
- Localisation. Labels are English.

## Further Notes

- The JDK is pinned to an LTS the Android Gradle Plugin supports, which may not
  be the newest LTS. This is checked when the build first runs, not assumed.
- Text inside the Dial ignores the system font size setting, because the Dial is
  a bitmap. This is accepted, and noted in ADR-0002.
- The four Accent colours were chosen to be visible on a white disc. Any future
  palette change has the same constraint.
- Nothing limits how many Events there can be. `SharedPreferences` would be the
  wrong home for thousands, and nothing in the app can make thousands.
