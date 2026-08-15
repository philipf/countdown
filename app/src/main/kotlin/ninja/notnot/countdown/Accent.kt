package ninja.notnot.countdown

/**
 * The colour the owner picks for the Progress Arc. Any colour: the editor offers
 * seven of them by name, and that is the editor's business rather than a limit
 * on what an Event can hold.
 *
 * Always opaque. The arc is drawn on the Dial's white disc and not through it,
 * so a see-through Accent would come out as a paler colour than the one it was
 * picked as. The alpha a colour arrives with is replaced instead of kept, which
 * is why [of] is the only way to make one.
 *
 * @param argb the colour itself, alpha first and always `FF`.
 */
@JvmInline
value class Accent private constructor(val argb: Int) {

    /** A shade rather than a signed integer, so a failed test names a colour. */
    override fun toString(): String = "Accent(${hexOf(this)})"

    companion object {
        /** [argb] as an Accent, opaque whatever alpha it was given. */
        fun of(argb: Int): Accent = Accent(argb or OPAQUE)

        /** What an Event has until the owner picks something else. */
        val DEFAULT: Accent get() = NamedAccent.BLUE.accent

        private const val OPAQUE = 0xFF shl 24
    }
}

/**
 * The Accents the editor offers, in the order it offers them. Seven colours,
 * each one legible on the Dial's white disc — which is why white is not among
 * them, and why yellow is a dark gold: the bright yellow a palette usually has
 * would be a pale arc on a pale disc.
 *
 * Each carries the name it is offered under, because a circle of colour says
 * nothing out loud, and a colour added without one would be announced as nothing
 * at all.
 *
 * A constant's name here is also what an Event holding that colour is stored as
 * (ADR-0010). Renaming one would lose the colour on phones that already have it;
 * changing a shade recolours those Events, which is the point of storing a name
 * rather than a number.
 *
 * @param argb the colour itself.
 * @param label what it is called.
 */
enum class NamedAccent(private val argb: Int, val label: String) {
    BLUE(0xFF0288D1.toInt(), "Blue"),
    BLACK(0xFF000000.toInt(), "Black"),
    MID_GREY(0xFF757575.toInt(), "Mid grey"),
    RED(0xFFD32F2F.toInt(), "Red"),
    GREEN(0xFF2E7D32.toInt(), "Green"),
    PINK(0xFFE91E63.toInt(), "Pink"),
    YELLOW(0xFF8A7500.toInt(), "Yellow"),
    ;

    /** The colour itself, which is what an Event holds and the Dial draws. */
    val accent: Accent get() = Accent.of(argb)
}

/**
 * How an Accent is written down: under its name when the palette offers it, and
 * as `#RRGGBB` when it is a colour nobody named. See ADR-0010.
 *
 * Which shape is used follows from the colour and nothing else, so one Accent is
 * always written the same way however the owner arrived at it.
 */
fun Accent.toStoredValue(): String =
    NamedAccent.entries.firstOrNull { it.accent == this }?.name ?: hexOf(this)

/**
 * The Accent [value] was written as. Anything else reads as the default: a name
 * the palette no longer offers, a colour that is not six hex digits, or nothing
 * stored at all. A colour that cannot be made out is not worth a crash on
 * launch — the worst of it is the owner picking again.
 */
fun accentFrom(value: String?): Accent {
    val named = NamedAccent.entries.firstOrNull { it.name == value }
    if (named != null) return named.accent
    return value?.let(::hexAccentOrNull) ?: Accent.DEFAULT
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
fun accentsInLines(widthDp: Int): List<List<NamedAccent>> =
    NamedAccent.entries.chunked(accentsAcross(widthDp))

/**
 * How many Accents fit across [widthDp]: one, and then as many more as a gap and
 * a circle will go into what is left. Never fewer than one, however narrow the
 * screen, since a line of none would offer no colours at all.
 */
fun accentsAcross(widthDp: Int): Int =
    if (widthDp < ACCENT_CIRCLE_DP) 1
    else 1 + (widthDp - ACCENT_CIRCLE_DP) / (ACCENT_CIRCLE_DP + ACCENT_GAP_DP)

/**
 * A colour written as a `#` and six hex digits, or null for anything else. The
 * `#` is what keeps the two shapes apart: a name is letters and underscores, so
 * no name can be read as a colour and no colour as a name.
 *
 * Either case is read, since a hex digit is a hex digit, but only one is written.
 */
private fun hexAccentOrNull(value: String): Accent? {
    if (value.length != HEX_LENGTH || !value.startsWith(HEX_MARK)) return null
    val digits = value.drop(HEX_MARK.length)
    if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return Accent.of(digits.toInt(16))
}

private fun hexOf(accent: Accent): String =
    HEX_MARK + (accent.argb and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()

/** What marks a stored colour as a colour and not a name. */
private const val HEX_MARK = "#"

/** The mark and six digits. Alpha is not among them, an Accent being opaque. */
private const val HEX_LENGTH = 7
