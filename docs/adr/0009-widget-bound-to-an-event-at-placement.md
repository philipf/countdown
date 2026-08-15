# Bind a widget copy to an Event when it is placed

v1 had no configuration activity: one Event app-wide left a copy of the widget
nothing to choose. With many Events, a copy has to know which one it shows.

Showing whichever Event is soonest was considered, and is tempting — no
per-widget state, nothing to clean up, and placement stays the one-step thing v1
asked for. It was rejected because every copy would then show the same
Event, which takes away the reason for keeping several, and because a date years
out could never be watched: something nearer would always be in front of it.

So: `android:configure` names a picker. Dropping a widget opens a list of Events,
and choosing one binds that copy to it.

The binding lives in its own `SharedPreferences` file, `widget_events`, mapping
`widget.<appWidgetId>` to an Event id. It is not kept beside the Events because
the two have different lifetimes: removing a widget must not touch an Event, and
deleting an Event must not have to know what is on the home screen.

The picker writes the binding before it calls `setResult(RESULT_OK)`. The
launcher drops the widget if the activity finishes without an OK, so the order
matters — an OK with no binding on disk leaves a widget on the home screen
pointing at nothing.

`onDeleted` removes the binding. Android reuses `appWidgetId`s, so without it the
file grows without limit and a recycled id inherits whatever the last widget was
showing.

`onDeleted` is a broadcast, though, and a broadcast can be missed — the app can
be force-stopped, or have its data cleared while the widgets stay where they are.
So the redraw sweeps as well: it already asks `AppWidgetManager` which copies are
on the home screen, and every binding that is not one of them goes before the
Dials do. That is the only moment the app can tell a stale binding from a live
one, and it costs a disk write only when there is something to remove.

An Event can still be deleted while a widget shows it. That widget then reads as
unbound and draws the same Dial as a first run — "Set a date" — and tapping it
opens the app. Refusing the deletion, or warning about it, would make the app
know about the home screen, which is what the separate file avoids.

## Consequences

- v1's promise that the widget works as soon as it is dropped is gone, and
  "widget configuration on placement" is no longer out of scope. Placement is two
  steps now. That is the price of per-widget binding, and no version of it avoids
  that.
- `drawDial` grouped widget ids by pixel size and sent one bitmap per group. It
  now groups by bound Event and size together. Two copies showing the same Event
  at the same size still share one bitmap and one update.
- `WidgetTest` asserts that `widget_info.xml` carries no `android:configure`.
  That assertion inverts.
- Placing a widget before there is any Event is a dead end: the picker has
  nothing to offer and says so, and backing out places nothing. Letting the
  picker create an Event was rejected as a second way into the editor for a case
  that happens once.
- Re-pointing a widget at a different Event after placement is not offered.
  Android's reconfigurable widgets are 12 and up while `minSdk` is 26, so it
  would be a feature that half the supported range could not have. Removing the
  widget and placing another does the same thing.
- One Day Rollover alarm still serves every widget. All Events roll over at the
  same local midnight.
