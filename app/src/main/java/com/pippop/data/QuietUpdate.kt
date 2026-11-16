package com.pippop.data

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/*
 * "Update by itself": when the whole app goes to the background, a background job looks for a newer
 * build, checks it and asks Android to install it while the app is closed. Everything that decides
 * things lives here with no Android in it, so it can be tested; the parts that touch Android (the
 * network, the package manager, the installer, the job) sit behind the small interfaces below and are
 * in QuietAndroid.kt.
 */

/** What one try ended with. */
enum class QuietOutcome { UP_TO_DATE, DOWNLOADED, INSTALLED, NEEDED_TAP, FAILED, SKIPPED }

/** One line of the log: when, what happened, and for which build. [detail] is a short reason, in plain words. */
data class QuietEntry(
    val at: Long,
    val outcome: QuietOutcome,
    val versionCode: Int = 0,
    val versionName: String = "",
    val detail: String = "",
)

/** An install that was handed to Android and not yet seen finishing (the app is replaced while it runs). */
data class QuietPending(val versionCode: Int, val versionName: String, val at: Long)

/** The build that is running now. */
data class RunningApp(val versionCode: Int, val versionName: String)

/** Where the setting and the log live. The real one is QuietStore (preferences). Newest entry first. */
interface QuietState {
    var enabled: Boolean
    val entries: List<QuietEntry>
    /** Adds a line at the top and keeps the last [QUIET_LOG_SIZE]; a repeat of the top line only moves its time. */
    fun add(entry: QuietEntry)
    var pending: QuietPending?
    /** The versionCode this phone ran the last time the app was opened. */
    var lastSeenVersionCode: Int
    /** Which attempt the hint was already shown for, so each is said once. */
    var hintedKey: String?
    /** Whether a game was going (a live room) when the app last left the front. */
    var gameGoing: Boolean
}

const val QUIET_LOG_SIZE = 5

/** The log with [entry] added the way every [QuietState] keeps it. */
fun List<QuietEntry>.withEntry(entry: QuietEntry): List<QuietEntry> {
    val top = firstOrNull()
    val rest = if (top != null && top.outcome == entry.outcome && top.versionCode == entry.versionCode && top.detail == entry.detail) drop(1) else this
    return (listOf(entry) + rest).take(QUIET_LOG_SIZE)
}

/** Where the newest build is, and a way to fetch it. The real one is [Updater]. */
interface QuietSource {
    suspend fun latest(): UpdateInfo
    /** Downloads the APK into [dir] (private to the app) over https from the configured release only. */
    suspend fun download(dir: File, cancelled: () -> Boolean): File
}

/** Looks at a downloaded file. Returns why it must not be installed, or null when it is fine. */
interface QuietVerifier {
    fun problem(apk: File): String?
}

sealed interface InstallResult {
    /** Android reported the install finished. */
    data object Installed : InstallResult
    /** Android wants the user to tap; nothing was shown. */
    data class NeedsTap(val reason: String) : InstallResult
    /** The app came back to the front before the install was committed. */
    data object Aborted : InstallResult
    data class Failed(val reason: String) : InstallResult
}

/** Hands a verified APK to Android. The real one is a PackageInstaller session. */
interface QuietInstaller {
    /** False before Android 12, which always asks before replacing an app. */
    val canInstallQuietly: Boolean
    /** Whether the user allowed this app to install apps. */
    fun mayInstall(): Boolean
    /** Installs [apk]; [shouldAbort] is asked just before the commit, and [onCommit] is called just before it. */
    suspend fun install(apk: File, shouldAbort: () -> Boolean, onCommit: () -> Unit): InstallResult
}

/** What a downloaded file says it is. Signers are SHA-256 digests of the signing certificates. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Set<String>)

/** Why [candidate] must not replace [installed], or null when it may: same app, newer, same signing certificate. */
fun apkProblem(candidate: ApkFacts?, installed: ApkFacts): String? = when {
    candidate == null -> "the download couldn't be read"
    candidate.packageName != installed.packageName -> "the file is for a different app"
    candidate.versionCode <= installed.versionCode -> "the file isn't newer"
    candidate.signers.isEmpty() || candidate.signers != installed.signers -> "the file is signed differently"
    else -> null
}

