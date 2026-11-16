package com.pippop.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** The deciding parts of "Update by itself", with the network, the package manager and the installer faked. */
class QuietUpdateTest {
    @get:Rule val tmp = TemporaryFolder()

    private class MemoryState(override var enabled: Boolean = true) : QuietState {
        override var entries: List<QuietEntry> = emptyList()
        override fun add(entry: QuietEntry) { entries = entries.withEntry(entry) }
        override var pending: QuietPending? = null
        override var lastSeenVersionCode = 0
        override var hintedKey: String? = null
        override var gameGoing = false
    }

    private class FakeSource(var info: UpdateInfo = UpdateInfo(7, "1.0.7-preview"), var fail: Boolean = false) : QuietSource {
        var downloads = 0
        var onDownload: () -> Unit = {}
        override suspend fun latest(): UpdateInfo = if (fail) throw IOException("offline") else info
        override suspend fun download(dir: File, cancelled: () -> Boolean): File {
            downloads++
            onDownload()
            dir.mkdirs()
            return File(dir, "PipPop.apk").also { it.writeText("apk") }
        }
    }

    private class FakeVerifier(var problem: String? = null) : QuietVerifier {
        override fun problem(apk: File) = problem
    }

    private class FakeInstaller(
        var result: InstallResult = InstallResult.Installed,
        override var canInstallQuietly: Boolean = true,
        var allowed: Boolean = true,
    ) : QuietInstaller {
        var installs = 0
        var pendingSeenDuringInstall: QuietPending? = null
        var state: QuietState? = null
        var abortAsked: Boolean? = null
        override fun mayInstall() = allowed
        var throwBeforeCommit = false
        override suspend fun install(apk: File, shouldAbort: () -> Boolean, onCommit: () -> Unit): InstallResult {
            abortAsked = shouldAbort()
            if (abortAsked == true) return InstallResult.Aborted
            if (throwBeforeCommit) throw kotlinx.coroutines.CancellationException("stopped")
            onCommit()
            installs++
            pendingSeenDuringInstall = state?.pending
            return result
        }
    }

    private val running = RunningApp(5, "1.0.5-preview")
    private var foreground = false
    private var clock = 1_000L

    private fun runner(
        state: QuietState, source: FakeSource = FakeSource(), verifier: FakeVerifier = FakeVerifier(),
        installer: FakeInstaller = FakeInstaller(),
    ): QuietUpdateRunner {
        installer.state = state
        return QuietUpdateRunner(state, source, verifier, installer, running, { foreground }, { clock++ }, tmp.newFolder())
    }

    private fun run(r: QuietUpdateRunner) = runBlocking { r.run() }

    // Newer or not.

    @Test fun notNewerIsLoggedAsUpToDateAndNothingIsDownloaded() {
        val state = MemoryState()
        val source = FakeSource(UpdateInfo(5, "1.0.5-preview"))
        assertEquals(QuietOutcome.UP_TO_DATE, run(runner(state, source)))
        assertEquals(0, source.downloads)
        assertEquals(listOf(QuietOutcome.UP_TO_DATE), state.entries.map { it.outcome })
    }

    @Test fun olderIsAlsoUpToDate() {
        val source = FakeSource(UpdateInfo(3, "1.0.3"))
        assertEquals(QuietOutcome.UP_TO_DATE, run(runner(MemoryState(), source)))
    }

    @Test fun newerIsDownloadedVerifiedAndInstalled() {
        val state = MemoryState()
        val installer = FakeInstaller()
        assertEquals(QuietOutcome.INSTALLED, run(runner(state, installer = installer)))
        assertEquals(1, installer.installs)
        assertEquals(listOf(QuietOutcome.INSTALLED, QuietOutcome.DOWNLOADED), state.entries.map { it.outcome })
        assertEquals(7, state.entries.first().versionCode)
        // The install is noted before it is handed over, because the app is replaced while it runs.
        assertEquals(7, installer.pendingSeenDuringInstall?.versionCode)
        assertNull(state.pending)
    }

    @Test fun downloadedFileIsDeletedAfterwards() {
        val dir = tmp.newFolder()
        val state = MemoryState()
        val installer = FakeInstaller().also { it.state = state }
        runBlocking { QuietUpdateRunner(state, FakeSource(), FakeVerifier(), installer, running, { false }, { 1L }, dir).run() }
        assertFalse(File(dir, "PipPop.apk").exists())
    }

