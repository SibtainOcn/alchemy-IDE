package com.sibtainocn.alchemy.exec

/**
 * What the setup screen has to say, supplied by whichever runner was compiled in.
 *
 * The screen itself is shared code and never names Termux. It asks the provider what the
 * runner is called, where to get it, and what has to be done once by hand, then draws
 * that. A different runner would fill this in differently and the screen would not change.
 */
data class SetupGuide(
    /** What to call the thing being set up, on screen. */
    val runnerName: String,

    /** Where a working copy comes from. */
    val downloadUrl: String,

    /** Why that page rather than an app store, when there is a reason worth giving. */
    val downloadNote: String? = null,

    /** Done once, in order. Each one is checked separately. */
    val steps: List<SetupStep>,
)

/**
 * One thing that has to be true before code can run.
 *
 * Each step is verified on its own rather than being folded into a single ready flag. A
 * handshake reaching the runner proves only that the runner is answering; it says nothing
 * about whether the runner can see the user's files. Reporting "ready" off the first
 * check, which is what this used to do, meant the screen declared success while two of
 * the three things it had asked for had not been done.
 */
data class SetupStep(
    /** Stable identity, so a provider knows which check this is. */
    val id: String,

    val title: String,

    /** What it does and why it is needed, in one sentence a non-expert can act on. */
    val why: String,

    /** A line to paste into the runner, or null when the step is a tap rather than a command. */
    val command: String? = null,

    /** What the app itself can do towards this step. */
    val action: StepAction = StepAction.None,

    /** Shown instead of [why] when the check has failed after the step was attempted. */
    val onFailure: String? = null,
)

/** What the app can offer to do for a step, beyond describing it. */
sealed interface StepAction {
    /** Nothing; the user does it in the runner. */
    data object None : StepAction

    /** Opens the page the runner is downloaded from. */
    data object Download : StepAction

    /** Requests the runtime permission the runner requires. */
    data object GrantPermission : StepAction

    /** Opens the runner app. */
    data object OpenRunner : StepAction
}

/** Where one step stands, as the checklist runs. */
sealed interface StepStatus {
    /** Not looked at yet. */
    data object Pending : StepStatus

    /** Being checked right now. */
    data object Checking : StepStatus

    /** Confirmed done. */
    data object Done : StepStatus

    /** Checked and not done. */
    data object Failed : StepStatus

    /**
     * Cannot be checked yet because an earlier step is not done.
     *
     * Distinct from [Failed] on purpose: telling somebody that step three failed, when
     * step two is what is actually wrong, sends them to fix the wrong thing.
     */
    data object Blocked : StepStatus
}
