package ninja.notnot.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

/**
 * Draws [state] as a square bitmap [sizePx] pixels on a side: a white disc, the
 * number and its label inside it, the Progress Arc around the rim in the Accent,
 * and the title below. The background is left transparent so the Dial sits on
 * the wallpaper rather than on a card.
 *
 * The renderer keeps nothing between calls and holds no Android context: a
 * [DialState] and a size in, a bitmap out. Everything it decides is in
 * [dialLayout], so the widget and the config screen preview cannot disagree.
 */
fun renderDial(state: DialState, sizePx: Int): Bitmap {
    val layout = dialLayout(
        sizePx = sizePx,
        hasLabel = state.labelText != null,
        hasTitle = state.title != null,
    )
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    drawDisc(canvas, layout)
    drawProgressArc(canvas, layout, state)
    drawPrimary(canvas, layout, state.primaryText)
    state.labelText?.let { drawLabel(canvas, layout, it) }
    state.title?.takeIf { layout.drawTitle }?.let { drawTitle(canvas, layout, it) }

    return bitmap
}

private fun drawDisc(canvas: Canvas, layout: DialLayout) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DialColours.DISC }
    canvas.drawCircle(layout.centreX, layout.centreY, layout.discRadius, paint)
}

private fun drawProgressArc(canvas: Canvas, layout: DialLayout, state: DialState) {
    val bounds = RectF(layout.arcLeft, layout.arcTop, layout.arcRight, layout.arcBottom)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = layout.arcStrokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    paint.color = trackColour(state.accent)
    canvas.drawOval(bounds, paint)

    val sweep = sweepAngle(state.arcFraction)
    if (sweep <= 0f) return
    paint.color = state.accent.argb
    canvas.drawArc(bounds, ARC_START_ANGLE, sweep, false, paint)
}

private fun drawPrimary(canvas: Canvas, layout: DialLayout, text: String) {
    val paint = textPaint(DialColours.PRIMARY_TEXT, Typeface.DEFAULT_BOLD)
    paint.fitTextSize(text, layout.primaryTextSize, layout.primaryMaxWidth)
    canvas.drawTextCentred(text, layout.centreX, layout.primaryCentreY, paint)
}

private fun drawLabel(canvas: Canvas, layout: DialLayout, text: String) {
    val paint = textPaint(DialColours.LABEL_TEXT, Typeface.DEFAULT)
    paint.fitTextSize(text, layout.labelTextSize, layout.labelMaxWidth)
    canvas.drawTextCentred(text, layout.centreX, layout.labelCentreY, paint)
}

private fun drawTitle(canvas: Canvas, layout: DialLayout, title: String) {
    val paint = textPaint(DialColours.TITLE_TEXT, Typeface.DEFAULT_BOLD)
    paint.textSize = layout.titleTextSize
    // The title sits on the wallpaper, which could be any colour, so it carries
    // its own shadow.
    paint.setShadowLayer(
        layout.titleTextSize / 8f,
        0f,
        layout.titleTextSize / 16f,
        DialColours.TITLE_SHADOW,
    )
    // Shrinking the title would break the promise that the Dial looks the same
    // at any size, so a long one is cut short instead.
    val text = TextUtils.ellipsize(
        title,
        TextPaint(paint),
        layout.titleMaxWidth,
        TextUtils.TruncateAt.END,
    )
    canvas.drawTextCentred(text.toString(), layout.centreX, layout.titleCentreY, paint)
}

private fun textPaint(colour: Int, typeface: Typeface): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
    }

/** Sets [size], then shrinks it if [text] would not fit inside [maxWidth]. */
private fun Paint.fitTextSize(text: String, size: Float, maxWidth: Float) {
    textSize = size
    textSize = fittedTextSize(size, measureText(text), maxWidth)
}

/**
 * Draws [text] centred on [centreY] rather than sitting on it, because the
 * layout places blocks of text by their middles.
 */
private fun Canvas.drawTextCentred(text: String, centreX: Float, centreY: Float, paint: Paint) {
    val metrics = paint.fontMetrics
    drawText(text, centreX, centreY - (metrics.ascent + metrics.descent) / 2f, paint)
}
