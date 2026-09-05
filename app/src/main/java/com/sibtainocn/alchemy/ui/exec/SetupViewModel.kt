package com.sibtainocn.alchemy.ui.exec

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sibtainocn.alchemy.data.Prefs
import com.sibtainocn.alchemy.exec.ConsoleLine
import com.sibtainocn.alchemy.exec.Execution
import com.sibtainocn.alchemy.exec.ExecutionProvider
import com.sibtainocn.alchemy.exec.InstallPlanner
import com.sibtainocn.alchemy.exec.InstallState
import com.sibtainocn.alchemy.exec.Readiness
import com.sibtainocn.alchemy.exec.Runtime
import com.sibtainocn.alchemy.exec.StepStatus
import com.sibtainocn.alchemy.exec.SetupStep
import kotlinx.coroutines.launch

/**
 * The state behind the setup flow: how far along the runner is, which languages are
 * present, and how an install is going.
 *
 * Held by the activity rather than by a screen, because an install is a download of a
 * couple of hundred megabytes and must not be abandoned because a dialog closed or the
 * phone was turned sideways.
 */
class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val provider: ExecutionProvider = Execution.provider(app)

    val supported: Boolean get() = provider.supported
    val guide get() = provider.setupGuide
    val requiredPermission: String? get() = provider.requiredPermission

    /** Null until the first check has finished. */
    var readiness by mutableStateOf<Readiness?>(null)
        private set

    var checking by mutableStateOf(false)
        private set

    /** The setup rows, in order. Empty when this build has nothing to set up. */
    val steps: List<SetupStep> get() = guide?.steps.orEmpty()

    /**
     * Where each row stands, keyed by step id.
     *
     * Written as each answer lands rather than in one go at the end, so the list fills in
     * from the top instead of sitting blank for the length of the slowest probe.
     */
    var stepStates by mutableStateOf(emptyMap<String, StepStatus>())
        private set

    /** Every row confirmed done. The only thing that may say setup is finished. */
    val allStepsDone: Boolean
        get() = steps.isNotEmpty() && steps.all { stepStates[it.id] == StepStatus.Done }

    /** How many rows are confirmed, for the counter in the header. */
    val stepsDone: Int get() = steps.count { stepStates[it.id] == StepStatus.Done }

    /** The first row that is not done, which is the one worth acting on. */
    val firstIncomplete: SetupStep?
        get() = steps.firstOrNull { stepStates[it.id] != StepStatus.Done }

    /** Runtimes found on PATH. Empty until the runner itself is ready. */
    var present by mutableStateOf(emptySet<Runtime>())
        private set

    var installStates by mutableStateOf(emptyMap<Runtime, InstallState>())
        private set

    var installing by mutableStateOf(false)
        private set

    /**
     * Everything the package manager printed, kept for the Logs button.
     *
     * Whole and unsummarised. A failed install is a wall of apt output with one useful
     * line somewhere in it, and choosing that line on the user's behalf has already been
     * wrong once.
     */
    var log by mutableStateOf(emptyList<ConsoleLine>())
        private set

    /** The runtimes this install was asked for, so progress can be counted against it. */
    var queue by mutableStateOf(emptyList<Runtime>())
        private set

    /** How many of [queue] have finished, whether they worked or not. */
    val finished: Int
        get() = queue.count { installStates[it] is InstallState.Failed ||
            installStates[it] == InstallState.Installed ||
            installStates[it] == InstallState.InstalledButMissing }

    /** What is downloading right now, for the one line that says so. */
    val current: Runtime?
        get() = queue.firstOrNull { installStates[it] == InstallState.Installing }

    /** Whether the setup flow has been offered before, so it is not shown unprompted twice. */
    val offeredBefore: Boolean get() = prefs.setupOffered

    fun markOffered() {
        prefs.setupOffered = true
    }

    fun launchIntent() = provider.launchIntent()

    /**
     * Re-checks everything.
     *
     * The runner's own state comes first: probing for languages means running commands
     * through it, which is pointless while it is not answering.
     */
    fun refresh() {
        if (checking || installing) return
        checking = true
        viewModelScope.launch {
            // The deep check, not the cheap one: an installed and permitted runner that is
            // refusing commands looks ready from the outside, and every question after
            // this one is asked by running something.
            val state = provider.verify()
            readiness = state
            present = if (state is Readiness.Ready) {
                Runtime.entries.filter { provider.isInstalled(it) }.toSet()
            } else {
                emptySet()
            }
            installStates = present.associateWith { InstallState.AlreadyInstalled }
            checking = false
        }
    }

    /**
     * Runs the setup checklist top to bottom, publishing each answer as it arrives.
     *
     * Sequential rather than parallel, and it stops asking once a row fails. The rows are
     * a chain: there is no point probing whether the runner can see shared storage when
     * the channel that would carry the question is not open, and an answer of "failed"
     * there would send somebody to fix the wrong thing. Rows after a failure are marked
     * [StepStatus.Blocked] instead.
     */
    fun recheck() {
        if (checking || installing) return
        val rows = steps
        if (rows.isEmpty()) return

        checking = true
        stepStates = rows.associate { it.id to StepStatus.Pending }

        viewModelScope.launch {
            var blocked = false
            for (step in rows) {
                if (blocked) {
                    stepStates = stepStates + (step.id to StepStatus.Blocked)
                    continue
                }
                stepStates = stepStates + (step.id to StepStatus.Checking)
                val ok = runCatching { provider.verifyStep(step.id) }.getOrNull()
                val status = when (ok) {
                    true -> StepStatus.Done
                    false -> StepStatus.Failed
                    // Not answerable from here. Treated as satisfied so it cannot block
                    // the rows below, and drawn as advice rather than as a tick.
                    null -> StepStatus.Done
                }
                stepStates = stepStates + (step.id to status)
                if (status == StepStatus.Failed) blocked = true
            }

            // Keep the ladder in step with the checklist, since the rest of the app still
            // asks the ladder rather than the rows.
            readiness = provider.readiness()
            if (allStepsDone) {
                present = Runtime.entries.filter { provider.isInstalled(it) }.toSet()
                installStates = present.associateWith { InstallState.AlreadyInstalled }
            }
            checking = false
        }
    }

    /**
     * Installs what was chosen and is not already there.
     *
     * The plan is worked out before anything starts so the dialog can show the whole list
     * with its own row per runtime, rather than revealing the work one item at a time.
     */
    fun install(selection: Set<Runtime>) {
        if (installing) return
        val plan = InstallPlanner.plan(selection, present)
        if (plan.isEmpty()) return

        installing = true
        queue = plan
        installStates = installStates + plan.associateWith { InstallState.Waiting }

        viewModelScope.launch {
            provider.install(
                runtimes = plan,
                onState = { runtime, state ->
                    installStates = installStates + (runtime to state)
                    if (state == InstallState.Installed) present = present + runtime
                },
                onOutput = { runtime, result ->
                    log = log + buildList {
                        add(ConsoleLine.Typed(runtime.installCommand))
                        if (result.stdout.isNotBlank()) add(ConsoleLine.Output(result.stdout.trimEnd()))
                        if (result.stderr.isNotBlank()) add(ConsoleLine.Error(result.stderr.trimEnd()))
                        add(ConsoleLine.Note("${runtime.label}: exit ${result.exitCode ?: "none"}"))
                    }
                },
            )
            installing = false
        }
    }

    /** What the installer would do, for the dialog to describe before it starts. */
    fun plan(selection: Set<Runtime>): List<Runtime> = InstallPlanner.plan(selection, present)
}
