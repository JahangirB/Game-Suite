package com.pippop.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pippop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A published build, as described by version.json next to the APK in the latest release. */
data class UpdateInfo(val versionCode: Int, val versionName: String) {
    val isNewer get() = versionCode > BuildConfig.VERSION_CODE
}

/**
 * Checks GitHub for a newer release, downloads it and hands it to the Android installer.
 * Android only accepts the update if it is signed with the same key as the installed app.
 */
class Updater(context: Context) : QuietSource {
    private val context = context.applicationContext
    private val dir get() = File(context.cacheDir, "updates")

    override suspend fun latest(): UpdateInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(open("$BASE/version.json").inputStream.bufferedReader().use { it.readText() })
        UpdateInfo(json.getInt("versionCode"), json.optString("versionName"))
    }

    /** Downloads the latest APK, reporting progress from 0 to 1 when the size is known. */
    suspend fun download(onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        copyApk(dir, onProgress) { false }
    }

    /**
     * The quiet update's download: https only, from the configured release only, into [dir] inside the
     * app's own storage. Stops early (and leaves nothing behind) when [cancelled] says the app is open.
     */
    override suspend fun download(dir: File, cancelled: () -> Boolean): File = withContext(Dispatchers.IO) {
        check(BASE.startsWith("https://")) { "The update address is not https." }
        copyApk(dir, {}, cancelled)
    }

    private fun copyApk(dir: File, onProgress: (Float) -> Unit, cancelled: () -> Boolean): File {
        dir.mkdirs()
        val part = File(dir, "PipPop.apk.part")
        val apk = File(dir, "PipPop.apk")
        val conn = open("$BASE/PipPop.apk")
        try {
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    copyCapped(input, output, conn.contentLengthLong, MAX_APK_BYTES, onProgress, cancelled)
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        apk.delete()
        check(part.renameTo(apk)) { "Could not save the update." }
        return apk
    }

    /** Whether the user has allowed this app to install updates. */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install updates. */
    fun permissionIntent() = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Shows the Android "update this app?" screen for the downloaded APK. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun start(intent: Intent) = context.startActivity(intent)

    private fun open(url: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            // GitHub redirects release downloads to its file servers.
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        val status = conn.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("Update server answered HTTP $status")
        }
        // After any redirects the answer must still come from GitHub over https.
        if (!allowedUpdateUrl(conn.url)) {
            conn.disconnect()
            throw IOException("Update came from an address that isn't allowed")
        }
        return conn
    }

    private companion object {
        val BASE: String = BuildConfig.UPDATE_BASE_URL
    }
}

/** The biggest update the app will download: far above a real build, far below filling the phone. */
const val MAX_APK_BYTES = 100L shl 20

/** Hosts a release download may come from, directly or after GitHub's redirect. */
val UPDATE_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

/** Whether [url] is https on one of [UPDATE_HOSTS]. */
fun allowedUpdateUrl(url: URL): Boolean = url.protocol == "https" && url.host in UPDATE_HOSTS

/**
 * Copies [input] to [output], stopping with an error when the announced [total] or the bytes really
 * read go over [max], and with [QuietAborted] when [cancelled] says so.
 */
fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream, total: Long, max: Long, onProgress: (Float) -> Unit, cancelled: () -> Boolean) {
    if (total > max) throw IOException("The update is too big.")
    val buffer = ByteArray(64 * 1024)
    var done = 0L
    while (true) {
        if (cancelled()) throw QuietAborted()
        val read = input.read(buffer)
        if (read < 0) break
        done += read
        if (done > max) throw IOException("The update is too big.")
        output.write(buffer, 0, read)
        if (total > 0) onProgress(done.toFloat() / total)
    }
}
