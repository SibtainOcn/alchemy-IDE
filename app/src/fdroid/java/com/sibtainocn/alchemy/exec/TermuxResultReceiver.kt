package com.sibtainocn.alchemy.exec

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where Termux's answer arrives.
 *
 * Declared in the manifest rather than registered at runtime, which is the whole point of
 * it. A runtime receiver has to declare whether it is exported, and a broadcast that
 * arrives through a PendingIntent another app sends does not reliably satisfy the
 * not-exported test: Termux ran the command, sent the result, and nothing was listening.
 * A PendingIntent aimed at an explicit component of ours has no such question to answer.
 * It reaches this class whether or not the app is even in memory, and no other app can
 * reach it, because the receiver is not exported.
 *
 * The waiting side leaves a callback in [TermuxResults] under a request id that travels
 * out with the PendingIntent and comes back in the reply.
 */
class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val id = intent?.getIntExtra(TermuxResults.EXTRA_REQUEST_ID, -1) ?: -1
        val result = intent?.getBundleExtra(Termux.EXTRA_RESULT_BUNDLE)

        Log.d(
            TermuxResults.TAG,
            "result for request $id: " +
                "exit=${result?.getInt(Termux.RESULT_EXIT_CODE, -1)} " +
                "err=${result?.getInt(Termux.RESULT_ERR, 0)} " +
                "stdout=${result?.getString(Termux.RESULT_STDOUT)?.length ?: 0} chars",
        )

        TermuxResults.deliver(id, result)
    }
}

/**
 * The desk the receiver leaves messages on.
 *
 * A broadcast receiver is created by the system and cannot be handed anything, so the
 * coroutine waiting for a reply parks a callback here first and takes it back either when
 * the reply lands or when the wait is given up on.
 */
object TermuxResults {

    const val TAG = "AlchemyTermux"
    const val EXTRA_REQUEST_ID = "com.sibtainocn.alchemy.REQUEST_ID"

    private val ids = AtomicInteger(1)
    private val waiting = ConcurrentHashMap<Int, (Bundle?) -> Unit>()

    fun nextId(): Int = ids.incrementAndGet()

    fun expect(id: Int, onResult: (Bundle?) -> Unit) {
        waiting[id] = onResult
    }

    fun forget(id: Int) {
        waiting.remove(id)
    }

    /** Hands the reply to whoever is waiting for it, once. */
    fun deliver(id: Int, result: Bundle?) {
        val callback = waiting.remove(id)
        if (callback == null) {
            // The wait was given up on, or this is a duplicate. Worth a line, because it
            // is the difference between "no answer came" and "an answer came too late".
            Log.d(TAG, "result for request $id arrived with nobody waiting")
            return
        }
        callback(result)
    }
}
