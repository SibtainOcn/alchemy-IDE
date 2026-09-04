package com.sibtainocn.alchemy

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the app does when something goes wrong that nothing anticipated.
 *
 * The editor draws a whole document through code that is not ours, on text that came off
 * someone's disk, in shapes nobody tested - a minified bundle on one line, a file that is
 * one byte of UTF-16, a buffer being re-laid-out while a background scan is still writing
 * spans for the version before it. Every one of those has a fix once it is seen. The
 * problem is being seen: an uncaught exception on Android kills the process with a system
 * dialog that names nothing, and the person hitting it has no way to tell us what they
 * did.
 *
 * So the last thing to run before the process dies writes down what happened, and the
 * next launch shows it and offers to copy it. The app cannot continue through an unknown
 * failure - pretending otherwise is how a corrupted buffer gets written back over a real
 * file - but it can come back knowing what it was, which is the difference between a bug
 * that gets fixed and one that gets reported as "it closes sometimes".
 */
object CrashGuard {

    private const val REPORT = "last-crash.txt"

    /** How much of a trace is worth keeping. Long enough for the cause and the frames. */
    private const val MAX_CHARS = 16_000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Recording must not be able to fail the process a second time, which is what
            // an exception in here would do - and then there would be nothing at all.
            runCatching { record(app, thread.name, error) }
            // Hand back to the platform: it stops the process, and stopping is right. The
            // buffer's state is unknown from here, and a save from an unknown state is
            // worse than a crash.
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, thread: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("Alchemy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}")
            appendLine("$when_ on thread $thread")
            appendLine()
            append(trace)
        }
        file(context).writeText(text.take(MAX_CHARS))
    }

    /** The last crash, or null when the previous run ended normally. */
    fun lastReport(context: Context): String? =
        file(context).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, REPORT)
}

class AlchemyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}
