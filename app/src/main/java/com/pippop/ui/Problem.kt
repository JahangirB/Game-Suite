package com.pippop.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pippop.data.ApiException
import java.io.IOException

/** The one thing that fixes a [Problem], shown as its button. */
enum class Fix(val label: String) {
    /** Bluetooth settings, to turn it on. */
    BLUETOOTH("Turn on"),
    /** The app's settings, to allow the nearby permissions Android no longer asks for. */
    PERMISSION("Allow"),
    /** Switch to Offline and connect nearby instead. */
    GO_OFFLINE("Go offline"),
    /** Back to the code, to check it. */
    EDIT_CODE("Edit code"),
}

/**
 * Something that went wrong, said the way a person would: what happened ([title]), why, and
 * the one [fix] that puts it right, if there is one. Never "Something went wrong".
 */
data class Problem(val title: String, val why: String, val fix: Fix? = null)

object Problems {
    val bluetooth = Problem("Can't search nearby", "Bluetooth is off. Nearby play needs it on.", Fix.BLUETOOTH)

    val noPermission = Problem(
        "Nearby needs permission",
        "Android blocks finding other phones until you allow it. Allow Nearby devices (and Location on older phones).",
        Fix.PERMISSION,
    )

    /** Whether Bluetooth is off on a phone that has it. */
    fun bluetoothOff(context: Context): Boolean {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return false
        return runCatching { !adapter.isEnabled }.getOrDefault(false)
    }

    private fun online(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * What a failure to connect means, for the screen: a room that isn't there, no internet,
     * a phone that didn't answer. Null for anything that has its own message already.
     */
    fun connecting(e: Exception, context: Context, nearby: Boolean, code: String, partner: String?): Problem? {
        val them = partner ?: "the other player"
        return when {
            e is ApiException && e.reason == "not_found" && nearby ->
                Problem("No room with code $code nearby", "Check the code, and that the other phone has the room open.", Fix.EDIT_CODE)
            e is ApiException && e.reason == "not_found" ->
                Problem("No game with code $code", "It may have ended, or a letter is off. Check with $them.", Fix.EDIT_CODE)
            e is IOException && nearby ->
                Problem("The other phone didn't answer", "Keep the phones close, with Bluetooth and Wi‑Fi on, and try again.")
            e is IOException && !online(context) ->
                Problem("No internet", "Online games need a connection. Offline works without one.", Fix.GO_OFFLINE)
            e is IOException ->
                Problem("Can't reach the online game", "The link may be out of date. Paste the latest message from $them, or play offline.", Fix.GO_OFFLINE)
            else -> null
        }
    }
}
