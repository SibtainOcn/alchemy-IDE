package com.sibtainocn.alchemy.exec

import android.content.Context

/**
 * The F-Droid and GitHub build's answer to "where does code run": a Termux the user
 * installed themselves.
 *
 * Shared code calls [provider] without knowing which build it is in. The Play Store
 * variant has a file of the same name offering a provider that runs nothing, and only one
 * of the two is ever compiled. See docs/DISTRIBUTION-SPLIT.md.
 */
object Execution {
    fun provider(context: Context): ExecutionProvider =
        TermuxExecutionProvider(context.applicationContext)
}
