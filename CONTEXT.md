# Countdown

A personal Android app that shows, on the home screen, how many days are left
until one date the user cares about.

## Language

**Event**:
The dated thing being counted towards. There is one, app-wide.
_Avoid_: Countdown, Reminder, Occasion, Appointment

**Event Date**:
The calendar date of the Event. No time of day. Read in the device's local
timezone.
_Avoid_: Target date, deadline, due date

**Anchor Date**:
The date progress is measured from. Set to the day the Event Date was last
chosen.
_Avoid_: Start date, created date, from date

**Days Remaining**:
Whole calendar days from today to the Event Date. Zero on the day itself,
negative after it.
_Avoid_: Days left, days to go, countdown value

**Dial**:
The circular graphic shown on the home screen and in the app: a white disc
holding Days Remaining and its label, with the Progress Arc around the edge.
_Avoid_: Widget face, gauge, ring, clock

**Progress Arc**:
The arc around the Dial. Shows how much of the Anchor Date to Event Date span
has passed. Empty on the Anchor Date, full on the Event Date.
_Avoid_: Ring, gauge, progress bar

**Accent**:
The colour the user picks for the Progress Arc. One of four fixed colours.
_Avoid_: Theme, colour scheme, swatch

**Day Rollover**:
Local midnight, when Days Remaining changes and the Dial needs redrawing.
_Avoid_: Refresh, tick, update
