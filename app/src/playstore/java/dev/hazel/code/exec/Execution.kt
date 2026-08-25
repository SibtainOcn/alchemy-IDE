package dev.hazel.code.exec

import android.content.Context

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
}
