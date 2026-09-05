package com.sibtainocn.alchemy.exec

/**
 * The contract Termux publishes for other apps, kept in one place.
 *
 * These strings belong to Termux, not to us. They are copied from its RunCommandService
 * documentation, and every one of them is a promise another project has to keep, so they
 * are gathered here rather than scattered through the code that uses them.
 */
object Termux {

    const val PACKAGE = "com.termux"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    /**
     * Where Termux sends the output.
     *
     * The name has no RESULT in it, unlike every key inside the bundle it delivers. Get it
     * wrong and nothing at all goes wrong: Termux takes the command, runs it, finds no
     * pending intent under the name it looks for, and drops the output on the floor. No
     * error, no reply, and a caller waiting for something that was never going to arrive.
     * Verified against the extras in Termux 0.119's own dex rather than from memory.
     */
    const val EXTRA_RESULT_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    /** Termux packs everything it returns into one bundle under this key. */
    const val EXTRA_RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"

    /**
     * What Termux puts in [RESULT_ERR] when nothing went wrong.
     *
     * Minus one, not zero: its Errno type numbers success as -1 and real failures above
     * zero. Reading a missing key as zero and treating zero as an error turns every
     * successful command into a failed one, which is exactly what it did.
     */
    const val ERR_SUCCESS = -1

    /**
     * How much the program really wrote, when the result had to be cut to fit.
     *
     * Termux reports these alongside the text so a caller can tell a short program from a
     * truncated one.
     */
    const val RESULT_STDOUT_LENGTH = "stdout_original_length"
    const val RESULT_STDERR_LENGTH = "stderr_original_length"
    const val RESULT_ERRMSG = "errmsg"

    /** Where Termux keeps the programs it has installed. */
    const val BIN_DIR = "/data/data/com.termux/files/usr/bin"
    const val BASH = "$BIN_DIR/bash"
    const val HOME = "/data/data/com.termux/files/home"

    /**
     * The first Termux release whose RunCommandService behaves as documented here.
     *
     * Google Play's copy is frozen well below this and cannot be updated past it, which
     * is why the store a build came from is worth checking separately.
     */
    const val MINIMUM_VERSION_CODE = 118L

    /**
     * Termux reports a rejected command in prose rather than with a code of its own, so
     * the one case worth acting on is recognised by what its message mentions.
     */
    fun rejectedForExternalApps(message: String?): Boolean =
        message != null && message.contains("allow-external-apps", ignoreCase = true)

    /**
     * Decides how far along the setup is, from the facts that can be established without
     * running anything.
     *
     * Kept apart from the Android calls that gather those facts so the ladder itself can
     * be tested. The last two rungs, a refused command and a missing runtime, are only
     * ever learned by trying.
     */
    fun readinessOf(
        installed: Boolean,
        installerPackage: String?,
        versionCode: Long?,
        permissionGranted: Boolean,
    ): Readiness = when {
        !installed -> Readiness.RunnerMissing
        installerPackage == PLAY_STORE_PACKAGE -> Readiness.RunnerFromAppStore
        versionCode != null && versionCode < MINIMUM_VERSION_CODE ->
            Readiness.RunnerTooOld(versionCode.toString())
        !permissionGranted -> Readiness.PermissionMissing
        else -> Readiness.Ready
    }

    const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** Where to get a build that works. */
    const val RELEASES_URL = "https://github.com/termux/termux-app/releases"

    /**
     * A command that proves the whole channel works, end to end.
     *
     * Cheap enough to run before every terminal session. It is the only way to know that
     * external apps are allowed: Termux reports that refusal as a notification of its own
     * and never answers the caller, so the absence of a reply is the signal.
     */
    const val HANDSHAKE = "echo alchemy-ok"
    const val HANDSHAKE_REPLY = "alchemy-ok"

    /**
     * Rewrites the property rather than appending to it.
     *
     * The earlier version only appended when `grep` found nothing, which leaves a file
     * that already carries the setting in some other shape exactly as it was: commented
     * out, spelled with different spacing, or set to false. Deleting every form of the
     * line first and writing one clean one is the version that fixes a broken file as
     * well as an empty one, and it stays safe to run again.
     */
    const val ENABLE_EXTERNAL_APPS =
        "mkdir -p ~/.termux && " +
            "touch ~/.termux/termux.properties && " +
            "sed -i '/allow-external-apps/d' ~/.termux/termux.properties && " +
            "echo 'allow-external-apps=true' >> ~/.termux/termux.properties && " +
            "termux-reload-settings && " +
            "echo done"

    /**
     * Termux cannot see the files Alchemy edits until this has been run once.
     *
     * Alchemy works in shared storage and Termux starts with access only to its own private
     * home, so without this every run would fail on a file that plainly exists. It raises
     * a permission dialog, which is why it is a step of its own rather than part of the
     * line above.
     */
    const val GRANT_STORAGE = "termux-setup-storage"

    /** Step ids, so the provider knows which check belongs to which row. */
    const val STEP_INSTALL = "install"
    const val STEP_PERMISSION = "permission"
    const val STEP_EXTERNAL = "external-apps"
    const val STEP_STORAGE = "storage"

    /** Created by [GRANT_STORAGE], and the cheapest proof that it has been run. */
    const val STORAGE_DIR = "$HOME/storage"

    /**
     * Everything that has to be true, in the order it has to become true.
     *
     * Each row is checked on its own. The screen used to report readiness from the
     * handshake alone, which proves only that Termux answers: somebody who had run the
     * first command and neither of the others was told they were ready, and then every
     * run failed on a file that plainly existed.
     */
    val SETUP_GUIDE = SetupGuide(
        runnerName = "Termux",
        downloadUrl = RELEASES_URL,
        downloadNote = "Install from GitHub, not Google Play. The Play Store copy is " +
            "frozen years behind and cannot take commands from other apps.",
        steps = listOf(
            SetupStep(
                id = STEP_INSTALL,
                title = "Install Termux",
                why = "Alchemy has no shell of its own. Termux provides one.",
                action = StepAction.Download,
                onFailure = "Get it from GitHub. The Google Play build cannot take " +
                    "commands from other apps and cannot be updated into one that can.",
            ),
            SetupStep(
                id = STEP_PERMISSION,
                title = "Allow Alchemy to talk to Termux",
                why = "Android asks you once. Nothing runs without it.",
                action = StepAction.GrantPermission,
            ),
            SetupStep(
                id = STEP_EXTERNAL,
                title = "Let Termux take commands",
                why = "Termux ignores other apps until this is switched on. Safe to run " +
                    "again at any time.",
                command = ENABLE_EXTERNAL_APPS,
                onFailure = "Still not answering. Run the line again, then close Termux " +
                    "from recent apps and reopen it: the service that takes commands " +
                    "sometimes holds the old setting until Termux is started fresh.",
            ),
            SetupStep(
                id = STEP_STORAGE,
                title = "Give Termux access to your files",
                why = "Your code is in shared storage and Termux starts out able to see " +
                    "only its own. Android will ask you to allow it.",
                command = GRANT_STORAGE,
                onFailure = "Termux still cannot reach your files. Run the line and " +
                    "accept the permission dialog Termux raises.",
            ),
        ),
    )
}
