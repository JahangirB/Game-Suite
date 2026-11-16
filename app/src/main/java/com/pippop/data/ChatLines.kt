package com.pippop.data

/**
 * The lines a player writes for one-tap talk: six, each at most [MAX] characters. A line travels
 * like a reaction under the key [KEY] with its words in a text field; the three servers (Apps
 * Script, Cloudflare, and the phone that hosts a nearby room) clean it by the same rule as [clean].
 * Today's app drops a reaction key it does not know, so it never shows a line.
 */
object ChatLines {
    /** The reaction key a line travels under. */
    const val KEY = "line"

    /** The server cap that says lines are understood; the new app sends text only to a server that has it. */
    const val CAP = "lines"

    /** How many characters (code points) a line may have. */
    const val MAX = 24

    /** How many lines a player keeps. */
    const val COUNT = 6

    val DEFAULTS = listOf("Nice one", "Hurry up", "Lucky", "Good game", "One more?", "Your turn")

    /** The most of a text that is looked at, in UTF-16 units, so a huge one costs nothing. */
    private const val LOOKED_AT = 256

    /**
     * A control character or an invisible format character: the soft hyphen, the Arabic letter mark, the
     * zero-width space, direction marks and overrides, line and paragraph separators, the word joiner and
     * invisible operators, direction isolates and the byte-order mark. The zero-width joiner stays (emoji need it).
     */
    private fun hidden(c: Char): Boolean = Character.getType(c) == Character.CONTROL.toInt() || when (c.code) {
        0x00AD, 0x061C, 0x200B, 0x200E, 0x200F, 0x2028, 0x2029, 0xFEFF -> true
        in 0x202A..0x202E, in 0x2060..0x2064, in 0x2066..0x2069 -> true
        else -> false
    }

    private fun cut(s: String): String = if (s.codePointCount(0, s.length) > MAX) s.substring(0, s.offsetByCodePoints(0, MAX)) else s

    /** What a field keeps while it is typed in: nothing hidden, at most [MAX] code points. Not trimmed, so a space between words survives. */
    fun limit(raw: String): String = cut(raw.take(LOOKED_AT).filterNot(::hidden))

    /** How many characters a line counts as (an emoji is one). */
    fun length(s: String): Int = s.codePointCount(0, s.length)

    /**
     * [raw] with hidden characters removed (see [hidden]), trimmed, and cut to [MAX] code points (an emoji is
     * never split). Empty when nothing is left. Only the first 256 UTF-16 units are looked at.
     */
    fun clean(raw: String?): String =
        cut((raw ?: "").take(LOOKED_AT).filterNot(::hidden).trim { it.isWhitespace() }).trimEnd { it.isWhitespace() }

    /** Six lines from [stored]: each cleaned, and an empty or missing one is its default. */
    fun resolve(stored: List<String>?): List<String> =
        List(COUNT) { i -> clean(stored?.getOrNull(i)).ifEmpty { DEFAULTS[i] } }

    /** How lines are kept in one stored value; a cleaned line has no line break, so one is the separator. */
    fun encode(lines: List<String>): String = resolve(lines).joinToString("\n")

    fun decode(stored: String?): List<String> = resolve(stored?.split("\n"))
}
