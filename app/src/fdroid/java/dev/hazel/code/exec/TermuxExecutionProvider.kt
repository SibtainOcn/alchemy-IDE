package dev.hazel.code.exec

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Runs commands by asking Termux to run them.
 *
 * Termux is a separate app the user installs themselves. It publishes a service that
 * accepts a command, runs it inside its own Linux environment, and sends the output back
 * through a PendingIntent we supply. Nothing is executed inside this app, and this app
 * ships no interpreters.
 *
 * The important limitation, which shapes everything built on top of this: each command is
 * a separate process with no standard input and no terminal. There is no session, so a
 * `cd` does not carry to the next command, an interactive prompt waits forever, and the
 * output arrives in one piece when the command finishes rather than as it is produced.
 * Callers that want the feel of a shell have to keep their own working directory and pass
 * it in with every request.
 */
class TermuxExecutionProvider(private val context: Context) : ExecutionProvider {

    private val requestIds = AtomicInteger(0)

    override val supported: Boolean get() = true

    override suspend fun readiness(): Readiness = withContext(Dispatchers.IO) {
        val info = packageInfo()
        Termux.readinessOf(
            installed = info != null,
            installerPackage = installerOf(),
            versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) },
            permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Termux.PERMISSION_RUN_COMMAND,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    override suspend fun run(request: RunRequest): RunResult {
        if (readiness() !is Readiness.Ready) return RunResult(failure = RunFailure.NotReady)

        // A distinct action per request. Two runs in flight would otherwise deliver to
        // each other's receiver, and the second result would be attributed to the first.
        val action = "${context.packageName}.TERMUX_RESULT.${requestIds.incrementAndGet()}"

        return withTimeoutOrNull(request.timeoutMs) { awaitResult(action, request) }
            ?: RunResult(failure = RunFailure.TimedOut)
    }

    private suspend fun awaitResult(action: String, request: RunRequest): RunResult =
        suspendCancellableCoroutine { continuation ->
            var registered = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(from: Context?, intent: Intent?) {
                    unregister(this) { registered = false }
                    if (continuation.isActive) continuation.resume(resultOf(intent))
                }
            }

            // Not exported: the broadcast is sent by Termux but with this app's identity,
            // because the PendingIntent belongs to this app. Nothing outside can reach it.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation {
                if (registered) unregister(receiver) { registered = false }
            }

            val callback = PendingIntent.getBroadcast(
                context,
                0,
                Intent(action).setPackage(context.packageName),
                pendingIntentFlags(),
            )

            runCatching { send(request, callback) }.onFailure { error ->
                if (registered) unregister(receiver) { registered = false }
                if (continuation.isActive) {
                    continuation.resume(
                        RunResult(
                            stderr = error.message ?: "Termux refused the command",
                            failure = RunFailure.Failed,
                        )
                    )
                }
            }
        }

    private fun send(request: RunRequest, callback: PendingIntent) {
        val intent = Intent().apply {
            setClassName(Termux.PACKAGE, Termux.RUN_COMMAND_SERVICE)
            action = Termux.ACTION_RUN_COMMAND
            // bash rather than the program itself, so a request can be a whole shell line
            // with pipes and && in it. -c and not -lc: a login shell prints its greeting,
            // which would arrive as the first line of every run's output.
            putExtra(Termux.EXTRA_COMMAND_PATH, Termux.BASH)
            putExtra(Termux.EXTRA_ARGUMENTS, arrayOf("-c", request.command))
            putExtra(Termux.EXTRA_WORKDIR, request.workingDir)
            putExtra(Termux.EXTRA_BACKGROUND, true)
            putExtra(Termux.EXTRA_RESULT_PENDING_INTENT, callback)
        }
        // Termux runs this as a foreground service and posts its own notification.
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Reads what Termux sent back.
     *
     * Termux distinguishes its own failures from the command's: `err` is Termux saying it
     * could not run the thing at all, while `exitCode` is the program's own answer. A
     * command that fails on purpose is a successful run with a non-zero exit code, and
     * the two must not be reported the same way.
     */
    private fun resultOf(intent: Intent?): RunResult {
        val bundle = intent?.getBundleExtra(Termux.EXTRA_RESULT_BUNDLE)
            ?: return RunResult(
                stderr = "Termux sent an empty result",
                failure = RunFailure.Failed,
            )

        val stdout = bundle.getString(Termux.RESULT_STDOUT).orEmpty()
        val stderr = bundle.getString(Termux.RESULT_STDERR).orEmpty()
        val message = bundle.getString(Termux.RESULT_ERRMSG)
        val termuxError = bundle.getInt(Termux.RESULT_ERR, 0)

        return when {
            Termux.rejectedForExternalApps(message) -> RunResult(
                stdout = stdout,
                stderr = stderr,
                failure = RunFailure.ExternalAppsDisabled,
            )

            termuxError != 0 -> RunResult(
                stdout = stdout,
                stderr = listOf(stderr, message.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"),
                failure = RunFailure.Failed,
            )

            else -> RunResult(
                stdout = stdout,
                stderr = stderr,
                exitCode = bundle.getInt(Termux.RESULT_EXIT_CODE, 0),
            )
        }
    }

    private fun unregister(receiver: BroadcastReceiver, onDone: () -> Unit) {
        runCatching { context.unregisterReceiver(receiver) }
        onDone()
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        // Termux fills the result into the intent before sending it, which an immutable
        // PendingIntent forbids. The flag only exists from API 31; below it a
        // PendingIntent is mutable already.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return flags
    }

    private fun packageInfo(): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                Termux.PACKAGE,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(Termux.PACKAGE, 0)
        }
    }.getOrNull()

    /** Which store the installed Termux came from, when the system will say. */
    private fun installerOf(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(Termux.PACKAGE).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(Termux.PACKAGE)
        }
    }.getOrNull()
}