    @Test fun serverTroubleIsLoggedAsFailed() {
        val state = MemoryState()
        val source = FakeSource(fail = true)
        assertEquals(QuietOutcome.FAILED, run(runner(state, source)))
        assertEquals("couldn't check for a new version", state.entries.first().detail)
    }

    // Verification.

    @Test fun failedVerificationDeletesTheFileAndDoesNotInstall() {
        val dir = tmp.newFolder()
        val state = MemoryState()
        val installer = FakeInstaller().also { it.state = state }
        val out = runBlocking {
            QuietUpdateRunner(state, FakeSource(), FakeVerifier("the file is signed differently"), installer, running, { false }, { 1L }, dir).run()
        }
        assertEquals(QuietOutcome.FAILED, out)
        assertEquals(0, installer.installs)
        assertFalse(File(dir, "PipPop.apk").exists())
        assertEquals("the file is signed differently", state.entries.first().detail)
    }

    private val installed = ApkFacts("com.gamesuite.app", 5, setOf("aa"))

    @Test fun apkPasses() = assertNull(apkProblem(ApkFacts("com.gamesuite.app", 6, setOf("aa")), installed))

    @Test fun apkWithAnotherPackageFails() =
        assertEquals("the file is for a different app", apkProblem(ApkFacts("com.other", 6, setOf("aa")), installed))

    @Test fun apkNotNewerFails() {
        assertEquals("the file isn't newer", apkProblem(ApkFacts("com.gamesuite.app", 5, setOf("aa")), installed))
        assertEquals("the file isn't newer", apkProblem(ApkFacts("com.gamesuite.app", 4, setOf("aa")), installed))
    }

    @Test fun apkWithAnotherCertificateFails() =
        assertEquals("the file is signed differently", apkProblem(ApkFacts("com.gamesuite.app", 6, setOf("bb")), installed))

    @Test fun apkWithNoCertificateFails() =
        assertEquals("the file is signed differently", apkProblem(ApkFacts("com.gamesuite.app", 6, emptySet()), installed))

    @Test fun unreadableApkFails() = assertEquals("the download couldn't be read", apkProblem(null, installed))

    // Setting and foreground.

    @Test fun settingOffDoesNothing() {
        val state = MemoryState(enabled = false)
        val source = FakeSource()
        assertNull(run(runner(state, source)))
        assertEquals(0, source.downloads)
        assertTrue(state.entries.isEmpty())
    }

    @Test fun foregroundAtTheStartDoesNothing() {
        foreground = true
        val state = MemoryState()
        val source = FakeSource()
        assertNull(run(runner(state, source)))
        assertEquals(0, source.downloads)
        foreground = false
    }

    @Test fun appOpenedDuringDownloadSkipsTheInstall() {
        val state = MemoryState()
        val source = FakeSource().also { it.onDownload = { foreground = true } }
        val installer = FakeInstaller()
        assertEquals(QuietOutcome.SKIPPED, run(runner(state, source, installer = installer)))
        assertEquals(0, installer.installs)
        assertNull(state.pending)
        assertEquals("the app was open", state.entries.first().detail)
        foreground = false
    }

    @Test fun installerIsToldToAbortWhenTheAppIsOpen() {
        val state = MemoryState()
        val installer = FakeInstaller(result = InstallResult.Aborted)
        assertEquals(QuietOutcome.SKIPPED, run(runner(state, installer = installer)))
        assertNull(state.pending)
    }

    @Test fun skippedIsNeverSaidAsAHint() {
        val state = MemoryState()
        state.add(QuietEntry(1, QuietOutcome.SKIPPED, 7, "1.0.7", "the app was open"))
        assertNull(QuietHints.take(state, running))
    }

    @Test fun secondRunWhileOneIsGoingDoesNotDownloadAgain() {
        val state = MemoryState()
        val source = FakeSource()
        lateinit var second: QuietUpdateRunner
        var secondResult: QuietOutcome? = QuietOutcome.INSTALLED
        second = runner(state, source)
        source.onDownload = { secondResult = run(second) }
        run(runner(state, source))
        assertNull(secondResult)
        assertEquals(1, source.downloads)
    }

