package com.pippop.data.nearby

import com.pippop.data.Avatar
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Identifies the app's games among everything else using Nearby Connections. It keeps the
 * app's first name on purpose: phones only find each other with the same id, and a phone one
 * version behind should still find the room (and be told to update) rather than see nothing.
 */
internal const val SERVICE_ID = "com.arcade.sudoku.battle"

/** Phones connected directly over Bluetooth / Wi-Fi without internet. */
// A star: the host's phone in the middle, up to three guests connected to it.
internal val STRATEGY: Strategy = Strategy.P2P_STAR

/**
 * A phone hosting a nearby battle, as seen by a player looking for one. The room's code is
 * never broadcast: [tag] is random and only tells this room apart when reconnecting.
 */
data class FoundHost(val endpointId: String, val tag: String, val name: String, val avatar: Avatar?)

/**
 * The host advertises "tag|name|avatar". Only someone told the room's code can join: the guest
 * sends it when opening the link, and the host turns away a wrong one.
 */
internal fun advertisedName(tag: String, name: String, avatar: Avatar?) = "$tag|$name|${avatar?.key.orEmpty()}"

/** A room code as typed: case and anything but letters and digits don't matter. */
internal fun sameCode(a: String, b: String) = clean(a) == clean(b)

private fun clean(code: String) = code.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

/** The host's answer to a wrong room code. */
internal const val WRONG_CODE = "wrong_code"

internal fun parseAdvertisedName(endpointId: String, text: String): FoundHost? {
    val parts = text.split("|")
    if (parts.size != 3 || parts[0].isBlank()) return null
    return FoundHost(endpointId, parts[0], parts[1], Avatar.from(parts[2]))
}

internal fun JSONObject.toPayload(): Payload = Payload.fromBytes(toString().toByteArray(Charsets.UTF_8))

internal fun Payload.toJson(): JSONObject? =
    asBytes()?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }

internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

internal fun errorJson(re: String?, message: String?, fatal: Boolean, reason: String? = null): JSONObject = JSONObject()
    .put("type", "error").put("re", re ?: JSONObject.NULL)
    .put("error", message ?: "Something went wrong.").put("fatal", fatal)
    .put("reason", reason ?: JSONObject.NULL)
