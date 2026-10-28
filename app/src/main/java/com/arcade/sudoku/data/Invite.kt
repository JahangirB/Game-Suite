package com.arcade.sudoku.data

import kotlin.random.Random

/**
 * What one player sends another to join an online room: the room code, and the link for
 * someone who hasn't played online yet. A password is never put in the message; one read
 * from an older message is still filled in.
 */
data class Invite(val scriptUrl: String?, val code: String?, val password: String? = null) {
    fun toShareText() = buildString {
        append("Let's play in Pip & Pop! Code: $code")
        if (!scriptUrl.isNullOrBlank()) append("\nLink: $scriptUrl")
    }

    companion object {
        private val URL_REGEX = Regex("""https://\S+""")
        // "Code: K7Q2XM", and "Secret code: …" from older messages.
        private val CODE_REGEX = Regex("""(?i)\bcode\b\s*[:=]?\s*([A-Z0-9]{4,12})""")
        private val PASSWORD_REGEX = Regex("""(?i)server\s*password\s*[:=]\s*(\S+)""")

        /** Pulls the link, password and code out of a shared invite message; any may be missing. */
        fun parse(text: String): Invite {
            val url = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ';')
            val code = CODE_REGEX.find(text)?.groupValues?.get(1)?.uppercase()
            val password = PASSWORD_REGEX.find(text)?.groupValues?.get(1)
            return Invite(url, code, password)
        }

        // No 0/O or 1/I so a code read aloud is unambiguous.
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomCode(length: Int = 6) =
            (1..length).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }
}
