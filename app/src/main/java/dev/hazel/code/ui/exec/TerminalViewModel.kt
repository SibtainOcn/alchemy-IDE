package dev.hazel.code.ui.exec

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hazel.code.exec.Console
import dev.hazel.code.exec.ConsoleLine
import dev.hazel.code.exec.Execution
import dev.hazel.code.exec.ExecutionProvider
import dev.hazel.code.exec.Readiness
import dev.hazel.code.exec.RunFailure
import dev.hazel.code.exec.RunRequest
import dev.hazel.code.exec.Runtime
import dev.hazel.code.exec.ShellQuote
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * The terminal sheet's state, and the one place that knows a command is not a session.
 *
 * Each command is a separate process, so the working directory has to be carried here and
 * passed back in every time. A bare `cd` never reaches the shell as a lasting thing: it
 * is run once to find out whether the directory exists, and the answer is kept.
 *
 * Lives on the activity, so a run survives the sheet being swiped away and the phone
 * being turned.
 */
class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val provider: ExecutionProvider = Execution.provider(app)
    private var job: Job? = null

    val supported: Boolean get() = provider.supported

    var open by mutableStateOf(false)
        private set

    var lines by mutableStateOf(emptyList<ConsoleLine>())
        private set

    /** Where the next command will start. Absolute, and never shown in the prompt itself. */
    var directory by mutableStateOf("")
        private set

    var running by mutableStateOf(false)
        private set

    /** True while the channel is being checked, before the sheet is worth opening. */
    var checking by mutableStateOf(false)
        private set

    /**
     * Opens the sheet, pointing at [dir].
     *
     * The directory is announced once, as a note, because the prompt deliberately stays
     * `~ $`: a full storage path is forty characters of noise in front of every line
     * somebody types.
     */
    fun openAt(dir: String) {
        if (directory != dir) {
            directory = dir
            append(ConsoleLine.Note("in $dir"))
        }
        open = true
    }

    fun close() {
        open = false
    }

    /**
     * Everything that has to be true before a terminal is worth showing, checked in the
     * order the user can fix them.
     *
     * Runs on every open rather than once at startup. Termux can be uninstalled, denied,
     * reconfigured or simply not started since the last time, and finding that out from a
     * command that never returns is the worst way to learn it.
     */
    suspend fun preflight(dir: String): Readiness {
        checking = true
        try {
            val state = provider.verify()
            if (state !is Readiness.Ready) return state
            return if (provider.canReach(dir)) Readiness.Ready else Readiness.StorageUnreachable
        } finally {
            checking = false
        }
    }

    /**
     * Abandons whatever is running.
     *
     * Only on this side: the command itself carries on inside the runner, because there
     * is no way to reach into it and stop a process. Said plainly rather than implied, so
     * nobody assumes a long install was cancelled when it was only stopped being watched.
     */
    fun cancel() {
        if (!running) return
        job?.cancel()
        job = null
        running = false
        append(ConsoleLine.Note("stopped waiting. The command may still be running in Termux."))
    }

    fun clear() {
        lines = emptyList()
        if (directory.isNotEmpty()) append(ConsoleLine.Note("in $directory"))
    }

    /** Whatever was typed at the prompt. */
    fun submit(input: String) {
        val command = input.trim()
        if (command.isEmpty() || running) return
        append(ConsoleLine.Typed(command))

        val cd = Console.cdTarget(command)
        if (cd != null) changeDirectory(cd) else execute(command)
    }

    /**
     * Runs [file] with whatever the language needs, the way the Run button does.
     *
     * Returns false when there is nothing to run it with, so the caller can offer to
     * install one rather than opening an empty terminal.
     */
    fun runFile(file: File, runtime: Runtime) {
        openAt(file.parent ?: directory)
        val command = runtime.commandFor(file.absolutePath)
        append(ConsoleLine.Typed(command))
        execute(command)
    }

    /**
     * A `cd` is checked against the filesystem rather than believed.
     *
     * Tracking it locally without asking would let someone walk into a directory that is
     * not there, and every command after that would fail for a reason the terminal had
     * already been told and had not passed on.
     */
    private fun changeDirectory(argument: String) {
        val home = provider.homeDirectory ?: directory
        val target = Console.resolve(directory, argument, home)
        running = true
        job = viewModelScope.launch {
            val probe = provider.run(
                RunRequest(
                    command = "cd ${ShellQuote.single(target)} && pwd",
                    workingDir = directory,
                    timeoutMs = CD_TIMEOUT_MS,
                )
            )
            running = false

            val landed = probe.stdout.trim()
            if (probe.exitCode == 0 && landed.isNotEmpty()) {
                directory = landed
                append(ConsoleLine.Note("in $landed"))
            } else {
                reportFailure(probe, fallback = "cd: $argument: no such directory")
            }
        }
    }

    private fun execute(command: String) {
        running = true
        val startedAt = System.currentTimeMillis()

        job = viewModelScope.launch {
            val result = provider.run(RunRequest(command = command, workingDir = directory))
            running = false
            report(result, startedAt)
        }
    }

    /**
     * Puts everything the program said on screen, exactly as it said it.
     *
     * Whole and unedited, on purpose. A traceback is the thing someone learning a language
     * actually needs to read, and an editor that replaces it with "exit 1" has taken away
     * the only part of the run that explains anything. Output comes before any of our own
     * commentary, and it is printed even when the run failed on our side, because a
     * program that printed three lines and then hit a wall wrote three useful lines.
     */
    private fun report(result: dev.hazel.code.exec.RunResult, startedAt: Long) {
        if (result.stdout.isNotEmpty()) append(ConsoleLine.Output(result.stdout.trimEnd('\n')))
        if (result.stderr.isNotEmpty()) append(ConsoleLine.Error(result.stderr.trimEnd('\n')))

        if (result.stdoutTruncated || result.stderrTruncated) {
            append(ConsoleLine.Note(truncationNote(result)))
        }

        if (result.failure != null) {
            reportFailure(result, fallback = "could not run")
        } else {
            append(
                ConsoleLine.Note(
                    Console.summarise(result.exitCode, System.currentTimeMillis() - startedAt)
                )
            )
        }
    }

    /** Says how much was lost, rather than letting a cut-off traceback look complete. */
    private fun truncationNote(result: dev.hazel.code.exec.RunResult): String {
        val shown = result.stdout.length + result.stderr.length
        val written = result.stdoutFullLength + result.stderrFullLength
        return "output was too large to pass back whole: showing $shown of $written characters. " +
            "Redirect it to a file to see all of it."
    }

    private fun reportFailure(result: dev.hazel.code.exec.RunResult, fallback: String) {
        val message = when (result.failure) {
            RunFailure.ExternalAppsDisabled ->
                "The terminal app is refusing commands. Run the setup again from the menu."
            RunFailure.NotReady -> "The terminal app is not set up yet."
            RunFailure.TimedOut -> "Timed out."
            RunFailure.Unsupported -> "This build cannot run code."
            else -> result.stderr.ifBlank { fallback }
        }
        append(ConsoleLine.Error(message))
    }

    private fun append(line: ConsoleLine) {
        // Bounded, because a program that prints in a loop should cost a scrollback rather
        // than the process.
        lines = (lines + line).takeLast(MAX_LINES)
    }

    suspend fun readiness(): Readiness = provider.readiness()

    suspend fun isInstalled(runtime: Runtime): Boolean = provider.isInstalled(runtime)

    private companion object {
        const val MAX_LINES = 400
        const val CD_TIMEOUT_MS = 15_000L
    }
}
