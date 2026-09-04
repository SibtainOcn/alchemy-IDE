package com.sibtainocn.alchemy.exec

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * The last cheap check and when it was taken.
     *
     * The package manager is a round trip to another process, and asking it again before
     * every command puts that delay in front of every `ls`. Nothing it reports can change
     * without the user leaving this app, so a couple of seconds of memory is safe and is
     * the difference between a terminal that feels immediate and one that does not.
     */
    private var cached: Readiness? = null
    private var cachedAt = 0L

    override val supported: Boolean get() = true

    override suspend fun readiness(): Readiness {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_MS) return it }
        return check().also {
            cached = it
            cachedAt = now
        }
    }

    /** Throws the remembered answer away, after something that could have changed it. */
    fun invalidate() {
        cached = null
    }

    private suspend fun check(): Readiness = withContext(Dispatchers.IO) {
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

    override suspend fun verify(): Readiness {
        invalidate()
        val basics = readiness()
        if (basics !is Readiness.Ready) return basics

        // Termux answers a refusal with a notification and never calls back, so there is
        // nothing to read: only the silence says so. This is also what wakes Termux, since
        // a background command starts its service whether or not the app was running.
        val handshake = send(
            RunRequest(
                command = Termux.HANDSHAKE,
                workingDir = Termux.HOME,
                timeoutMs = HANDSHAKE_TIMEOUT_MS,
            )
        )
        return if (handshake.stdout.contains(Termux.HANDSHAKE_REPLY)) {
            Readiness.Ready
        } else {
            Readiness.RunnerNotAnswering
        }
    }

    override suspend fun run(request: RunRequest): RunResult {
        if (readiness() !is Readiness.Ready) return RunResult(failure = RunFailure.NotReady)
        return send(request)
    }

    /** Sends a command without asking again whether it is worth sending. */
    private suspend fun send(request: RunRequest): RunResult {
        // One id per request, carried out with the PendingIntent and back with the reply.
        // Two commands in flight would otherwise collect each other's answers.
        val id = TermuxResults.nextId()
        Log.d(TermuxResults.TAG, "request $id: ${request.command.take(120)}")

        val result = withTimeoutOrNull(request.timeoutMs) { awaitResult(id, request) }
        if (result == null) {
            TermuxResults.forget(id)
            Log.w(TermuxResults.TAG, "request $id: no reply within ${request.timeoutMs}ms")
            return RunResult(failure = RunFailure.TimedOut)
        }
        return result
    }

    override val setupGuide: SetupGuide get() = Termux.SETUP_GUIDE

    override val requiredPermission: String get() = Termux.PERMISSION_RUN_COMMAND

    override fun launchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(Termux.PACKAGE)

    override val homeDirectory: String get() = Termux.HOME

    override suspend fun canReach(path: String): Boolean {
        val result = run(
            RunRequest(
                command = "test -d ${ShellQuote.single(path)} && echo reachable",
                workingDir = Termux.HOME,
                timeoutMs = PROBE_TIMEOUT_MS,
            )
        )
        return result.stdout.contains("reachable")
    }

    override suspend fun isInstalled(runtime: Runtime): Boolean {
        val result = run(
            RunRequest(
                command = runtime.probeCommand,
                workingDir = Termux.HOME,
                timeoutMs = PROBE_TIMEOUT_MS,
            )
        )
        // `command -v` says nothing and exits non-zero when the program is not there.
        return result.exitCode == 0 && result.stdout.isNotBlank()
    }

    override suspend fun install(
        runtimes: List<Runtime>,
        onState: (Runtime, InstallState) -> Unit,
        onOutput: (Runtime, RunResult) -> Unit,
    ) {
        runtimes.forEach { runtime ->
            onState(runtime, InstallState.Installing)

            val result = run(
                RunRequest(
                    command = runtime.installCommand,
                    workingDir = Termux.HOME,
                    timeoutMs = INSTALL_TIMEOUT_MS,
                )
            )

            onOutput(runtime, result)

            val state = when {
                result.failure == RunFailure.TimedOut ->
                    InstallState.Failed("Timed out. A slow connection can outlast the wait.")

                result.failure != null ->
                    InstallState.Failed(lastMeaningfulLine(result.stderr, "Could not reach Termux"))

                result.exitCode != 0 -> InstallState.Failed(
                    lastMeaningfulLine(
                        result.stderr.ifBlank { result.stdout },
                        "The package manager exited with ${result.exitCode}",
                    )
                )

                // Installed as far as the package manager is concerned. Whether the
                // program is actually on PATH is a separate question, and the answer is
                // not always yes: a broken mirror or a half-finished earlier install both
                // end here.
                !isInstalled(runtime) -> InstallState.InstalledButMissing

                else -> InstallState.Installed
            }

            onState(runtime, state)
        }
    }

    /**
     * Sends one command and waits for the reply the receiver will hand back.
     *
     * The wait is registered before the command goes out, because a fast command can
     * answer before this function has finished starting.
     */
    private suspend fun awaitResult(id: Int, request: RunRequest): RunResult =
        suspendCancellableCoroutine { continuation ->
            TermuxResults.expect(id) { bundle ->
                if (continuation.isActive) continuation.resume(resultOf(bundle))
            }
            continuation.invokeOnCancellation { TermuxResults.forget(id) }

            val callback = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, TermuxResultReceiver::class.java)
                    .putExtra(TermuxResults.EXTRA_REQUEST_ID, id),
                pendingIntentFlags(),
            )

            runCatching { dispatch(request, callback) }.onFailure { error ->
                Log.w(TermuxResults.TAG, "could not hand the command to Termux", error)
                TermuxResults.forget(id)
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

    private fun dispatch(request: RunRequest, callback: PendingIntent) {
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
    private fun resultOf(bundle: Bundle?): RunResult {
        if (bundle == null) {
            return RunResult(
                stderr = "Termux sent an empty result",
                failure = RunFailure.Failed,
            )
        }

        val stdout = bundle.getString(Termux.RESULT_STDOUT).orEmpty()
        val stderr = bundle.getString(Termux.RESULT_STDERR).orEmpty()
        val message = bundle.getString(Termux.RESULT_ERRMSG)
        // Anything above zero is a Termux-side failure. Success is -1, and an absent key
        // has to read as success too, or a working command is reported as a broken one.
        val termuxError = bundle.getInt(Termux.RESULT_ERR, Termux.ERR_SUCCESS)
        val stdoutLength = lengthOf(bundle, Termux.RESULT_STDOUT_LENGTH, stdout.length)
        val stderrLength = lengthOf(bundle, Termux.RESULT_STDERR_LENGTH, stderr.length)

        return when {
            Termux.rejectedForExternalApps(message) -> RunResult(
                stdout = stdout,
                stderr = stderr,
                failure = RunFailure.ExternalAppsDisabled,
                stdoutFullLength = stdoutLength,
                stderrFullLength = stderrLength,
            )

            // Termux could not run the thing. Whatever it managed to say is kept as well
            // as its own explanation, because either one might be the answer.
            termuxError > 0 -> RunResult(
                stdout = stdout,
                stderr = listOf(stderr, message.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"),
                failure = RunFailure.Failed,
                stdoutFullLength = stdoutLength,
                stderrFullLength = stderrLength,
            )

            else -> RunResult(
                stdout = stdout,
                stderr = stderr,
                exitCode = bundle.getInt(Termux.RESULT_EXIT_CODE, 0),
                stdoutFullLength = stdoutLength,
                stderrFullLength = stderrLength,
            )
        }
    }

    /**
     * Reads one of the original-length fields.
     *
     * Defensive about the type: this is another project's bundle, and a number that
     * arrives as text should not cost the truncation warning.
     */
    private fun lengthOf(bundle: Bundle, key: String, fallback: Int): Int {
        if (!bundle.containsKey(key)) return fallback
        val asInt = bundle.getInt(key, Int.MIN_VALUE)
        if (asInt != Int.MIN_VALUE) return asInt
        return bundle.getString(key)?.trim()?.toIntOrNull() ?: fallback
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

    private companion object {
        /** A probe is one process and no network; anything slower has gone wrong. */
        const val PROBE_TIMEOUT_MS = 15_000L

        /**
         * How long to wait for `echo` before concluding nobody is listening.
         *
         * Short on purpose. A working Termux answers this in well under a second, and
         * this wait is spent in front of the user with a terminal opening.
         */
        const val HANDSHAKE_TIMEOUT_MS = 7_000L

        /** How long a package-manager answer is trusted for. */
        const val CACHE_MS = 3_000L

        /**
         * Long, because this is a download over whatever connection the phone has, and
         * Go is a quarter of a gigabyte. Better to wait than to abandon an install
         * half-written and leave the package manager to be repaired by hand.
         */
        const val INSTALL_TIMEOUT_MS = 15 * 60 * 1000L
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
