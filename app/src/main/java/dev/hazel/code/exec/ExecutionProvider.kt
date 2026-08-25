package dev.hazel.code.exec

import android.content.Intent

/**
 * Where code goes to be run.
 *
 * The app never runs anything itself. This is the seam between the editor, which is the
 * same in every build, and whatever the current build is able to hand work to. The
 * F-Droid and GitHub build passes commands to a separately installed Termux; the Play
 * Store build has no runner at all and says so.
 *
 * Everything above this interface is shared code. Nothing above it may name Termux, check
 * a package, or assume a runner exists: ask [supported] and [readiness] instead.
 * docs/DISTRIBUTION-SPLIT.md explains how the two implementations are wired in.
 */
interface ExecutionProvider {

    /** Whether this build has any way to run code. False in the Play Store build. */
    val supported: Boolean

    /**
     * What still stands between the user and a working run.
     *
     * Cheap enough to call when a button is tapped. Some rungs cannot be checked without
     * attempting a run, so [Readiness.Ready] means "nothing known to be missing" rather
     * than a guarantee.
     */
    suspend fun readiness(): Readiness

    /** Runs one command and returns everything it wrote. */
    suspend fun run(request: RunRequest): RunResult

    /**
     * What to tell the user to do by hand, or null when there is nothing to set up.
     *
     * The setup screen is shared code and never names the runner. It asks for this and
     * draws whatever it is given.
     */
    val setupGuide: SetupGuide?

    /**
     * The permission the user has to grant before anything can be asked of the runner, or
     * null when the runner needs none.
     */
    val requiredPermission: String?

    /** An intent that opens the runner app, or null when there is nothing to open. */
    fun launchIntent(): Intent?

    /** Whether [runtime] is installed and on PATH. False when that cannot be established. */
    suspend fun isInstalled(runtime: Runtime): Boolean

    /**
     * Installs [runtimes] in the order given, reporting each one as it goes.
     *
     * One at a time rather than all at once: a package manager holds a lock, so a second
     * install started alongside the first fails on the lock rather than on anything real.
     * A failure does not stop the ones after it, because one language failing is no
     * reason to deny somebody the other two.
     */
    suspend fun install(runtimes: List<Runtime>, onState: (Runtime, InstallState) -> Unit)
}

/** One command to run, and where to run it. */
data class RunRequest(
    /** A shell line, already quoted. See [ShellQuote]. */
    val command: String,
    /** Absolute path of the directory the command starts in. */
    val workingDir: String,
    /** How long to wait before giving up and reporting [RunFailure.TimedOut]. */
    val timeoutMs: Long = 60_000,
)

/**
 * What a command produced.
 *
 * [exitCode] is null when the command never got as far as running, in which case
 * [failure] says why.
 */
data class RunResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val failure: RunFailure? = null,
) {
    val succeeded: Boolean get() = failure == null && exitCode == 0
}

/** Why a command did not produce an exit code. */
enum class RunFailure {
    /** This build has no runner. */
    Unsupported,

    /** The runner is installed but not ready; call [ExecutionProvider.readiness]. */
    NotReady,

    /** The runner accepted the command but is not allowed to take orders from other apps. */
    ExternalAppsDisabled,

    /** Nothing came back inside the time allowed. */
    TimedOut,

    /** Something else went wrong; the message is in [RunResult.stderr]. */
    Failed,
}

/**
 * The rungs between "nothing installed" and "ready to run", in the order a person climbs
 * them. Each one needs its own explanation on screen: a single "could not run" message is
 * what makes an integration like this feel broken.
 */
sealed interface Readiness {

    /** Nothing known to be missing. */
    data object Ready : Readiness

    /** This build cannot run code at all, by design. */
    data object Unsupported : Readiness

    /** The runner is not installed. */
    data object RunnerMissing : Readiness

    /**
     * The runner came from an app store that ships a crippled copy of it.
     *
     * Termux is the case this exists for: its Play Store build is frozen years behind and
     * cannot take commands from other apps, so it has to be replaced rather than updated.
     * Telling someone it is "too old" would send them looking for an update that will
     * never arrive.
     */
    data object RunnerFromAppStore : Readiness

    /** The runner is older than the interface this uses. */
    data class RunnerTooOld(val versionName: String?) : Readiness

    /** The user has not granted the permission that lets this app talk to the runner. */
    data object PermissionMissing : Readiness

    /**
     * The runner is refusing commands from other apps.
     *
     * This one cannot be detected in advance: the setting lives inside the runner's own
     * config file, which is not readable from here. It is only ever reported after a run
     * comes back rejected.
     */
    data object ExternalAppsDisabled : Readiness

    /** Everything is in place except the language's own tooling. */
    data class RuntimeMissing(val runtime: Runtime) : Readiness
}