    // Results.

    @Test fun needsATapIsLoggedAndNothingIsShown() {
        val state = MemoryState()
        val installer = FakeInstaller(result = InstallResult.NeedsTap("needs a tap"))
        assertEquals(QuietOutcome.NEEDED_TAP, run(runner(state, installer = installer)))
        assertEquals("needs a tap", state.entries.first().detail)
        assertEquals(7, QuietHints.neededTapFor(state))
        assertNull(state.pending)
    }

    @Test fun olderAndroidDoesNothingAndSaysNothing() {
        val state = MemoryState()
        val source = FakeSource()
        assertNull(run(runner(state, source, installer = FakeInstaller(canInstallQuietly = false))))
        assertEquals(0, source.downloads)
        assertTrue(state.entries.isEmpty())
    }

    @Test fun triggerIsOffBelowAndroid12() {
        // The app passes available = false there (SDK < 31): nothing is ever scheduled.
        val s = FakeScheduler()
        QuietTrigger(MemoryState(), s, available = false).appStopped()
        assertEquals(0, s.scheduled)
    }

    @Test fun aGameGoingSkipsTheRun() {
        val state = MemoryState().also { it.gameGoing = true }
        val source = FakeSource()
        assertEquals(QuietOutcome.SKIPPED, run(runner(state, source)))
        assertEquals(0, source.downloads)
        assertEquals("Skipped: a game was going", quietLine(state.entries.first()).substringAfter(" \u00b7 "))
        assertNull(QuietHints.take(state, running))
    }

