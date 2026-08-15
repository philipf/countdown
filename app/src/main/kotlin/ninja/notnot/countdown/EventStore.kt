package ninja.notnot.countdown

import android.content.Context
import android.content.SharedPreferences

/**
 * The Events on disk. What goes in and what comes out is decided by
 * [storedEventsFrom], [withEvent] and the functions around them; this is only
 * the reads and writes, and it decides nothing.
 *
 * Reads are synchronous, because the widget's redraw runs in a broadcast
 * receiver where blocking is simpler and safer than a coroutine. The whole file
 * is read at once, as it always was: an Event is a handful of short strings and
 * there cannot be many of them, so reading all of them to draw one is cheaper
 * than being clever about it.
 *
 * Writes are committed rather than applied: there is no Save button, so a change
 * has to be on disk by the time the owner leaves, and they may leave by
 * force-stopping the app.
 */
class EventStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * Every Event, by id. The first read after an upgrade from v1 finds its one
     * Event under the old flat keys and stores it the new way before handing it
     * back, so nothing after that read ever sees the old shape.
     */
    fun read(): Map<EventId, StoredEvent> = storedEventsFrom(carriedOver(values()))

    /** Writes the Event with [id], listing the id if this is the first time. */
    fun write(id: EventId, stored: StoredEvent) {
        val before = values()
        save(before, before.withEvent(id, stored))
        // Drawn here rather than at the call site: every write goes through this
        // one method, so a future writer cannot leave the home screen showing an
        // Event the owner has already changed. The same call sets the Day
        // Rollover alarm, so saving a change also puts back an alarm that is
        // missing or aimed at the wrong instant.
        drawDialForToday(appContext)
    }

    /**
     * Takes the Event with [id] away, along with the keys it was kept under.
     * There is no undo and nothing is backed up, so the asking is done before
     * this is called.
     */
    fun delete(id: EventId) {
        val before = values()
        save(before, before.withoutEvent(id))
        // The home screen may be showing the Event that has just gone, so a
        // delete redraws for the same reason a write does.
        drawDialForToday(appContext)
    }

    /** An id no Event has. */
    fun newEventId(): EventId = newEventId(taken = eventIdsFrom(values()).toSet())

    /**
     * The whole file. Anything in it that is not a string was not written here,
     * so it is passed over rather than read as an empty field.
     */
    private fun values(): Map<String, String?> =
        preferences.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    private fun carriedOver(values: Map<String, String?>): Map<String, String?> {
        val carried = v1EventCarriedOver(values, newEventId(eventIdsFrom(values).toSet()))
            ?: return values
        save(values, carried)
        return carried
    }

    /** Writes [after], taking away the keys [before] had that it no longer wants. */
    private fun save(before: Map<String, String?>, after: Map<String, String?>) {
        val editor = preferences.edit()
        for (key in before.keys + after.keys) {
            val value = after[key]
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        editor.commit()
    }

    private companion object {
        const val NAME = "event"
    }
}
