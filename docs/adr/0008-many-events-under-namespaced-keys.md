# Store many Events under namespaced keys

v1 stored the one Event as four flat strings — `eventDate`, `anchorDate`,
`title`, `accent` — in a `SharedPreferences` file called `event`. There are now
many Events, so each one needs identity and the set of them needs writing down.

Room was rejected. It is a large dependency for a handful of dates, and the
widget's redraw reads on the broadcast receiver's thread, which Room permits only
with `allowMainThreadQueries` — the thing it exists to prevent. That read has to
stay synchronous, so storage has to be something that can be read that way
without apology.

A single JSON string was rejected. The project has no serialisation dependency,
and adding one to hold four fields per Event is out of proportion; hand-rolling a
parser is worse. It would also throw away `storedEventFrom` and `toValues`, which
take and return `Map<String, String?>` and are the tested part of storage.

So: the same four keys per Event, each prefixed `event.<id>.`, in the same file.
One further key, `events`, holds the ids that exist. A read is: read `events`,
then read each id's four keys through the functions that are already there, with
the prefix stripped. A write is the same in reverse. `storedEventFrom` and
`toValues` do not change, and neither do their tests.

`events` is what says an Event exists, not the presence of its keys. A write that
was interrupted leaves keys behind with no id listed, and those read as nothing
rather than as half an Event.

Ids are random and never reused. A counter would be shorter, but a widget holds
the id of the Event it shows (ADR-0009), and an id handed out twice would point
an existing widget at a new Event without anything looking wrong. Randomness
costs a few bytes per key and removes that.

The list is ordered by Event Date, soonest first, with an Event that has no date
yet at the top, and ties broken by title and then by id. None of that is stored,
so there is no stored order to keep correct and no reordering to build.

v1's unprefixed keys are read once, on upgrade: if `eventDate` is there, it
becomes an Event with a fresh id and the old keys are removed. That is what keeps
story 51 true across the version that introduces this. It is a read of a shape
that is never written again.

## Consequences

- Deleting an Event is removing its id from `events` and removing its four keys.
  Nothing else refers to it except a widget binding, which ADR-0009 deals with.
- Five keys per Event is fine for tens of Events and would not be for thousands.
  Nothing in the app can make thousands.
- Every read loads the whole file, as it already did. A redraw reads all the
  Events to draw the ones it needs. At this size that is cheaper than being
  clever about it.
- An id appears in key names, so it has to survive being a preference key: it is
  generated from a fixed alphabet rather than being anything the owner types.
