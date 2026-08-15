package ninja.notnot.countdown

/**
 * How big the widget's Dial is drawn.
 *
 * `RemoteViews` reach the launcher over a Binder transaction, and
 * `setImageViewBitmap` carries the bitmap inside it. The transaction buffer is
 * one mebibyte per process and everything else in flight shares it, so a bitmap
 * that merely fits is not safe — the rest of the transaction has to fit beside
 * it. Overrunning it throws in the launcher's process, where neither a unit test
 * nor a build can see it. So the size is decided here, capped, and checked.
 *
 * Nothing in this file touches Android, so all of it is checked on the JVM.
 */

/** The Binder transaction buffer: one mebibyte, shared per process. */
const val TRANSACTION_BUFFER_BYTES: Long = 1024L * 1024L

/**
 * The most of that buffer the Dial may take. The rest is margin for the other
 * fields of the `RemoteViews` and for whatever else is in flight at the time.
 */
const val DIAL_BUFFER_SHARE: Float = 0.4f

/**
 * Bytes per pixel. The Dial is `ARGB_8888`: its background is transparent, so it
 * cannot drop to a format without an alpha channel.
 */
const val DIAL_BYTES_PER_PIXEL: Int = 4

/**
 * The widget's fixed size, in dp — two cells on the usual launcher grid, which
 * asks for 70dp a cell less 30dp of margin. `res/xml/widget_info.xml` declares
 * the same number to the launcher, and a test holds the two together.
 *
 * Fixed for now. Redrawing at whatever size the owner drags the widget to is a
 * separate job.
 */
const val WIDGET_SIZE_DP: Float = 110f

/**
 * The largest Dial that stays inside [DIAL_BUFFER_SHARE] of the buffer:
 * 320 × 320 × 4 bytes is 409,600, which is 39% of a mebibyte.
 *
 * A 110dp widget reaches this at a screen density of about 2.9, so on a dense
 * phone the Dial is drawn a little smaller than the space it fills and the
 * `ImageView` scales it up by a few percent. That is invisible, and it is the
 * cheap half of the trade.
 */
const val MAX_DIAL_SIZE_PX: Int = 320

/**
 * Below this there is no Dial worth drawing. It also keeps a nonsense size — a
 * launcher reporting nothing, a density of zero — positive, which the renderer
 * requires.
 */
const val MIN_DIAL_SIZE_PX: Int = 48

/** What a square Dial [sizePx] across costs inside the transaction. */
fun dialBitmapBytes(sizePx: Int): Long =
    sizePx.toLong() * sizePx.toLong() * DIAL_BYTES_PER_PIXEL

/**
 * The pixel size to draw the Dial at, for a widget [sizeDp] across on a screen
 * of [density] pixels to the dp.
 *
 * The cap is the point of this function: no screen, however dense, and no
 * garbage the launcher hands over can produce a bitmap too big for the
 * transaction. A size that is not a number at all converts to zero and is
 * raised to the floor with the rest.
 */
fun dialPixelSize(sizeDp: Float, density: Float): Int =
    (sizeDp * density).toInt().coerceIn(MIN_DIAL_SIZE_PX, MAX_DIAL_SIZE_PX)
