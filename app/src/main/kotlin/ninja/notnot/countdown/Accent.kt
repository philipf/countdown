package ninja.notnot.countdown

/**
 * The colour the owner picks for the Progress Arc. Four fixed choices, each one
 * legible on the Dial's white disc — which is why white is not among them.
 */
enum class Accent(val argb: Int) {
    BLUE(0xFF0288D1.toInt()),
    BLACK(0xFF000000.toInt()),
    MID_GREY(0xFF757575.toInt()),
    RED(0xFFD32F2F.toInt()),
    ;

    companion object {
        /** What an Event has until the owner picks something else. */
        val DEFAULT = BLUE
    }
}
