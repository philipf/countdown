# Use an inexact alarm for Day Rollover

Days Remaining changes at local midnight, so the widget needs redrawing then.

`updatePeriodMillis` was rejected: its floor is 30 minutes, so the widget can
show yesterday's number for half an hour, and it wakes the device 48 times a day
to redraw a number that changes once.

An exact alarm was rejected because nobody looks at the widget at 00:00. Doze can
delay an inexact alarm by minutes, which does not matter here, and avoiding
exact alarms avoids the permission.

So: `setAndAllowWhileIdle` for the next local midnight, rescheduling itself each
time it fires. Alarms are lost on reboot and point at the wrong instant after a
timezone change, so `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`
receivers re-arm it.
