package com.sibtainocn.alchemy.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sibtainocn.alchemy.data.AccessDenied
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.Prefs
import com.sibtainocn.alchemy.data.SortBy
import com.sibtainocn.alchemy.data.Transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** An entry picked up by cut or copy, waiting for somewhere to land. */
data class Staged(val entry: Entry, val transfer: Transfer)

/**
 * A name clash the transfer has stopped on, and the reply it is waiting for.
 *
 * The worker thread is parked inside the copy until [reply] is called, which is what
 * lets the answer be a dialog rather than a policy decided in advance.
 */
class ConflictPrompt(
    val conflict: FileStore.Conflict,
    /** True when this run could hit more clashes, which is what makes "all" worth offering. */
    val repeatable: Boolean,
    private val answer: CompletableDeferred<FileStore.Decision>,
) {
    fun reply(resolution: FileStore.Resolution, all: Boolean = false) {
        answer.complete(FileStore.Decision(resolution, all))
    }
}

data class ExplorerState(
    val dir: File = FileStore.storageRoot,
    val entries: List<Entry> = emptyList(),
    val loading: Boolean = true,
    val query: String = "",
    val searching: Boolean = false,
    val sortBy: SortBy = SortBy.MODIFIED,
    val sortDescending: Boolean = true,
    val showHidden: Boolean = false,
    val error: String? = null,
    /** Set when Android refused to list this folder, as opposed to it being empty. */
    val denied: Boolean = false,
    /** What cut or copy is holding, or null when nothing is waiting to be pasted. */
    val staged: Staged? = null,
    /** A transfer in flight, as the line to show while it runs. */
    val working: String? = null,
    /** 0f..1f while a transfer reports its size, null while it cannot be measured. */
    val progress: Float? = null,
    /** Set while a transfer is stopped on a name that is already taken. */
    val ask: ConflictPrompt? = null,
    /** Absolute paths the user has pinned, wherever in the tree they live. */
    val pinned: Set<String> = emptySet(),
) {
    val visible: List<Entry>
        get() {
            val matched = if (query.isBlank()) entries
            else entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (pinned.isEmpty()) return matched
            // Pinned rows lead, and inside each group the folder's own order is kept, so
            // a pin lifts a row without shuffling anything around it.
            val (top, rest) = matched.partition { it.file.absolutePath in pinned }
            return top + rest
        }

    val atRoot: Boolean
        get() = dir.absolutePath == FileStore.storageRoot.absolutePath || dir.parentFile == null
}

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(
        ExplorerState(
            dir = prefs.lastDir?.let(::File)?.takeIf { it.isDirectory } ?: FileStore.storageRoot,
            sortBy = prefs.sortBy,
            sortDescending = prefs.sortDescending,
            showHidden = prefs.showHidden,
            pinned = prefs.pinned,
        )
    )
    val state: StateFlow<ExplorerState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() = load(_state.value.dir)

    fun open(dir: File) {
        if (!dir.isDirectory) return
        _state.update { it.copy(query = "", searching = false) }
        load(dir)
    }

    fun up(): Boolean {
        val s = _state.value
        if (s.searching) { setSearching(false); return true }
        if (s.atRoot) return false
        s.dir.parentFile?.let { load(it) }
        return true
    }

    fun jumpTo(dir: File) = open(dir)

    private fun load(dir: File) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(dir = dir, loading = true, error = null, denied = false) }
            prefs.lastDir = dir.absolutePath
            val s = _state.value
            val items = runCatching {
                FileStore.list(dir, s.showHidden, s.sortBy, s.sortDescending)
            }
            // A directory listing usually returns instantly. Holding the loader for a
            // beat stops it flashing in and out as a one-frame artefact.
            delay(90)
            _state.update {
                items.fold(
                    onSuccess = { list -> it.copy(entries = list, loading = false, denied = false) },
                    onFailure = { e ->
                        // A refusal gets its own pane with a route to Settings; anything
                        // else is a transient error and belongs in a snackbar.
                        val refused = e is AccessDenied
                        it.copy(
                            entries = emptyList(),
                            loading = false,
                            denied = refused,
                            error = if (refused) null else e.message ?: "Cannot read this folder",
                        )
                    },
                )
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun setSearching(on: Boolean) =
        _state.update { it.copy(searching = on, query = if (on) it.query else "") }

    fun setSort(by: SortBy, descending: Boolean) {
        prefs.sortBy = by
        prefs.sortDescending = descending
        _state.update { it.copy(sortBy = by, sortDescending = descending) }
        refresh()
    }

    fun toggleHidden() {
        val next = !_state.value.showHidden
        prefs.showHidden = next
        _state.update { it.copy(showHidden = next) }
        refresh()
    }

    fun createFile(name: String, onDone: (File?) -> Unit) = viewModelScope.launch {
        FileStore.createFile(_state.value.dir, name)
            .onSuccess { refresh(); onDone(it) }
            .onFailure { fail(it); onDone(null) }
    }

    fun createFolder(name: String) = viewModelScope.launch {
        FileStore.createDir(_state.value.dir, name)
            .onSuccess { refresh() }
            .onFailure { fail(it) }
    }

    fun rename(entry: Entry, name: String) = viewModelScope.launch {
        FileStore.rename(entry.file, name)
            .onSuccess { renamed ->
                // A pin follows the thing it was put on. Losing it on rename would be a
                // small betrayal of the one promise a pin makes.
                repin(entry.file.absolutePath, renamed.absolutePath)
                refresh()
            }
            .onFailure { fail(it) }
    }

    fun delete(entry: Entry) = viewModelScope.launch {
        FileStore.delete(entry.file)
            .onSuccess { repin(entry.file.absolutePath, null); refresh() }
            .onFailure { fail(it) }
    }

    // ---- Pins ----

    /**
     * Lifts an entry to the top of its folder, or puts it back.
     *
     * Kept in the app's own preferences rather than worked out from the filesystem, which
     * has nowhere to record it. One read at startup, one write per tap, and nothing to
     * recompute when a folder is opened.
     */
    fun togglePin(entry: Entry) {
        val path = entry.file.absolutePath
        val next = _state.value.pinned.let { if (path in it) it - path else it + path }
        prefs.pinned = next
        _state.update { it.copy(pinned = next) }
    }

    /** Moves a pin to a new path, or drops it when [to] is null. */
    private fun repin(from: String, to: String?) {
        val current = _state.value.pinned
        if (from !in current) return
        val next = if (to == null) current - from else current - from + to
        prefs.pinned = next
        _state.update { it.copy(pinned = next) }
    }

    // ---- Cut, copy, paste ----

    fun stage(entry: Entry, transfer: Transfer) =
        _state.update { it.copy(staged = Staged(entry, transfer)) }

    fun clearStaged() = _state.update { it.copy(staged = null) }

    /** Lands whatever is staged in the folder currently on screen. */
    fun paste() {
        val staged = _state.value.staged ?: return
        carryOut(staged, _state.value.dir)
    }

    /** Move, without the round trip through the clipboard: pick a folder, go. */
    fun moveTo(entry: Entry, destination: File) =
        carryOut(Staged(entry, Transfer.MOVE), destination)

    private fun carryOut(staged: Staged, destination: File) = viewModelScope.launch {
        // A move inside one volume returns almost instantly, but a copy across one does
        // not, and the folder has to say that something is happening to it.
        val verb = if (staged.transfer == Transfer.MOVE) "Moving " else "Copying "
        _state.update { it.copy(working = verb + staged.entry.name, progress = null) }

        // Only a folder can produce a second clash, so only a folder is offered the
        // choice that applies to all of them.
        val repeatable = staged.entry.isDir

        // Parks the worker on a dialog and hands back whatever comes off it. The finally
        // matters: a prompt left on screen after its transfer ended could never be
        // answered by anything.
        val resolve: suspend (FileStore.Conflict) -> FileStore.Decision = { conflict ->
            val answer = CompletableDeferred<FileStore.Decision>()
            _state.update { it.copy(ask = ConflictPrompt(conflict, repeatable, answer)) }
            try {
                answer.await()
            } finally {
                _state.update { it.copy(ask = null) }
            }
        }

        // Called from the IO thread doing the work, already throttled by FileStore. A
        // StateFlow update is safe from there and is the whole of what it costs.
        val onProgress: (FileStore.Progress) -> Unit = { p ->
            _state.update { it.copy(progress = p.fraction) }
        }

        val result = when (staged.transfer) {
            Transfer.MOVE -> FileStore.moveInto(staged.entry.file, destination, onProgress, resolve)
            Transfer.COPY -> FileStore.copyInto(staged.entry.file, destination, onProgress, resolve)
        }

        // The clipboard empties either way. A failed paste that stayed armed would invite
        // the same attempt again, and the snackbar has already said why it will not work.
        _state.update { it.copy(working = null, progress = null, staged = null, ask = null) }

        // Refreshed whichever way it went: a run stopped half way through a folder still
        // left some of it on disk, and the list has to show what is actually there.
        refresh()
        result.onFailure { e ->
            // Dismissing the prompt is a decision, not a fault, and needs no snackbar.
            if (e !is FileStore.TransferAborted) fail(e)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun fail(t: Throwable) = _state.update { it.copy(error = t.message ?: "Something went wrong") }
}
