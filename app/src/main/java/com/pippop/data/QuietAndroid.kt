package com.pippop.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pippop.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest

/*
 * The Android side of "update by itself". None of this can run under Robolectric (it has no real
 * PackageInstaller), so the build is what checks it compiles; the decisions around it are tested in
 * QuietUpdateTest with fakes.
 */

/** Whether any screen of the app is in front (false while the app is closed or in the background). */
fun appInForeground(): Boolean =
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

/** Reads who signed a package and what version it is. */
private fun facts(info: PackageInfo): ApkFacts {
    val certs = if (Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners.orEmpty()
    } else {
        @Suppress("DEPRECATION")
        info.signatures.orEmpty()
    }
    val signers = certs.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    return ApkFacts(info.packageName, PackageInfoCompat.getLongVersionCode(info), signers)
}

/** Opens the downloaded file with the package manager and compares it with the installed app. */
class PackageVerifier(context: Context) : QuietVerifier {
    private val context = context.applicationContext

    override fun problem(apk: File): String? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val installed = pm.getPackageInfo(context.packageName, flags)
        return apkProblem(archive?.let(::facts), facts(installed))
    }
}

/** Installs through a PackageInstaller session and waits for Android's answer. Never shows anything itself. */
class SessionInstaller(context: Context) : QuietInstaller {
    private val context = context.applicationContext

    // Before Android 12 there is no way to ask for an install without the confirmation screen.
    override val canInstallQuietly get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun mayInstall() = context.packageManager.canRequestPackageInstalls()

    override suspend fun install(apk: File, shouldAbort: () -> Boolean, onCommit: () -> Unit): InstallResult = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12 and later may replace the app without asking, when it is not in front.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            // Not setRequestUpdateOwnership (Android 14): it needs the ENFORCE_UPDATE_OWNERSHIP permission and
            // would shut out the in-app Update button and every other installer, which is not harmless here.
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        val answer = CompletableDeferred<InstallResult>()
        val action = "${context.packageName}.QUIET_INSTALL_RESULT"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != id) return
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                answer.complete(
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> InstallResult.Installed
                        // Android wants a tap. Don't open its screen while the app is closed.
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallResult.NeedsTap("needs a tap")
                        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Failed("the install was stopped")
                        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallResult.Failed("this phone blocked it")
                        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                            InstallResult.Failed("it didn't fit the installed app")
                        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult.Failed("not enough space")
                        else -> InstallResult.Failed("Android said no")
                    },
                )
            }
        }
        var committed = false
        var registered = false
        try {
            apk.inputStream().use { input ->
                session.openWrite("PipPop.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            // The session holds its own copy now. The app is replaced when the install finishes, so
            // nothing would be left running to delete the file afterwards.
            apk.delete()
            if (shouldAbort()) return@withContext InstallResult.Aborted
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            // Android fills the answer into this intent, so it must be mutable; it names this app, so only this app gets it.
            val sender = PendingIntent.getBroadcast(
                context, id, Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ).intentSender
            onCommit()
            session.commit(sender)
            committed = true
            val result = try {
                withTimeout(WAIT_MS) { answer.await() }
            } catch (e: TimeoutCancellationException) {
                InstallResult.Failed("Android took too long")
            }
            // A tap is needed: leave nothing half-open behind. The offer on the next open starts fresh.
            if (result is InstallResult.NeedsTap) runCatching { installer.abandonSession(id) }
            result
        } finally {
            withContext(NonCancellable) {
                if (registered) runCatching { context.unregisterReceiver(receiver) }
                runCatching { session.close() }
                if (!committed) runCatching { installer.abandonSession(id) }
            }
        }
    }

    private companion object {
        const val WAIT_MS = 2 * 60 * 1000L
    }
}

/** The job: one try, on a network, after the app has closed. */
class QuietUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        QuietUpdateRunner(
            state = QuietStore.get(context),
            source = Updater(context),
            verifier = PackageVerifier(context),
            installer = SessionInstaller(context),
            running = RunningApp(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME),
            isForeground = ::appInForeground,
            now = System::currentTimeMillis,
            dir = File(context.filesDir, "quiet-update"),
        ).run()
        // Whatever happened is in the log; a failed try is not retried until the next close.
        return Result.success()
    }
}

/** WorkManager: one job at a time under one name; a second close while one waits or runs is ignored. */
class WorkScheduler(context: Context) : QuietScheduler {
    private val work = WorkManager.getInstance(context.applicationContext)

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<QuietUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // A short wait, so closing and quickly coming back never starts anything.
            .setInitialDelay(2, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        work.cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "quiet-update"
    }
}
