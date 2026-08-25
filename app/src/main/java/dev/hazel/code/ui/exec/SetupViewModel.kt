package dev.hazel.code.ui.exec

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hazel.code.data.Prefs
import dev.hazel.code.exec.Execution
import dev.hazel.code.exec.ExecutionProvider
import dev.hazel.code.exec.InstallPlanner
import dev.hazel.code.exec.InstallState
import dev.hazel.code.exec.Readiness
import dev.hazel.code.exec.Runtime
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

    /** Runtimes found on PATH. Empty until the runner itself is ready. */
    var present by mutableStateOf(emptySet<Runtime>())
        private set

    var installStates by mutableStateOf(emptyMap<Runtime, InstallState>())
        private set

    var installing by mutableStateOf(false)
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
            provider.install(plan) { runtime, state ->
                installStates = installStates + (runtime to state)
                if (state == InstallState.Installed) present = present + runtime
            }
            installing = false
        }
    }

    /** What the installer would do, for the dialog to describe before it starts. */
    fun plan(selection: Set<Runtime>): List<Runtime> = InstallPlanner.plan(selection, present)
}
