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
 * Every copy shows the Event it was bound to when it was placed (ADR-0009), so
 * two copies on two Events count to two different dates. The binding is the only
 * thing a copy has of its own: it is made in [ChooseEventActivity], which the
 * launcher opens when the copy is dropped, and it is forgotten again when the
 * copy is removed.
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
        // Redraws every copy rather than only the ones named. Each copy now
        // costs a Dial of its own, but a broadcast asking for only some of them
        // is rare, and this is the call that also sets the Day Rollover alarm,
        // so doing the lot keeps that in one place.
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

    /**
     * The copies named have been taken off the home screen, so what they were
     * showing is nobody's business any more. Android reuses `appWidgetId`s, and
     * a binding left behind would be inherited by whatever is placed next.
     *
     * Nothing is redrawn and no alarm is cancelled here: the copies that are
     * left are still showing what they were, and an alarm that has outlived the
     * last copy cancels itself when it fires and finds nothing to draw.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetBindingStore(context).unbind(appWidgetIds)
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
 *
 * [justPlaced] is the copy the chooser has this moment bound, which the launcher
 * may not be listing yet. Nothing else sends that copy its first Dial — a
 * configuration activity is expected to do that itself — so it is drawn with the
 * rest rather than waited for.
 */
fun drawDialForToday(context: Context, justPlaced: Int? = null) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    val listed = manager.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
    val ids = if (justPlaced == null || justPlaced in listed) listed else listed + justPlaced
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

    // Both read on the calling thread. This runs in a broadcast receiver, where
    // blocking is simpler and safer than a coroutine. The whole of both files is
    // read to draw whatever these copies need: at this size that is cheaper than
    // being clever about it.
    val events = EventStore(context).read()
    val boundTo = WidgetBindingStore(context).read()
    val density = context.resources.displayMetrics.density

    val placed = appWidgetIds.map {
        PlacedWidget(it, boundTo[it], dialSizeOf(manager, it, density))
    }

    // Grouped by the Dial that comes out, so two copies on the same Event at the
    // same size still share one bitmap and one update. Only a copy on a
    // different Event, or dragged to a different size, costs one of its own.
    for (dial in dialsToDraw(placed, events, LocalDate.now())) {
        send(context, manager, dial.appWidgetIds.toIntArray(), dial.state, dial.sizePx)
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