/** Looks for a newer build, downloads and checks it, and installs it, only while the app is closed. */
class QuietUpdateRunner(
    private val state: QuietState,
    private val source: QuietSource,
    private val verifier: QuietVerifier,
    private val installer: QuietInstaller,
    private val running: RunningApp,
    private val isForeground: () -> Boolean,
    private val now: () -> Long,
    private val dir: File,
) {
    /** Returns what happened, or null when nothing was tried (setting off, app open, or a run already going). */
    suspend fun run(): QuietOutcome? {
        if (!state.enabled || isForeground()) return null
        // One at a time: a second job never starts a second download.
        if (!busy.compareAndSet(false, true)) return null
        var apk: File? = null
        try {
            // Whatever an earlier run left behind goes.
            dir.listFiles()?.forEach { it.deleteRecursively() }
            if (state.gameGoing) return log(QuietOutcome.SKIPPED, detail = "a game was going")
            val latest = try {
                source.latest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return log(QuietOutcome.FAILED, detail = "couldn't check for a new version")
            }
            if (latest.versionCode <= running.versionCode) return log(QuietOutcome.UP_TO_DATE)
            if (isForeground()) return log(QuietOutcome.SKIPPED, latest, "the app was open")
            // Before Android 12 nothing can be installed without a tap: leave it to the usual offer, say nothing.
            if (!installer.canInstallQuietly) return null
            if (!installer.mayInstall()) return log(QuietOutcome.NEEDED_TAP, latest, "installs aren't allowed yet")

            apk = try {
                source.download(dir) { isForeground() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return log(if (isForeground()) QuietOutcome.SKIPPED else QuietOutcome.FAILED, latest, if (isForeground()) "the app was open" else "couldn't download it")
            }
            log(QuietOutcome.DOWNLOADED, latest)

            val problem = try {
                verifier.problem(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "couldn't check the file"
            }
            if (problem != null) return log(QuietOutcome.FAILED, latest, problem)
            if (isForeground()) return log(QuietOutcome.SKIPPED, latest, "the app was open")

            val result = try {
                // The note is made only once the install is really handed over; the app is replaced while it runs.
                installer.install(apk, { isForeground() }, { state.pending = QuietPending(latest.versionCode, latest.versionName, now()) })
            } catch (e: CancellationException) {
                // Stopped after the commit may still install: the pending note stays for the next open to settle.
                throw e
            } catch (e: Exception) {
                InstallResult.Failed("the install didn't start")
            }
            state.pending = null
            return when (result) {
                InstallResult.Installed -> log(QuietOutcome.INSTALLED, latest)
                is InstallResult.NeedsTap -> log(QuietOutcome.NEEDED_TAP, latest, result.reason)
                InstallResult.Aborted -> log(QuietOutcome.SKIPPED, latest, "the app was open")
                is InstallResult.Failed -> log(QuietOutcome.FAILED, latest, result.reason)
            }
        } finally {
            apk?.delete()
            busy.set(false)
        }
    }

    private fun log(outcome: QuietOutcome, info: UpdateInfo? = null, detail: String = ""): QuietOutcome {
        state.add(QuietEntry(now(), outcome, info?.versionCode ?: 0, info?.versionName.orEmpty(), detail))
        return outcome
    }

    private companion object {
        val busy = AtomicBoolean(false)
    }
}

/** The line shown on the first open after an attempt. [good] is the tick (updated); otherwise a warning. */
data class QuietHint(val text: String, val good: Boolean)

object QuietHints {
    private val said = setOf(QuietOutcome.INSTALLED, QuietOutcome.NEEDED_TAP, QuietOutcome.FAILED)

    /**
     * Called once when the app opens. Settles an install that finished while the app was being
     * replaced, then says what the last attempt came to, once. Remembers the version it ran as.
     */
    fun take(state: QuietState, running: RunningApp): QuietHint? {
        val seen = state.lastSeenVersionCode
        state.pending?.let { p ->
            state.pending = null
            val entry = if (running.versionCode >= p.versionCode) {
                QuietEntry(p.at, QuietOutcome.INSTALLED, p.versionCode, p.versionName)
            } else {
                QuietEntry(p.at, QuietOutcome.FAILED, p.versionCode, p.versionName, "the install didn't finish")
            }
            state.add(entry)
        }
        state.lastSeenVersionCode = running.versionCode

        val last = state.entries.firstOrNull { it.outcome in said } ?: return null
        val key = "${last.outcome}:${last.versionCode}:${last.detail}"
        if (key == state.hintedKey) return null
        val hint = when (last.outcome) {
            QuietOutcome.INSTALLED ->
                if (running.versionCode > seen && last.versionCode == running.versionCode) QuietHint("Updated by itself to ${running.versionName}", good = true) else null
            else ->
                if (last.versionCode > running.versionCode) QuietHint("Couldn't update by itself: ${last.detail}", good = false) else null
        }
        if (hint != null) state.hintedKey = key
        return hint
    }

    /** The version whose update needed a tap last time, so the offer can say so; null otherwise. */
    fun neededTapFor(state: QuietState): Int? =
        state.entries.firstOrNull { it.outcome in said }?.takeIf { it.outcome == QuietOutcome.NEEDED_TAP }?.versionCode
}

/** Starts and stops the background job. The real one is WorkManager. */
interface QuietScheduler {
    fun schedule()
    fun cancel()
}

/** Reacts to the whole app going to the background or coming back. */
class QuietTrigger(private val state: QuietState, private val scheduler: QuietScheduler, private val available: Boolean) {
    // [available] is false for a build with no update address and for Android before 12.
    /** The last screen is gone: one job per close (the scheduler keeps a second from queuing). */
    fun appStopped() {
        if (available && state.enabled) scheduler.schedule()
    }

    /** Back in front: a job that has not installed yet gives up and tries again at the next close. */
    fun appStarted() = scheduler.cancel()
}

/** Thrown by a download that stopped because the app came back. */
class QuietAborted : IOException("The app was opened.")