    @Test fun leftoversFromAnEarlierRunAreRemovedAtTheStart() {
        val dir = tmp.newFolder()
        File(dir, "PipPop.apk").writeText("old")
        File(dir, "PipPop.apk.part").writeText("old")
        val state = MemoryState()
        runBlocking { QuietUpdateRunner(state, FakeSource(UpdateInfo(5, "x")), FakeVerifier(), FakeInstaller(), running, { false }, { 1L }, dir).run() }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun aVerifierThatThrowsIsLoggedAsFailed() {
        val state = MemoryState()
        val installer = FakeInstaller()
        val verifier = object : QuietVerifier { override fun problem(apk: File): String? = throw IllegalStateException("boom") }
        installer.state = state
        val out = runBlocking { QuietUpdateRunner(state, FakeSource(), verifier, installer, running, { false }, { 1L }, tmp.newFolder()).run() }
        assertEquals(QuietOutcome.FAILED, out)
        assertEquals(0, installer.installs)
        assertEquals("couldn't check the file", state.entries.first().detail)
    }

    @Test fun pendingIsOnlyNotedAtCommitAndNotLeftAfterACancelBeforeIt() {
        val state = MemoryState()
        val installer = FakeInstaller().also { it.throwBeforeCommit = true }
        try { run(runner(state, installer = installer)) } catch (_: kotlinx.coroutines.CancellationException) {}
        assertNull(state.pending)
        assertTrue(state.entries.none { it.outcome == QuietOutcome.FAILED })
    }

    @Test fun pendingIsClearedWhenAbortedBeforeTheCommit() {
        val state = MemoryState()
        foreground = false
        val installer = FakeInstaller(result = InstallResult.Aborted)
        run(runner(state, installer = installer))
        assertNull(state.pending)
    }

    @Test fun downloadsOverTheCapAreRefused() {
        val tooBig = MAX_APK_BYTES + 1
        val out = java.io.ByteArrayOutputStream()
        // Announced size over the cap: refused before reading.
        assertTrue(runCatching { copyCapped(java.io.ByteArrayInputStream(ByteArray(10)), out, tooBig, MAX_APK_BYTES, {}, { false }) }.exceptionOrNull() is IOException)
        // Size not announced, but more arrives than the cap.
        assertTrue(runCatching { copyCapped(java.io.ByteArrayInputStream(ByteArray(300_000)), out, -1, 200_000, {}, { false }) }.exceptionOrNull() is IOException)
        // Within the cap.
        val ok = java.io.ByteArrayOutputStream()
        copyCapped(java.io.ByteArrayInputStream(ByteArray(1000)), ok, 1000, 2000, {}, { false })
        assertEquals(1000, ok.size())
        assertEquals(100L * 1024 * 1024, MAX_APK_BYTES)
    }

    @Test fun onlyHttpsOnGitHubHostsIsAllowed() {
        fun ok(u: String) = allowedUpdateUrl(java.net.URL(u))
        assertTrue(ok("https://github.com/o/r/releases/download/preview/PipPop.apk"))
        assertTrue(ok("https://objects.githubusercontent.com/x"))
        assertTrue(ok("https://release-assets.githubusercontent.com/x"))
        assertFalse(ok("http://github.com/x"))
        assertFalse(ok("https://evil.example.com/x"))
        assertFalse(ok("https://github.com.evil.example/x"))
    }

    @Test fun installsNotAllowedYetNeedsATap() {
        val state = MemoryState()
        val source = FakeSource()
        assertEquals(QuietOutcome.NEEDED_TAP, run(runner(state, source, installer = FakeInstaller(allowed = false))))
        assertEquals(0, source.downloads)
    }

    @Test fun installerFailureIsLoggedWithItsReason() {
        val state = MemoryState()
        assertEquals(QuietOutcome.FAILED, run(runner(state, installer = FakeInstaller(result = InstallResult.Failed("not enough space")))))
        assertEquals("not enough space", state.entries.first().detail)
    }

    @Test fun logKeepsTheLastFiveAndFoldsRepeats() {
        val state = MemoryState()
        for (i in 1..8) state.add(QuietEntry(i.toLong(), QuietOutcome.FAILED, i, "v$i", "x"))
        assertEquals(5, state.entries.size)
        assertEquals(8, state.entries.first().versionCode)
        state.add(QuietEntry(99, QuietOutcome.FAILED, 8, "v8", "x"))
        assertEquals(5, state.entries.size)
        assertEquals(99L, state.entries.first().at)
        state.add(QuietEntry(100, QuietOutcome.UP_TO_DATE))
        state.add(QuietEntry(101, QuietOutcome.UP_TO_DATE))
        assertEquals(listOf(101L, 99L), state.entries.take(2).map { it.at })
    }

    // Trigger.

    private class FakeScheduler : QuietScheduler {
        var scheduled = 0
        var cancelled = 0
        override fun schedule() { scheduled++ }
        override fun cancel() { cancelled++ }
    }

    @Test fun closingTheAppSchedulesOneJob() {
        val s = FakeScheduler()
        QuietTrigger(MemoryState(), s, available = true).appStopped()
        assertEquals(1, s.scheduled)
    }

    @Test fun settingOffSchedulesNothing() {
        val s = FakeScheduler()
        QuietTrigger(MemoryState(enabled = false), s, available = true).appStopped()
        assertEquals(0, s.scheduled)
    }

    @Test fun aBuildWithoutAnUpdateAddressSchedulesNothing() {
        val s = FakeScheduler()
        QuietTrigger(MemoryState(), s, available = false).appStopped()
        assertEquals(0, s.scheduled)
    }

    @Test fun comingBackCancelsTheJob() {
        val s = FakeScheduler()
        QuietTrigger(MemoryState(), s, available = true).appStarted()
        assertEquals(1, s.cancelled)
    }

    // The hint.

    private val after = RunningApp(7, "1.0.7-preview")

    @Test fun hintAfterAQuietUpdateIsShownOnce() {
        val state = MemoryState()
        state.lastSeenVersionCode = 5
        state.add(QuietEntry(10, QuietOutcome.INSTALLED, 7, "1.0.7-preview"))
        val hint = QuietHints.take(state, after)
        assertEquals(QuietHint("Updated by itself to 1.0.7-preview", good = true), hint)
        assertNull(QuietHints.take(state, after))
        assertEquals(7, state.lastSeenVersionCode)
    }

    @Test fun noHintWhenTheVersionDidNotChange() {
        val state = MemoryState()
        state.lastSeenVersionCode = 7
        state.add(QuietEntry(10, QuietOutcome.INSTALLED, 7, "1.0.7-preview"))
        assertNull(QuietHints.take(state, after))
    }

    @Test fun noHintForAManualUpdate() {
        val state = MemoryState()
        state.lastSeenVersionCode = 5
        state.add(QuietEntry(10, QuietOutcome.UP_TO_DATE))
        assertNull(QuietHints.take(state, after))
    }

    @Test fun firstEverOpenHasNoHint() {
        val state = MemoryState()
        assertNull(QuietHints.take(state, running))
        assertEquals(5, state.lastSeenVersionCode)
    }

    @Test fun aTapNeededHintSaysSoPlainlyAndOnlyOnce() {
        val state = MemoryState()
        state.lastSeenVersionCode = 5
        state.add(QuietEntry(10, QuietOutcome.NEEDED_TAP, 7, "1.0.7-preview", "needs a tap"))
        assertEquals(QuietHint("Couldn't update by itself: needs a tap", good = false), QuietHints.take(state, running))
        assertNull(QuietHints.take(state, running))
        // The same news a day later is not said again, but a different reason is.
        state.add(QuietEntry(20, QuietOutcome.NEEDED_TAP, 7, "1.0.7-preview", "needs a tap"))
        assertNull(QuietHints.take(state, running))
        state.add(QuietEntry(30, QuietOutcome.FAILED, 7, "1.0.7-preview", "couldn't download it"))
        assertEquals("Couldn't update by itself: couldn't download it", QuietHints.take(state, running)?.text)
    }

    @Test fun noFailureHintOnceTheAppHasCaughtUp() {
        val state = MemoryState()
        state.add(QuietEntry(10, QuietOutcome.FAILED, 7, "1.0.7-preview", "couldn't download it"))
        assertNull(QuietHints.take(state, after))
    }

    @Test fun anInstallThatFinishedWhileTheAppWasReplacedIsSettledOnTheNextOpen() {
        val state = MemoryState()
        state.lastSeenVersionCode = 5
        state.pending = QuietPending(7, "1.0.7-preview", 10)
        assertEquals("Updated by itself to 1.0.7-preview", QuietHints.take(state, after)?.text)
        assertNull(state.pending)
        assertEquals(QuietOutcome.INSTALLED, state.entries.first().outcome)
    }

    @Test fun anInstallThatNeverHappenedIsLoggedAsFailed() {
        val state = MemoryState()
        state.lastSeenVersionCode = 5
        state.pending = QuietPending(7, "1.0.7-preview", 10)
        assertEquals("Couldn't update by itself: the install didn't finish", QuietHints.take(state, running)?.text)
    }

    @Test fun logLinesReadInWords() {
        val zone = java.time.ZoneOffset.UTC
        val at = java.time.LocalDateTime.of(2026, 11, 14, 9, 12).toInstant(zone).toEpochMilli()
        assertEquals("14 Nov, 09:12 · Installed by itself: 1.0.7-preview", quietLine(QuietEntry(at, QuietOutcome.INSTALLED, 7, "1.0.7-preview"), zone))
        assertEquals("14 Nov, 09:12 · Needed a tap", quietLine(QuietEntry(at, QuietOutcome.NEEDED_TAP, 7, "x", "needs a tap"), zone))
        assertEquals("14 Nov, 09:12 · Up to date", quietLine(QuietEntry(at, QuietOutcome.UP_TO_DATE), zone))
    }
}

/** The real store: it keeps the setting and the log across restarts. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class QuietStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("quiet_test", Context.MODE_PRIVATE)

    @Test fun savesTheSettingTheLogAndTheHintState() {
        prefs.edit().clear().commit()
        val a = QuietStore.over(prefs)
        assertTrue(a.enabled)
        a.enabled = false
        a.add(QuietEntry(5, QuietOutcome.INSTALLED, 7, "1.0.7", ""))
        a.pending = QuietPending(8, "1.0.8", 9)
        a.lastSeenVersionCode = 7
        a.hintedKey = "k"
        val b = QuietStore.over(prefs)
        assertFalse(b.enabled)
        assertEquals(listOf(QuietEntry(5, QuietOutcome.INSTALLED, 7, "1.0.7", "")), b.entries)
        assertEquals(QuietPending(8, "1.0.8", 9), b.pending)
        assertEquals(7, b.lastSeenVersionCode)
        assertEquals("k", b.hintedKey)
        assertEquals(listOf(5L), b.view.value.entries.map { it.at })
    }
}
