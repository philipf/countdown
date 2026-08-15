package ninja.notnot.countdown

/**
 * Where everything on the Dial goes, for one pixel size. Every proportion is a
 * fraction of that size, so the Dial looks the same however big it is drawn.
 *
 * This is the whole of the renderer's judgement, kept apart from the drawing so
 * it can be checked on the JVM. Nothing here touches Android.
 *
 * The Dial is square. When a title is drawn it takes a band across the bottom
 * and the disc shrinks to sit above it; otherwise the disc has the lot.
 *
 * @param sizePx the width and height of the bitmap.
 * @param centreX horizontal centre of the disc.
 * @param centreY vertical centre of the disc.
 * @param discRadius radius of the white disc.
 * @param arcStrokeWidth thickness of the Progress Arc.
 * @param arcLeft the left edge of the square the arc is swept inside.
 * @param arcTop the top edge of that square.
 * @param arcRight the right edge of that square.
 * @param arcBottom the bottom edge of that square.
 * @param primaryTextSize text size for the big text, before it is fitted.
 * @param primaryMaxWidth how wide the big text may be before it is shrunk.
 * @param primaryCentreY where the big text is centred vertically.
 * @param labelTextSize text size for the label under the number.
 * @param labelMaxWidth how wide the label may be before it is shrunk.
 * @param labelCentreY where the label is centred vertically.
 * @param drawTitle whether the title is worth drawing at this size.
 * @param titleTextSize text size for the title.
 * @param titleMaxWidth how wide the title may be before it is truncated.
 * @param titleCentreY where the title is centred vertically.
 */
data class DialLayout(
    val sizePx: Int,
    val centreX: Float,
    val centreY: Float,
    val discRadius: Float,
    val arcStrokeWidth: Float,
    val arcLeft: Float,
    val arcTop: Float,
    val arcRight: Float,
    val arcBottom: Float,
    val primaryTextSize: Float,
    val primaryMaxWidth: Float,
    val primaryCentreY: Float,
    val labelTextSize: Float,
    val labelMaxWidth: Float,
    val labelCentreY: Float,
    val drawTitle: Boolean,
    val titleTextSize: Float,
    val titleMaxWidth: Float,
    val titleCentreY: Float,
)

/** The Progress Arc starts at twelve o'clock and sweeps clockwise. */
const val ARC_START_ANGLE: Float = -90f

/**
 * Lays the Dial out at [sizePx] pixels square.
 *
 * @param hasLabel whether there is a label under the number. Without one the
 *   number is centred in the disc rather than sitting above the label.
 * @param hasTitle whether the Event has a title to draw.
 */
fun dialLayout(sizePx: Int, hasLabel: Boolean, hasTitle: Boolean): DialLayout {
    require(sizePx > 0) { "the Dial needs a positive size, not $sizePx" }
    val size = sizePx.toFloat()

    val titleTextSize = TITLE_TEXT_SIZE * size
    // Below a certain size the title is specks rather than words, so it is
    // dropped and the disc takes the space back.
    val drawTitle = hasTitle && titleTextSize >= MIN_LEGIBLE_TITLE_PX
    val titleBand = if (drawTitle) TITLE_BAND * size else 0f

    val discDiameter = size - titleBand
    val discRadius = discDiameter / 2
    val centreX = size / 2
    val centreY = discRadius

    // The arc is stroked on a circle half a stroke inside the rim, so it sits on
    // the edge of the disc without spilling out of the bitmap.
    val arcStrokeWidth = ARC_STROKE * discDiameter
    val arcRadius = discRadius - arcStrokeWidth / 2

    return DialLayout(
        sizePx = sizePx,
        centreX = centreX,
        centreY = centreY,
        discRadius = discRadius,
        arcStrokeWidth = arcStrokeWidth,
        arcLeft = centreX - arcRadius,
        arcTop = centreY - arcRadius,
        arcRight = centreX + arcRadius,
        arcBottom = centreY + arcRadius,
        primaryTextSize = (if (hasLabel) PRIMARY_TEXT_SIZE else PRIMARY_TEXT_SIZE_ALONE) * discDiameter,
        primaryMaxWidth = PRIMARY_MAX_WIDTH * discDiameter,
        primaryCentreY = centreY - (if (hasLabel) PRIMARY_RISE * discDiameter else 0f),
        labelTextSize = LABEL_TEXT_SIZE * discDiameter,
        labelMaxWidth = LABEL_MAX_WIDTH * discDiameter,
        labelCentreY = centreY + LABEL_DROP * discDiameter,
        drawTitle = drawTitle,
        titleTextSize = titleTextSize,
        titleMaxWidth = TITLE_MAX_WIDTH * size,
        titleCentreY = discDiameter + titleBand / 2,
    )
}

/**
 * How far round the Progress Arc sweeps, in degrees. Out-of-range and unusable
 * fractions read as empty or full rather than as a wild arc.
 */
fun sweepAngle(arcFraction: Float): Float =
    if (arcFraction.isNaN()) 0f else arcFraction.coerceIn(0f, 1f) * 360f

/**
 * [textSize] shrunk far enough that text measuring [measuredWidth] at that size
 * fits within [maxWidth]. Text that already fits is left alone, so the Dial's
 * proportions only give way when a long number needs the room.
 */
fun fittedTextSize(textSize: Float, measuredWidth: Float, maxWidth: Float): Float =
    if (measuredWidth <= maxWidth || measuredWidth <= 0f) textSize
    else textSize * maxWidth / measuredWidth

/** The fixed colours of the Dial. Only the Progress Arc follows the Accent. */
object DialColours {
    /** The disc. Opaque, so the Dial reads on any wallpaper. */
    const val DISC: Int = 0xFFFFFFFF.toInt()

    /** The big number. Near-black rather than black, which reads as softer. */
    const val PRIMARY_TEXT: Int = 0xFF212121.toInt()

    /** The label under the number, quieter than the number itself. */
    const val LABEL_TEXT: Int = 0xFF616161.toInt()

    /** The title, which sits on the wallpaper rather than on the disc. */
    const val TITLE_TEXT: Int = 0xFFFFFFFF.toInt()

    /** A shadow under the title, so it survives a pale wallpaper. */
    const val TITLE_SHADOW: Int = 0xB3000000.toInt()
}

/**
 * The unfilled part of the Progress Arc: the Accent, faded far enough to read as
 * the space the arc has yet to fill.
 *
 * The hue is the Accent's own and only the alpha is the app's, so a colour that
 * was never in the palette gets a track the same way a named one does.
 */
fun trackColour(accent: Accent): Int = (accent.argb and 0x00FFFFFF) or (TRACK_ALPHA shl 24)

// Proportions. Those named against the disc are fractions of its diameter, the
// rest are fractions of the bitmap.
private const val ARC_STROKE = 0.075f
private const val PRIMARY_TEXT_SIZE = 0.34f
private const val PRIMARY_TEXT_SIZE_ALONE = 0.30f
private const val PRIMARY_MAX_WIDTH = 0.68f
private const val PRIMARY_RISE = 0.06f
private const val LABEL_TEXT_SIZE = 0.13f
private const val LABEL_MAX_WIDTH = 0.62f
private const val LABEL_DROP = 0.16f
private const val TITLE_BAND = 0.18f
private const val TITLE_TEXT_SIZE = 0.105f
private const val TITLE_MAX_WIDTH = 0.94f

/** Under this the title is unreadable, so it is dropped instead. */
private const val MIN_LEGIBLE_TITLE_PX = 12f

private const val TRACK_ALPHA = 0x24
