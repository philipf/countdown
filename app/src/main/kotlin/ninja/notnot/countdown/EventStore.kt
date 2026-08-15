package ninja.notnot.countdown

import android.content.Context
import android.content.SharedPreferences

/**
 * The Event on disk. What goes in and what comes out is decided by
 * [storedEventFrom] and [toValues]; this is only the reads and writes around
 * them.
 *
 * Reads are synchronous, because the widget's redraw runs in a broadcast
 * receiver where blocking is simpler and safer than a coroutine. Writes are
 * committed rather than applied: there is no Save button, so a change has to be
 * on disk by the time the owner leaves, and they may leave by force-stopping the
 * app. Four short strings is a cheap thing to commit.
 */
class EventStore(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
    )

    fun read(): StoredEvent =
        storedEventFrom(EventKeys.ALL.associateWith { preferences.getString(it, null) })

    fun write(stored: StoredEvent) {
        val editor = preferences.edit()
        for ((key, value) in stored.toValues()) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        editor.commit()
    }

    private companion object {
        const val NAME = "event"
    }
}
