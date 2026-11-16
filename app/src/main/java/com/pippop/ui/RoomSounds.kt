package com.pippop.ui

import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState

/**
 * The room sounds heard when [after] arrives where [before] was showing: someone else joined (`player_join`), someone
 * else left (`player_leave`), someone else tapped Ready or Rematch (`ready`). All three are other players' events, so
 * they play with `others = true`. Nothing plays for the first state of a room (joining one, or opening the app into
 * one), and "you" is never in [RoomState.others]. A player who only loses their connection is still in the room (the
 * server marks a real leave with `left`), so a reconnect blip makes no sound. Each sound is listed at most once.
 */
internal fun roomSounds(before: RoomState?, after: RoomState): List<Sound> {
    if (before == null || before.code != after.code) return emptyList()
    val was = before.others.here()
    val now = after.others.here()
    val sounds = LinkedHashSet<Sound>()
    if (now.keys.any { it !in was }) sounds += Sound.PLAYER_JOIN
    if (was.keys.any { it !in now }) sounds += Sound.PLAYER_LEAVE
    for ((key, player) in now) {
        val earlier = was[key] ?: continue
        if ((player.ready && !earlier.ready) || (player.wantsRestart && !earlier.wantsRestart)) sounds += Sound.READY
    }
    return sounds.toList()
}

/** The players still in the room, by who they are: their phone's id where the server gives one, else their name. */
private fun List<PlayerInfo>.here(): Map<String, PlayerInfo> = filter { !it.left }.associateBy { it.deviceId ?: it.name }
