package ninja.notnot.countdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * The home screen widget.
 *
 * There is one Event app-wide, so a copy of the widget has nothing of its own to
 * configure: there is no configuration activity, no per-instance state, and
 * every copy is sent the same Dial.
 *
 * The layout is a single `ImageView` holding the bitmap from [renderDial], as
 * ADR-0002 has it, so the widget and the config screen preview cannot disagree.
 * Everything this file decides beyond that is in [dialPixelSize].
 */
class CountdownWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        drawDial(context, appWidgetManager, appWidgetIds)
    }
}

/**
 * Redraws every copy of the widget.
 *
 * [EventStore] calls this after every write, so a change saved in the app is on
 * the home screen at once and no future caller has to remember to ask.
 */
fun redrawWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
    drawDial(context, manager, ids)
}

private fun drawDial(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    if (appWidgetIds.isEmpty()) return

    // Read on the calling thread. This runs in a broadcast receiver, where
    // blocking is simpler and safer than a coroutine.
    val state = dialState(EventStore(context).read().toEvent(), LocalDate.now())
    val sizePx = dialPixelSize(WIDGET_SIZE_DP, context.resources.displayMetrics.density)

    val views = RemoteViews(context.packageName, R.layout.widget_dial).apply {
        setImageViewBitmap(R.id.dial, renderDial(state, sizePx))
        // The Dial is a bitmap, so it has to say out loud what it shows.
        setContentDescription(R.id.dial, spokenAs(state))
        // The empty widget opens the app too: it says "Set a date", and this is
        // how the owner sets one.
        setOnClickPendingIntent(R.id.dial, openTheApp(context))
    }

    // One `RemoteViews` for the lot, because every copy shows the same Event.
    manager.updateAppWidget(appWidgetIds, views)
}

private fun openTheApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
