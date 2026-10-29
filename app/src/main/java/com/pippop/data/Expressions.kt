package com.pippop.data

/** Player pictures. Keys must match AVATARS in both servers. */
enum class Avatar(val key: String, val emoji: String) {
    FOX("fox", "🦊"), CAT("cat", "🐱"), DOG("dog", "🐶"), PANDA("panda", "🐼"),
    OWL("owl", "🦉"), FROG("frog", "🐸"), TIGER("tiger", "🐯"), UNICORN("unicorn", "🦄"),
    ROBOT("robot", "🤖"), ALIEN("alien", "👽"), LION("lion", "🦁"), PENGUIN("penguin", "🐧");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Quick reactions players can send during a battle. Keys must match EMOJIS in the server
 * (room.js); the nearby host checks them here. A [sticker] is a meme picture with its own sound,
 * shown bigger and for longer. One that [takesOverMs] owns the game for that long: its song
 * plays out, the other phones' controls freeze, and nobody can send another reaction.
 */
enum class Reaction(val key: String, val emoji: String, val sticker: Boolean = false, val takesOverMs: Long = 0) {
    KYLIAN("kylian", "🫡", sticker = true, takesOverMs = 11_000),
    THUMBS_UP("thumbsup", "👍"), CLAP("clap", "👏"), FIRE("fire", "🔥"), LAUGH("laugh", "😂"),
    WOW("wow", "😮"), SWEAT("sweat", "😅"), THINK("think", "🤔"), COOL("cool", "😎"),
    HEART("heart", "❤️"), HURRY("hurry", "⏰"),
    SIDE_EYE("sideeye", "🤨", sticker = true), STONKS("stonks", "📈", sticker = true),
    GALAXY_BRAIN("galaxybrain", "🧠", sticker = true), THIS_IS_FINE("thisisfine", "🔥", sticker = true);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}
