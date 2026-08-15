package ninja.notnot.countdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * The home screen widget.
 *
 * Every copy shows the same Event — whichever one the list puts first — so a
 * copy has nothing of its own to configure: there is no configuration activity,
 * no per-instance state, and every copy of a given size is sent the same Dial.
 * Which Event that is, is arbitrary, and it stands only until a copy is bound to
 * an Event when it is placed (ADR-0009).
 *
 * The layout is a single `ImageView` holding the bitmap from [renderDial], as
 * ADR-0002 has it, so the widget and the editor's preview cannot disagree.
 * A bitmap cannot be resized after the fact, so the Dial is drawn again at the
 * new pixel size whenever the widget is dragged to a new one. Everything this
 * file decides beyond that is in [widgetSquareDp] and [dialPixelSize].
 */
class CountdownWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Every copy shows the same Event, so this redraws all of them rather
        // than only the ones named: it is the same work, and it is the call that
        // also sets the Day Rollover alarm. Dropping the first copy on the home
        // screen is what starts the days counting.
        drawDialForToday(context)
    }

    /**
     * The widget has been resized. Only the copy that moved needs redrawing, and
     * it needs a new bitmap rather than the old one stretched.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        drawDial(context, appWidgetManager, intArrayOf(appWidgetId))
    }
}

/**
 * Draws today's Dial on every copy of the widget and sets the alarm for the next
 * Day Rollover.
 *
 * This is the only way in. Redrawing and re-arming are one call because half of
 * it is a bug either way: a redraw with no alarm freezes the number until
 * something else happens along, and an alarm with no redraw leaves yesterday's
 * number where it is. [EventStore] calls this after every write and
 * [DayRolloverReceiver] calls it for every broadcast it hears, and neither has
 * to remember the other half.
 */
fun drawDialForToday(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
    // Before the redraw rather than after: a redraw that throws is one wrong
    // Dial, but an alarm that was never set is every day after it.
    //
    // With no widget on the home screen there is nothing to redraw, so there is
    // nothing to wake the phone for. Removing the last copy does not call this,
    // so an alarm outlives it by up to a day and then cancels itself when it
    // fires and finds nothing here.
    if (ids.isEmpty()) cancelDayRollover(context) else armDayRollover(context)
    drawDial(context, manager, ids)
}

private fun drawDial(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    if (appWidgetIds.isEmpty()) return

    // Read on the calling thread. This runs in a broadcast receiver, where
    // blocking is simpler and safer than a coroutine.
    //
    // Whichever Event the list puts first, which is the one the owner is nearest
    // to. Temporary: a widget shows the Event it was bound to when it was placed
    // (ADR-0009), and until that binding exists there is nothing here to choose
    // with. No Event at all draws the same Dial as a first run.
    val stored = eventsInOrder(EventStore(context).read()).firstOrNull()?.stored
    val state = dialState(stored?.toEvent(), LocalDate.now())
    val density = context.resources.displayMetrics.density

    // Every copy shows the same Event, so copies of the same size still share
    // one bitmap and one update. Only a copy dragged to a different size needs
    // one of its own.
    for ((sizePx, ids) in appWidgetIds.groupBy { dialSizeOf(manager, it, density) }) {
        send(context, manager, ids.toIntArray(), state, sizePx)
    }
}

/** How big to draw the Dial for one copy of the widget, at its current size. */
private fun dialSizeOf(manager: AppWidgetManager, appWidgetId: Int, density: Float): Int {
    val options = manager.getAppWidgetOptions(appWidgetId) ?: Bundle.EMPTY
    val sizeDp = widgetSquareDp(
        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
    )
    return dialPixelSize(sizeDp, density)
}

/**
 * Sends the Dial, and on a refusal sends a smaller one rather than leaving the
 * home screen blank. See [smallerDialSize] for why that should never happen and
 * why it is caught anyway. The last refusal is thrown: by then the Dial is as
 * small as it goes and the fault is something else.
 */
private fun send(
    context: Context,
    manager: AppWidgetManager,
    appWidgetIds: IntArray,
    state: DialState,
    wantedPx: Int,
) {
    var sizePx = wantedPx
    while (true) {
        try {
            manager.updateAppWidget(appWidgetIds, dialViews(context, state, sizePx))
            return
        } catch (refused: RuntimeException) {
            sizePx = smallerDialSize(sizePx) ?: throw refused
        }
    }
}

private fun dialViews(context: Context, state: DialState, sizePx: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_dial).apply {
        setImageViewBitmap(R.id.dial, renderDial(state, sizePx))
        // The Dial is a bitmap, so it has to say out loud what it shows.
        setContentDescription(R.id.dial, spokenAs(state))
        // The empty widget opens the app too: it says "Set a date", and this is
        // how the owner sets one.
        setOnClickPendingIntent(R.id.dial, openTheApp(context))
    }

private fun openTheApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
