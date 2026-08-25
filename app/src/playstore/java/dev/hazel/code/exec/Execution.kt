package dev.hazel.code.exec

import android.content.Context
import android.content.Intent

/**
 * The Play Store build's answer to "where does code run": nowhere.
 *
 * This file exists so that shared code can ask for a provider without knowing which build
 * it is in. The Termux implementation is not excluded at runtime, it is not compiled into
 * this variant at all, and neither is the permission that would let it work. See
 * docs/DISTRIBUTION-SPLIT.md.
 */
object Execution {
    fun provider(context: Context): ExecutionProvider = NoExecutionProvider
}

object NoExecutionProvider : ExecutionProvider {
    override val supported: Boolean get() = false

    override suspend fun readiness(): Readiness = Readiness.Unsupported

    override suspend fun run(request: RunRequest): RunResult =
        RunResult(failure = RunFailure.Unsupported)

    override val setupGuide: SetupGuide? get() = null

    override val requiredPermission: String? get() = null

    override fun launchIntent(): Intent? = null

    override suspend fun isInstalled(runtime: Runtime): Boolean = false

    override suspend fun install(runtimes: List<Runtime>, onState: (Runtime, InstallState) -> Unit) {
        runtimes.forEach { onState(it, InstallState.Failed("This build cannot install runtimes")) }
    }
}
