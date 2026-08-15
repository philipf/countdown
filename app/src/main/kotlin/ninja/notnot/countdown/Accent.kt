package ninja.notnot.countdown

/**
 * The colour the owner picks for the Progress Arc. Seven fixed choices, each one
 * legible on the Dial's white disc — which is why white is not among them, and
 * why yellow is a dark gold: the bright yellow a palette usually has would be a
 * pale arc on a pale disc.
 *
 * Each carries the name it is offered under, because a circle of colour says
 * nothing out loud, and a colour added without one would be announced as
 * nothing at all.
 *
 * @param argb the colour itself.
 * @param label what it is called.
 */
enum class Accent(val argb: Int, val label: String) {
    BLUE(0xFF0288D1.toInt(), "Blue"),
    BLACK(0xFF000000.toInt(), "Black"),
    MID_GREY(0xFF757575.toInt(), "Mid grey"),
    RED(0xFFD32F2F.toInt(), "Red"),
    GREEN(0xFF2E7D32.toInt(), "Green"),
    PINK(0xFFE91E63.toInt(), "Pink"),
    YELLOW(0xFF8A7500.toInt(), "Yellow"),
    ;

    companion object {
        /** What an Event has until the owner picks something else. */
        val DEFAULT = BLUE
    }
}

/** How wide one Accent is drawn in the editor, in dp. */
const val ACCENT_CIRCLE_DP: Int = 56

/** The gap between two of them, across and down, in dp. */
const val ACCENT_GAP_DP: Int = 16

/**
 * The Accents in the lines the editor offers them in: as many across as [widthDp]
 * holds, and the rest on the lines below.
 *
 * Seven of them do not fit across a narrow phone. A single line that overflowed
 * would put the last colours past the edge of the screen, where they can be seen
 * by nobody and tapped by nobody, so the palette wraps instead.
 */
fun accentsInLines(widthDp: Int): List<List<Accent>> =
    Accent.entries.chunked(accentsAcross(widthDp))

/**
 * How many Accents fit across [widthDp]: one, and then as many more as a gap and
 * a circle will go into what is left. Never fewer than one, however narrow the
 * screen, since a line of none would offer no colours at all.
 */
fun accentsAcross(widthDp: Int): Int =
    if (widthDp < ACCENT_CIRCLE_DP) 1
    else 1 + (widthDp - ACCENT_CIRCLE_DP) / (ACCENT_CIRCLE_DP + ACCENT_GAP_DP)
