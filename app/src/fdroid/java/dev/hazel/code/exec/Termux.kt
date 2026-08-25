package dev.hazel.code.exec

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
    const val EXTRA_RESULT_PENDING_INTENT = "com.termux.RUN_COMMAND_RESULT_PENDING_INTENT"

    /** Termux packs everything it returns into one bundle under this key. */
    const val EXTRA_RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
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
        !installed -> Readiness.TermuxMissing
        installerPackage == PLAY_STORE_PACKAGE -> Readiness.TermuxFromPlayStore
        versionCode != null && versionCode < MINIMUM_VERSION_CODE ->
            Readiness.TermuxTooOld(versionCode.toString())
        !permissionGranted -> Readiness.PermissionMissing
        else -> Readiness.Ready
    }

    const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** Where to get a build that works. */
    const val RELEASES_URL = "https://github.com/termux/termux-app/releases"

    /**
     * The one line that lets Termux take commands from other apps.
     *
     * Written to be safe to run twice: appending it blindly would stack duplicate lines
     * in the config every time someone pasted it again.
     */
    const val ENABLE_EXTERNAL_APPS =
        "mkdir -p ~/.termux && grep -q '^allow-external-apps' ~/.termux/termux.properties " +
            "2>/dev/null || echo 'allow-external-apps = true' >> ~/.termux/termux.properties; " +
            "termux-reload-settings"

    /**
     * Termux cannot see the files Hazel edits until this has been run once.
     *
     * Hazel works in shared storage and Termux starts with access only to its own private
     * home, so without this every run would fail on a file that plainly exists. It raises
     * a permission dialog, which is why it is a step of its own rather than part of the
     * line above.
     */
    const val GRANT_STORAGE = "termux-setup-storage"

    /**
     * The two commands, in the order they have to happen, with the reason for each.
     *
     * Both are mandatory and neither can be run for the user. The first is the permission
     * that lets this app ask Termux for anything at all, so until it is set there is no
     * channel to send the second one down.
     */
    val SETUP_GUIDE = SetupGuide(
        runnerName = "Termux",
        downloadUrl = RELEASES_URL,
        downloadNote = "Install from GitHub, not Google Play. The Play Store copy is " +
            "frozen years behind and cannot take commands from other apps.",
        steps = listOf(
            SetupStep(
                title = "Let Termux take commands",
                why = "Termux ignores other apps until this is switched on. Safe to run " +
                    "twice: it will not add the line again.",
                command = ENABLE_EXTERNAL_APPS,
            ),
            SetupStep(
                title = "Give Termux access to your files",
                why = "Your code lives in shared storage and Termux starts out able to " +
                    "see only its own. Android will ask you to allow it.",
                command = GRANT_STORAGE,
            ),
        ),
    )
}
