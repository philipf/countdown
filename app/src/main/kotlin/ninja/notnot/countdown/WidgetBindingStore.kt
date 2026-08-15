package ninja.notnot.countdown

import android.content.Context
import android.content.SharedPreferences

/**
 * The Bound Events on disk: which Event each copy of the widget shows. What goes
 * in and what comes out is decided by [boundEventsFrom], [withBinding] and
 * [withoutBindings]; this is only the reads and writes, and it decides nothing.
 *
 * Its own file rather than a corner of the Events' one, because the two have
 * different lifetimes (ADR-0009): removing a widget must not touch an Event, and
 * deleting an Event must not have to know what is on the home screen.
 *
 * Reads are synchronous and writes are committed, for the same reasons
 * [EventStore]'s are: the redraw runs in a broadcast receiver where blocking is
 * simpler than a coroutine, and a binding that is not on disk by the time the
 * chooser returns leaves a widget on the home screen pointing at nothing.
 */
class WidgetBindingStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The Bound Event of every copy that has one, by `appWidgetId`. */
    fun read(): Map<Int, EventId> = boundEventsFrom(values())

    /** Binds the copy with [appWidgetId] to the Event with [eventId]. */
    fun bind(appWidgetId: Int, eventId: EventId) {
        val before = values()
        save(before, before.withBinding(appWidgetId, eventId))
    }

    /** Forgets the named copies, which are no longer on the home screen. */
    fun unbind(appWidgetIds: IntArray) {
        val before = values()
        save(before, before.withoutBindings(appWidgetIds.toList()))
    }

    /**
     * The whole file. Anything in it that is not a string was not written here,
     * so it is passed over rather than read as a binding.
     */
    private fun values(): Map<String, String?> =
        preferences.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

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
        const val NAME = "widget_events"
    }
}
