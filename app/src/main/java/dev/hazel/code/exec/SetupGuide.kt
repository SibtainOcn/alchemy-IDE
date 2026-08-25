package dev.hazel.code.exec

/**
 * What the setup screen has to say, supplied by whichever runner was compiled in.
 *
 * The screen itself is shared code and never names Termux. It asks the provider what the
 * runner is called, where to get it, and which commands the user has to run once by hand,
 * then draws that. A different runner would fill this in differently and the screen would
 * not change.
 */
data class SetupGuide(
    /** What to call the thing being set up, on screen. */
    val runnerName: String,

    /** Where a working copy comes from. */
    val downloadUrl: String,

    /** Why that page rather than an app store, when there is a reason worth giving. */
    val downloadNote: String? = null,

    /** Run once, by hand, in order. */
    val steps: List<SetupStep>,
)

/**
 * One command the user has to paste in themselves.
 *
 * These cannot be run for them: the whole point of the permission they are granting is
 * that this app cannot yet tell the runner to do anything.
 */
data class SetupStep(
    val title: String,

    /** What it does and why it is needed, in one sentence a non-expert can act on. */
    val why: String,

    val command: String,
)
