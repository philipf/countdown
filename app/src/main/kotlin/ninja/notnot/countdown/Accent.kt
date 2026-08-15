package ninja.notnot.countdown

/**
 * The colour the owner picks for the Progress Arc. Any colour: the editor offers
 * seven of them by name and lets the owner mix the rest, and no colour is
 * refused. See ADR-0011.
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
    override fun toString(): String = "Accent($hex)"

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
 * That is a rule about what is put in front of the owner and not about what can
 * be chosen. The mixer beside these will make white if it is asked to, and is
 * not asked to think about it. See ADR-0011.
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
    NamedAccent.entries.firstOrNull { it.accent == this }?.name ?: hex

/**
 * The colour as `#RRGGBB`, upper case. It is what an unnamed colour is stored as
 * and what the mixer puts on the screen, which are the same six digits on
 * purpose: the owner reading a colour off the mixer is reading what is written
 * down.
 */
val Accent.hex: String
    get() = HEX_MARK + (argb and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()

/** The red in the colour, 0..255. One of the three the mixer moves. */
val Accent.red: Int get() = (argb shr 16) and CHANNEL_MAX

/** The green in the colour, 0..255. */
val Accent.green: Int get() = (argb shr 8) and CHANNEL_MAX

/** The blue in the colour, 0..255. */
val Accent.blue: Int get() = argb and CHANNEL_MAX

/**
 * The same colour with its red moved to [value], which is how a colour is mixed:
 * one channel at a time, from wherever the Event's Accent already is.
 *
 * A value off either end of a slider is pulled back onto it rather than carrying
 * into the channel beside it, so nothing a slider can hand over turns red into
 * green.
 */
fun Accent.withRed(value: Int): Accent = mixedOf(channel(value), green, blue)

/** The same colour with its green moved to [value]. */
fun Accent.withGreen(value: Int): Accent = mixedOf(red, channel(value), blue)

/** The same colour with its blue moved to [value]. */
fun Accent.withBlue(value: Int): Accent = mixedOf(red, green, channel(value))

/** The most any one channel can be. Three of these make white. */
const val CHANNEL_MAX: Int = 0xFF

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

/**
 * A circle in the editor's palette. Seven of them are colours to take as they
 * are; the last is the way to mix one that is not there.
 *
 * They are one list rather than a row of colours and a button beside it, because
 * on the screen they are one row of circles and they wrap as one.
 */
sealed interface AccentChoice {

    /** One of the seven the palette offers. */
    data class Named(val named: NamedAccent) : AccentChoice

    /** The mixer: the colour the owner made, or the way to make one. */
    data object Mixed : AccentChoice
}

/** Every circle the editor offers, in the order it offers them. */
val ACCENT_CHOICES: List<AccentChoice> =
    NamedAccent.entries.map(AccentChoice::Named) + AccentChoice.Mixed

/**
 * Which circle [accent] is the colour of, which is the one the editor rings.
 *
 * A colour the palette offers is that colour's own circle however the owner
 * arrived at it — picking red and mixing the same red are one Accent (ADR-0010),
 * so they cannot be told apart and are not worth telling apart. Everything else
 * is the mixer's circle, so a colour nobody named comes back as itself rather
 * than as the named colour nearest to it.
 */
fun accentChoiceOf(accent: Accent): AccentChoice =
    NamedAccent.entries.firstOrNull { it.accent == accent }
        ?.let(AccentChoice::Named)
        ?: AccentChoice.Mixed

/**
 * What a circle is called out loud, with [inUse] the Accent the Event has. A
 * circle of colour says nothing on its own, and the mixer's says two different
 * things: the colour it is holding when the Event has a mixed one, and what it
 * is for when the Event has one of the seven.
 */
fun accentChoiceLabel(choice: AccentChoice, inUse: Accent): String = when (choice) {
    is AccentChoice.Named -> choice.named.label
    AccentChoice.Mixed ->
        if (accentChoiceOf(inUse) == AccentChoice.Mixed) "Mixed colour ${inUse.hex}" else "Mix a colour"
}

/** How wide one circle is drawn in the editor, in dp. */
const val ACCENT_CIRCLE_DP: Int = 56

/** The gap between two of them, across and down, in dp. */
const val ACCENT_GAP_DP: Int = 16

/**
 * The palette in the lines the editor offers it in: as many across as [widthDp]
 * holds, and the rest on the lines below.
 *
 * Eight circles do not fit across a narrow phone. A single line that overflowed
 * would put the last of them past the edge of the screen, where they can be seen
 * by nobody and tapped by nobody, so the palette wraps instead. The mixer is
 * last, so it is the first thing a narrow screen pushes onto the line below.
 */
fun accentChoicesInLines(widthDp: Int): List<List<AccentChoice>> =
    ACCENT_CHOICES.chunked(accentsAcross(widthDp))

/**
 * How many circles fit across [widthDp]: one, and then as many more as a gap and
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

private fun mixedOf(red: Int, green: Int, blue: Int): Accent =
    Accent.of((red shl 16) or (green shl 8) or blue)

private fun channel(value: Int): Int = value.coerceIn(0, CHANNEL_MAX)

/** What marks a stored colour as a colour and not a name. */
private const val HEX_MARK = "#"

/** The mark and six digits. Alpha is not among them, an Accent being opaque. */
private const val HEX_LENGTH = 7
