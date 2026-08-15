package ninja.notnot.countdown

import kotlin.math.max
import kotlin.math.min

/**
 * How big the widget's Dial is drawn, for whatever size the owner has dragged it
 * to.
 *
 * ## What actually bounds the bitmap
 *
 * Ticket #5 sized the Dial against the one-mebibyte Binder transaction buffer
 * and capped it at 320px on that reasoning. That was the wrong yardstick. A
 * bitmap over 16KB is not copied into the transaction at all: it is written to
 * anonymous shared memory and the parcel carries a file descriptor, so the
 * buffer sees a handful of bytes however big the Dial is.
 *
 * The limit that does apply is the framework's own. `AppWidgetService` works out
 * a ceiling of one and a half screens' worth of bitmap per `RemoteViews` —
 * `6 * width * height` bytes, being 1.5 × 4 bytes a pixel — and refuses an
 * update over it. On a 1080 × 2400 phone that is 15,552,000 bytes, fifteen times
 * the transaction buffer, which is the clearest sign that the buffer was never
 * the constraint.
 *
 * A square Dial can never breach that ceiling. The widget cannot be bigger than
 * the screen, so the Dial's side is at most the screen's short side, and
 * `4 * min(w, h)² <= 4 * w * h`, which is two thirds of `6 * w * h`. A third of
 * the allowance is spare at the very largest size a launcher can offer.
 *
 * So the cap below is not a safety limit — it is a judgement about how many
 * pixels are worth drawing. See [MAX_DIAL_SIZE_PX].
 *
 * Nothing in this file touches Android, so all of it is checked on the JVM.
 */

/**
 * Screens' worth of bitmap the framework lets one `RemoteViews` carry, from
 * `AppWidgetServiceImpl.computeMaximumWidgetBitmapMemory`.
 */
const val WIDGET_BITMAP_SCREENS: Float = 1.5f

/**
 * Bytes per pixel. The Dial is `ARGB_8888`: its background is transparent, so it
 * cannot drop to a format without an alpha channel.
 */
const val DIAL_BYTES_PER_PIXEL: Int = 4

/**
 * The widget's smallest size, in dp — two cells on the usual launcher grid,
 * which asks for 70dp a cell less 30dp of margin. It is the default size too.
 * `res/xml/widget_info.xml` declares the same number to the launcher, and a test
 * holds the two together.
 */
const val MIN_WIDGET_SIZE_DP: Float = 110f

/**
 * The largest Dial worth drawing: 1080 × 1080, which is 4,665,600 bytes.
 *
 * 1080 is the short side of a 1080p phone, the screen this is built for. A
 * widget cannot be wider than the screen, so on such a phone the Dial is drawn
 * pixel for pixel at every size the launcher offers, right up to a widget
 * filling the width — 411dp at a density of 2.625 is 1078 pixels. That costs
 * 4,648,336 bytes, 30% of the phone's bitmap ceiling.
 *
 * Past that the Dial is drawn at 1080 and the `ImageView` scales it up: by up to
 * a third on a 1440p phone, by about half on a tablet's short side, and only for
 * a widget large enough to fill it. Drawing 1440 square instead would cost
 * 8,294,400 bytes a redraw for pixels that only the largest widget on the
 * densest phone would ever show, which is not a trade worth making.
 */
const val MAX_DIAL_SIZE_PX: Int = 1080

/**
 * Below this there is no Dial worth drawing. It also keeps a nonsense size — a
 * launcher reporting nothing, a density of zero — positive, which the renderer
 * requires.
 */
const val MIN_DIAL_SIZE_PX: Int = 48

/** What a square Dial [sizePx] across costs inside the `RemoteViews`. */
fun dialBitmapBytes(sizePx: Int): Long =
    sizePx.toLong() * sizePx.toLong() * DIAL_BYTES_PER_PIXEL

/**
 * The most bitmap the framework will carry to a widget on a screen
 * [screenWidthPx] by [screenHeightPx].
 */
fun widgetBitmapCeilingBytes(screenWidthPx: Int, screenHeightPx: Int): Long =
    (DIAL_BYTES_PER_PIXEL * WIDGET_BITMAP_SCREENS).toLong() *
        screenWidthPx.toLong() *
        screenHeightPx.toLong()

/**
 * The side of the largest square that fits the widget, in dp, from the four
 * numbers the launcher reports in the widget's options.
 *
 * The Dial is a circle, so it wants a square; a resized widget generally is not
 * one, and the `ImageView` centres the square in whatever box it ends up with.
 * The short side of the box is therefore the size to draw.
 *
 * There are two boxes, not one: the launcher reports the widget's width and
 * height in both orientations at once, the narrower width and the shorter height
 * belonging to different ones. Rotating the phone does not change the options,
 * so nothing redraws the Dial — it is drawn for whichever orientation gives it
 * the most room, and merely scaled down in the other.
 *
 * A launcher that reports nothing gets the declared minimum rather than a Dial
 * the size of a full stop.
 */
fun widgetSquareDp(
    minWidthDp: Int,
    maxWidthDp: Int,
    minHeightDp: Int,
    maxHeightDp: Int,
): Float {
    val portrait = min(minWidthDp, maxHeightDp)
    val landscape = min(maxWidthDp, minHeightDp)
    return max(max(portrait, landscape).toFloat(), MIN_WIDGET_SIZE_DP)
}

/**
 * The pixel size to draw the Dial at, for a widget [sizeDp] across on a screen
 * of [density] pixels to the dp.
 *
 * Both ends matter: no garbage the launcher hands over can produce a Dial too
 * small for the renderer to draw or bigger than [MAX_DIAL_SIZE_PX]. A size that
 * is not a number at all converts to zero and is raised to the floor with the
 * rest.
 */
fun dialPixelSize(sizeDp: Float, density: Float): Int =
    (sizeDp * density).toInt().coerceIn(MIN_DIAL_SIZE_PX, MAX_DIAL_SIZE_PX)

/**
 * The size to try after the launcher has refused a Dial [sizePx] across, or null
 * once there is nothing smaller left worth sending.
 *
 * The reasoning above says a refusal cannot happen. It is reasoning about
 * someone else's process, though, and the failure lands on the home screen where
 * no test can see it, so a refused update is halved and sent again rather than
 * left as a blank square.
 */
fun smallerDialSize(sizePx: Int): Int? =
    if (sizePx <= MIN_DIAL_SIZE_PX) null else (sizePx / 2).coerceAtLeast(MIN_DIAL_SIZE_PX)
