package ninja.notnot.countdown

import java.time.LocalDate

/**
 * Which Event each copy of the widget shows, and what that means the widget has
 * to draw. Values in and values out, like the Events' own storage rules:
 * reaching the disk is [WidgetBindingStore]'s job and it decides nothing.
 *
 * The Bound Event is chosen when the copy is placed and fixed for as long as it
 * is there (ADR-0009). It is kept away from the Events themselves because the
 * two have different lifetimes: removing a widget must not touch an Event, and
 * deleting an Event must not have to know what is on the home screen.
 */
object WidgetKeys {
    /** Where the Bound Event of the copy with [appWidgetId] is kept. */
    fun keyFor(appWidgetId: Int): String = "$PREFIX$appWidgetId"

    /**
     * The copy [key] belongs to, or null when the key is not a binding at all.
     * Anything else in the file is left alone rather than read as a binding.
     */
    fun appWidgetIdIn(key: String): Int? =
        key.removePrefix(PREFIX).takeIf { it != key }?.toIntOrNull()

    private const val PREFIX = "widget."
}

/**
 * The Bound Event of every copy that has one, by `appWidgetId`. A key that is
 * not a binding, and a binding with nothing behind it, read as no binding rather
 * than as an error: the worst case is a copy drawing the same Dial as a first
 * run, which is what a copy whose Event has been deleted draws anyway.
 */
fun boundEventsFrom(values: Map<String, String?>): Map<Int, EventId> =
    values.mapNotNull { (key, value) ->
        val appWidgetId = WidgetKeys.appWidgetIdIn(key) ?: return@mapNotNull null
        val eventId = value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        appWidgetId to EventId(eventId)
    }.toMap()

/**
 * The file with the copy [appWidgetId] bound to the Event [eventId]. Every other
 * copy's binding is left as it was.
 */
fun Map<String, String?>.withBinding(appWidgetId: Int, eventId: EventId): Map<String, String?> =
    this + (WidgetKeys.keyFor(appWidgetId) to eventId.value)

/**
 * The file with the named copies' bindings taken away. A null value means the
 * key is removed rather than written, as it does in [StoredEvent.toValues].
 *
 * Android reuses `appWidgetId`s, so a removed copy has to take its binding with
 * it: without that the file grows without limit and a recycled id inherits
 * whatever the last copy was showing.
 */
fun Map<String, String?>.withoutBindings(appWidgetIds: List<Int>): Map<String, String?> =
    this + appWidgetIds.associate { WidgetKeys.keyFor(it) to null }

/**
 * The file with every binding taken away whose copy is not in [stillPlaced] —
 * the copies the framework says are on the home screen.
 *
 * This is the sweep behind [withoutBindings]. Removing a copy tells the app so
 * through a broadcast, and a broadcast can be missed: the app can be
 * force-stopped, or its data cleared while the copies stay where they are. A
 * binding that outlives its copy is inherited by the next copy Android hands the
 * same `appWidgetId` to, so whatever onDeleted did not take away is taken away
 * here, on the next redraw.
 *
 * Every key that names a copy goes, whatever is behind it, so a binding left
 * half-written goes with the rest.
 */
fun Map<String, String?>.withoutBindingsBeyond(stillPlaced: Set<Int>): Map<String, String?> =
    withoutBindings(keys.mapNotNull(WidgetKeys::appWidgetIdIn).filterNot { it in stillPlaced })

/**
 * One copy of the widget on the home screen: which Event it is bound to, if any,
 * and how big a Dial it has room for.
 */
data class PlacedWidget(val appWidgetId: Int, val boundTo: EventId?, val dialSizePx: Int)

/** One Dial to draw once, and the copies of the widget it is sent to. */
data class DialToDraw(val state: DialState, val sizePx: Int, val appWidgetIds: List<Int>)

/**
 * The Event a copy of the widget shows, or null when it has none to show.
 *
 * There are two ways to have none, and they answer the same on purpose: a copy
 * that was never bound to anything, and a copy whose Bound Event has since been
 * deleted. Neither is an error and neither is worth telling apart on the home
 * screen — what the second one must not do is go on standing there with the last
 * number it was given for an Event that is gone.
 */
fun eventShownBy(copy: PlacedWidget, events: Map<EventId, StoredEvent>): Event? =
    copy.boundTo?.let(events::get)?.toEvent()

/**
 * What to draw on every copy of the widget: each copy's Bound Event, as a Dial,
 * at the size that copy has room for.
 *
 * Copies are grouped by the Dial they end up with rather than drawn one at a
 * time, so two copies showing the same Event at the same size share one bitmap
 * and one update. Only a copy bound to a different Event, or dragged to a
 * different size, costs a drawing of its own.
 *
 * A copy bound to an Event that has since been deleted, and a copy with no
 * binding at all, both draw the same Dial as a first run — "Set a date". Nothing
 * refuses a deletion or warns about it, because that would make the app know
 * what is on the home screen (ADR-0009).
 */
fun dialsToDraw(
    placed: List<PlacedWidget>,
    events: Map<EventId, StoredEvent>,
    today: LocalDate,
): List<DialToDraw> =
    placed
        .groupBy { copy -> dialState(eventShownBy(copy, events), today) to copy.dialSizePx }
        .map { (dial, copies) ->
            DialToDraw(dial.first, dial.second, copies.map { it.appWidgetId })
        }
