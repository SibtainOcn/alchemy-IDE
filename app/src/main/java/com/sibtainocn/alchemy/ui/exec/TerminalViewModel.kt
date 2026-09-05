package com.sibtainocn.alchemy.ui.exec

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sibtainocn.alchemy.data.CommandHistory
import com.sibtainocn.alchemy.data.Prefs
import com.sibtainocn.alchemy.exec.Console
import com.sibtainocn.alchemy.exec.ConsoleLine
import com.sibtainocn.alchemy.exec.Execution
import com.sibtainocn.alchemy.exec.ExecutionProvider
import com.sibtainocn.alchemy.exec.Readiness
import com.sibtainocn.alchemy.exec.RunFailure
import com.sibtainocn.alchemy.exec.RunRequest
import com.sibtainocn.alchemy.exec.Runtime
import com.sibtainocn.alchemy.exec.ShellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val prefs = Prefs(app)
    private val stored = CommandHistory(File(app.filesDir, "command-history.txt"))
    private var job: Job? = null

    val supported: Boolean get() = provider.supported

    var open by mutableStateOf(false)
        private set

    var lines by mutableStateOf(emptyList<ConsoleLine>())
        private set

    /**
     * The id of `lines[0]`; every later line is this plus its offset.
     *
     * The list is a window, not a log: it is trimmed from the front once it reaches
     * [MAX_LINES], so a row's position is not a name for it. Keying the transcript on
     * position instead meant that the first trim renamed every row on screen, and the
     * whole list was rebuilt on the frame a long run finished. Ids only ever go up, and
     * they survive a clear.
     */
    var firstLineId by mutableStateOf(0L)
        private set

    /** Where the next command will start. Absolute, and never shown in the prompt itself. */
    var directory by mutableStateOf("")
        private set

    var running by mutableStateOf(false)
        private set

    /** True while the channel is being checked, before the sheet is worth opening. */
    var checking by mutableStateOf(false)
        private set

    var fontSizeSp by mutableStateOf(prefs.terminalFontSp)
        private set

    /** Whether each command reports how long it took. */
    var showTimings by mutableStateOf(prefs.terminalTimings)
        private set

    fun setFontSize(sp: Int) {
        fontSizeSp = sp.coerceIn(8, 22)
        prefs.terminalFontSp = fontSizeSp
    }

    fun toggleTimings() {
        showTimings = !showTimings
        prefs.terminalTimings = showTimings
    }

    /**
     * Commands typed this session, oldest first, for walking back through.
     *
     * Only what was typed at the prompt. A file run from the Run button is not something
     * anyone wants to find in their history, and repeating the same command twice in a
     * row leaves one entry rather than two.
     */
    private val history = stored.load().toMutableList()

    /** Where the walk has got to, or -1 when not walking. */
    private var recallAt = -1

    val hasHistory: Boolean get() = history.isNotEmpty()

    /**
     * The command before the one recall last offered, or the most recent to begin with.
     *
     * Written into the prompt rather than run, so it can be edited first. Stops at the
     * oldest rather than wrapping round to the newest, because a list that loops gives no
     * sign that you have reached the end of it.
     */
    fun recallPrevious(): String? {
        if (history.isEmpty()) return null
        recallAt = if (recallAt < 0) history.lastIndex else (recallAt - 1).coerceAtLeast(0)
        return history[recallAt]
    }

    /**
     * The saved history, as a file to open.
     *
     * Shown in the editor rather than printed into the scrollback: it is a text file, this
     * is a text editor, and everything the editor already does with a file, from scrolling
     * to selecting to searching, is what someone looking at their own history wants.
     */
    /**
     * The history file, ready to be opened.
     *
     * Prepared here rather than by the caller so the editor is never handed a path that
     * does not exist yet. The write is small and only ever happens on a file that is
     * missing or empty.
     */
    fun historyFile(): File = stored.ensureExists()

    /** Forgets everything typed, on disk and in the walk-back. */
    fun clearHistory() {
        history.clear()
        recallAt = -1
        viewModelScope.launch { withContext(Dispatchers.IO) { stored.clear() } }
        append(ConsoleLine.Note("command history cleared"))
    }

    /** Everything on screen, as text, for copying a whole session out at once. */
    fun transcript(): String = lines.joinToString("\n") { line ->
        when (line) {
            is ConsoleLine.Typed -> "$ ${line.command}"
            is ConsoleLine.Output -> line.text
            is ConsoleLine.Error -> line.text
            is ConsoleLine.Note -> line.text
            is ConsoleLine.Timing -> line.text
        }
    }

    /**
     * Shows a log produced somewhere else, such as an install.
     *
     * Appended to the same scrollback rather than shown in a place of its own: the output
     * of `pkg install` is terminal output, and the terminal is where someone will look
     * for it again afterwards.
     */
    fun showLog(log: List<ConsoleLine>) {
        if (log.isEmpty()) return
        appendAll(log)
        open = true
    }

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
        // Anything typed ahead goes with it. Stopping means stopping, and running the
        // next line into a runner still busy with the last one is not what was asked for.
        queued = null
        append(ConsoleLine.Note("stopped waiting. The command may still be running in Termux."))
    }

    fun clear() {
        firstLineId += lines.size
        lines = emptyList()
        if (directory.isNotEmpty()) append(ConsoleLine.Note("in $directory"))
    }

    /**
     * Typed while something was still running, and run the moment it is not.
     *
     * The prompt no longer goes dead while a command is in flight, because a field that is
     * disabled and re-enabled hands the keyboard back and forth and the screen flickers
     * for as long as the command takes. Typing ahead is what a terminal is for; one line
     * is held rather than a list of them, since the second line typed replaces the first
     * in every shell that offers this.
     */
    private var queued: String? = null

    /** Whatever was typed at the prompt. */
    fun submit(input: String) {
        val command = input.trim()
        if (command.isEmpty()) return
        if (running) {
            queued = command
            append(ConsoleLine.Note("queued: $command"))
            return
        }
        start(command)
    }

    private fun start(command: String) {
        if (history.lastOrNull() != command) history += command
        recallAt = -1
        // Written on its own thread: the prompt should not wait on a file to accept a
        // command, and losing the last line of history to a crash costs nothing.
        viewModelScope.launch { withContext(Dispatchers.IO) { stored.add(command) } }
        append(ConsoleLine.Typed(command))

        val cd = Console.cdTarget(command)
        if (cd != null) changeDirectory(cd) else execute(command)
    }

    /** Runs whatever was typed ahead, once the runner is free again. */
    private fun runQueued() {
        val next = queued ?: return
        queued = null
        start(next)
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
            runQueued()
        }
    }

    private fun execute(command: String) {
        running = true
        val startedAt = System.currentTimeMillis()

        job = viewModelScope.launch {
            val result = provider.run(RunRequest(command = command, workingDir = directory))
            running = false
            report(result, startedAt)
            runQueued()
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
    private fun report(result: com.sibtainocn.alchemy.exec.RunResult, startedAt: Long) {
        // Collected and added once. Everything a finished run has to say lands on the same
        // frame, so the list settles at its final height in one step rather than growing
        // under the reader three times in a row.
        val batch = buildList {
            if (result.stdout.isNotEmpty()) add(ConsoleLine.Output(result.stdout.trimEnd('\n')))
            if (result.stderr.isNotEmpty()) add(ConsoleLine.Error(result.stderr.trimEnd('\n')))
            if (result.stdoutTruncated || result.stderrTruncated) {
                add(ConsoleLine.Note(truncationNote(result)))
            }
            if (result.failure != null) {
                add(ConsoleLine.Error(failureMessage(result, fallback = "could not run")))
            } else {
                add(
                    ConsoleLine.Timing(
                        Console.summarise(result.exitCode, System.currentTimeMillis() - startedAt)
                    )
                )
            }
        }
        appendAll(batch)
    }

    /** Says how much was lost, rather than letting a cut-off traceback look complete. */
    private fun truncationNote(result: com.sibtainocn.alchemy.exec.RunResult): String {
        val shown = result.stdout.length + result.stderr.length
        val written = result.stdoutFullLength + result.stderrFullLength
        return "output was too large to pass back whole: showing $shown of $written characters. " +
            "Redirect it to a file to see all of it."
    }

    private fun reportFailure(result: com.sibtainocn.alchemy.exec.RunResult, fallback: String) {
        append(ConsoleLine.Error(failureMessage(result, fallback)))
    }

    private fun failureMessage(
        result: com.sibtainocn.alchemy.exec.RunResult,
        fallback: String,
    ): String = when (result.failure) {
        RunFailure.ExternalAppsDisabled ->
            "The terminal app is refusing commands. Run the setup again from the menu."
        RunFailure.NotReady -> "The terminal app is not set up yet."
        RunFailure.TimedOut -> "Timed out."
        RunFailure.Unsupported -> "This build cannot run code."
        else -> result.stderr.ifBlank { fallback }
    }

    private fun append(line: ConsoleLine) = appendAll(listOf(line))

    /**
     * Adds to the scrollback in one go, and says how much fell off the front.
     *
     * Bounded, because a program that prints in a loop should cost a scrollback rather
     * than the process. Written as one assignment however many lines arrive: a run that
     * prints output, a truncation note and a timing is one recomposition rather than
     * three, which is what stops the view flinching as a command finishes.
     */
    private fun appendAll(incoming: List<ConsoleLine>) {
        if (incoming.isEmpty()) return
        val combined = lines + incoming
        val dropped = (combined.size - MAX_LINES).coerceAtLeast(0)
        if (dropped > 0) firstLineId += dropped
        lines = if (dropped > 0) combined.subList(dropped, combined.size).toList() else combined
    }

    suspend fun readiness(): Readiness = provider.readiness()

    suspend fun isInstalled(runtime: Runtime): Boolean = provider.isInstalled(runtime)

    private companion object {
        const val MAX_LINES = 400
        const val CD_TIMEOUT_MS = 15_000L
    }
}
