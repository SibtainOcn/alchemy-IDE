package com.sibtainocn.alchemy.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sibtainocn.alchemy.data.AccessDenied
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.MediaIndex
import com.sibtainocn.alchemy.data.Prefs
import com.sibtainocn.alchemy.data.Search
import com.sibtainocn.alchemy.data.SearchKind
import com.sibtainocn.alchemy.data.SortBy
import com.sibtainocn.alchemy.data.Transfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * What cut or copy is holding, waiting for somewhere to land.
 *
 * A list rather than one entry: the clipboard is the same clipboard whether one thing or
 * forty was picked up, and a batch that arrived as a loop of single transfers could not
 * measure itself, could not answer a conflict once for all of it, and could not be
 * cancelled as a whole.
 */
data class Staged(val entries: List<Entry>, val transfer: Transfer) {
    constructor(entry: Entry, transfer: Transfer) : this(listOf(entry), transfer)

    val count: Int get() = entries.size

    /** What the clipboard strip calls what it is carrying. */
    val label: String
        get() = entries.singleOrNull()?.name ?: "$count items"
}

/** What a running batch is doing, as the dialog reporting it needs to read it. */
data class TransferJob(
    val kind: Kind,
    /** The file being written or removed at this instant. */
    val name: String?,
    /** Bytes for a copy or a move, items for a delete. */
    val done: Long,
    val total: Long,
    /** Bytes per second, once there is enough of a run to average over. */
    val speed: Long? = null,
    /** How many things the batch was asked to handle. */
    val count: Int = 1,
    /**
     * True once Hide has been tapped.
     *
     * The work carries on; only the dialog goes. A transfer is not a modal thing - it can
     * take minutes, and standing in front of the file list for all of them is the reason
     * people learn to distrust progress dialogs.
     */
    val hidden: Boolean = false,
) {
    enum class Kind { COPY, MOVE, DELETE }

    val fraction: Float?
        get() = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else null

    val verb: String
        get() = when (kind) {
            Kind.COPY -> "Copying"
            Kind.MOVE -> "Moving"
            Kind.DELETE -> "Deleting"
        }
}

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
    /** The batch in flight, or null when nothing is running. */
    val job: TransferJob? = null,
    /** Set while a transfer is stopped on a name that is already taken. */
    val ask: ConflictPrompt? = null,
    /** Absolute paths the user has pinned, wherever in the tree they live. */
    val pinned: Set<String> = emptySet(),
    /** True while rows are being picked rather than opened. */
    val selecting: Boolean = false,
    /** Absolute paths ticked in selection mode. */
    val selected: Set<String> = emptySet(),
    /** The chip in force, or null when the search is on names alone. */
    val searchKind: SearchKind? = null,
    /** What the walk has found so far, deepest folders last. */
    val results: List<Entry> = emptyList(),
    /** True while the tree is still being walked. */
    val searchRunning: Boolean = false,
    /** True when the walk stopped at the cap rather than at the end of the tree. */
    val searchTruncated: Boolean = false,
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

    /**
     * The ticked rows, in the order they are listed.
     *
     * Read from the visible list rather than kept as a second copy of the entries, so a
     * rename, a sort or a refresh cannot leave the selection describing files that are no
     * longer what it says they are.
     */
    val selection: List<Entry> get() = visible.filter { it.file.absolutePath in selected }

    /**
     * Bytes ticked, as far as a listing knows.
     *
     * A folder's own size is not the size of what is under it, and finding that out means
     * walking it - which is not something to do on every tap of a row. So folders count as
     * nothing here and the figure is honest about being a lower bound.
     */
    val selectedBytes: Long get() = selection.sumOf { if (it.isDir) 0L else it.sizeBytes }

    val allSelected: Boolean get() = visible.isNotEmpty() && selected.size >= visible.size

    /** True once the search has been given something to go on. */
    val searchAsked: Boolean get() = query.isNotBlank() || searchKind != null
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

    /** The batch in flight, held so Cancel has something to cancel. */
    private var transfer: Job? = null

    /** The walk in flight. A newer search cancels it where it stands. */
    private var searchJob: Job? = null

    // The running average behind the speed readout. Plain fields rather than state: they
    // are written from the worker thread on every report and read only to produce the one
    // number that does reach the UI.
    private var speedAt = 0L
    private var speedDone = 0L
    private var speed: Long? = null

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
            _state.update {
                it.copy(
                    dir = dir,
                    loading = true,
                    error = null,
                    denied = false,
                    // A selection names rows in the folder it was made in. Carrying it
                    // into the next one would leave paths ticked that are not on screen.
                    selecting = false,
                    selected = emptySet(),
                )
            }
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

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        search()
    }

    /** Picks a kind to search for, or drops the one in force. Tapping the same chip clears it. */
    fun setSearchKind(kind: SearchKind?) {
        _state.update { it.copy(searchKind = if (it.searchKind == kind) null else kind) }
        search()
    }

    fun setSearching(on: Boolean) {
        searchJob?.cancel()
        searchJob = null
        _state.update {
            it.copy(
                searching = on,
                query = "",
                searchKind = null,
                results = emptyList(),
                searchRunning = false,
                searchTruncated = false,
                // Picking and searching are two modes and only one can be on: the bar has
                // room for one question at a time.
                selecting = false,
                selected = emptySet(),
            )
        }
    }

    /**
     * Walks the current folder and everything under it.
     *
     * Debounced, because a search is started by every keystroke and the one being typed
     * over is worth nothing. Superseded rather than queued: the previous walk is cancelled
     * where it stands, which the file layer checks for on every directory.
     *
     * Results are published as they are found rather than at the end. On a large volume
     * the difference is a list that fills in front of you against a spinner that sits
     * there for ten seconds and then produces everything at once.
     */
    private fun search() {
        searchJob?.cancel()
        val s = _state.value
        if (!s.searching || !s.searchAsked) {
            _state.update { it.copy(results = emptyList(), searchRunning = false, searchTruncated = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searchRunning = true, results = emptyList(), searchTruncated = false) }
            val outcome = runCatching {
                Search.run(s.dir, s.query, s.searchKind, s.showHidden) { batch ->
                    _state.update { it.copy(results = batch) }
                }
            }
            // A cancelled walk has been replaced by a newer one, which owns the state now.
            outcome
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            results = result.entries,
                            searchRunning = false,
                            searchTruncated = result.truncated,
                        )
                    }
                }
                .onFailure { if (it !is CancellationException) fail(it) }
        }
    }

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
            .onSuccess { announce(listOf(it)); refresh(); onDone(it) }
            .onFailure { fail(it); onDone(null) }
    }

    fun createFolder(name: String) = viewModelScope.launch {
        FileStore.createDir(_state.value.dir, name)
            .onSuccess { announce(listOf(it)); refresh() }
            .onFailure { fail(it) }
    }

    fun rename(entry: Entry, name: String) = viewModelScope.launch {
        FileStore.rename(entry.file, name)
            .onSuccess { renamed ->
                // A pin follows the thing it was put on. Losing it on rename would be a
                // small betrayal of the one promise a pin makes.
                repin(entry.file.absolutePath, renamed.absolutePath)
                // Both names: the one that went and the one that arrived.
                announce(listOf(entry.file, renamed))
                refresh()
            }
            .onFailure { fail(it) }
    }

    fun delete(entry: Entry) = deleteAll(listOf(entry))

    /**
     * Removes a whole selection.
     *
     * Through the same batch engine as one item, because one item is a batch of one and a
     * second code path for it is a second place for this to go wrong. Progress counts
     * items rather than bytes, and a file that will not go is reported without stopping
     * the rest.
     */
    fun deleteAll(entries: List<Entry>) {
        if (entries.isEmpty()) return
        val targets = entries.map { it.file }
        run(TransferJob.Kind.DELETE, count = targets.size) {
            val result = FileStore.deleteAll(targets, onProgress = ::report)
            targets.forEach { repin(it.absolutePath, null) }
            announce(targets)
            result
        }
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

    fun stage(entry: Entry, transfer: Transfer) = stage(listOf(entry), transfer)

    /** Picks up a whole selection, and leaves selection mode: the choosing is done. */
    fun stage(entries: List<Entry>, transfer: Transfer) {
        if (entries.isEmpty()) return
        _state.update {
            it.copy(staged = Staged(entries, transfer), selecting = false, selected = emptySet())
        }
    }

    fun clearStaged() = _state.update { it.copy(staged = null) }

    /** Lands whatever is staged in the folder currently on screen. */
    fun paste() {
        val staged = _state.value.staged ?: return
        carryOut(staged, _state.value.dir)
    }

    /** Move, without the round trip through the clipboard: pick a folder, go. */
    fun moveTo(entry: Entry, destination: File) = moveTo(listOf(entry), destination)

    fun moveTo(entries: List<Entry>, destination: File) {
        if (entries.isEmpty()) return
        _state.update { it.copy(selecting = false, selected = emptySet()) }
        carryOut(Staged(entries, Transfer.MOVE), destination)
    }

    private fun carryOut(staged: Staged, destination: File) {
        val sources = staged.entries.map { it.file }
        val kind =
            if (staged.transfer == Transfer.MOVE) TransferJob.Kind.MOVE else TransferJob.Kind.COPY

        // Only a folder, or a selection of more than one thing, can produce a second
        // clash, so only those are offered the answer that applies to all of them.
        val repeatable = staged.entries.size > 1 || staged.entries.any { it.isDir }

        run(kind, count = sources.size) {
            // Parks the worker on a dialog and hands back whatever comes off it. The
            // finally matters: a prompt left on screen after its transfer ended could
            // never be answered by anything.
            val resolve: suspend (FileStore.Conflict) -> FileStore.Decision = { conflict ->
                val answer = CompletableDeferred<FileStore.Decision>()
                _state.update { it.copy(ask = ConflictPrompt(conflict, repeatable, answer)) }
                try {
                    answer.await()
                } finally {
                    _state.update { it.copy(ask = null) }
                }
            }

            val result = FileStore.transferAll(sources, destination, staged.transfer, ::report, resolve)
            // Both ends: what arrived, and for a move what is no longer where it was.
            announce(result.done + sources)
            // The clipboard empties either way. A failed paste that stayed armed would
            // invite the same attempt again, and the message has already said why it will
            // not work.
            _state.update { it.copy(staged = null) }
            result
        }
    }

    // ---- Running a batch ----

    /**
     * The one place a batch runs, whatever it is doing.
     *
     * Everything the three operations share lives here: the job that reports them, the
     * speed averaged out of the progress reports, the cancel that reaches the worker, the
     * refresh at the end, and the summary of whatever would not go. What the caller
     * supplies is the work itself.
     *
     * Held as a [Job] so Cancel has something to cancel. The copy loops check for
     * cancellation between slices, so the stop lands within a slice rather than at the end
     * of the file.
     */
    private fun run(
        kind: TransferJob.Kind,
        count: Int,
        block: suspend () -> FileStore.BatchResult,
    ) {
        if (_state.value.job != null) return
        speedAt = 0L
        speedDone = 0L
        speed = null
        _state.update {
            it.copy(
                job = TransferJob(kind, name = null, done = 0L, total = 0L, count = count),
                // The picking is over the moment something is being done with what was
                // picked. Leaving the ticks on would invite the same action twice.
                selecting = false,
                selected = emptySet(),
            )
        }
        transfer = viewModelScope.launch {
            val outcome = runCatching { block() }
            _state.update { it.copy(job = null, ask = null) }
            // Refreshed whichever way it went: a run stopped half way through still left
            // some of it on disk, and the list has to show what is actually there.
            refresh()
            outcome
                .onSuccess { summarise(it) }
                .onFailure { e ->
                    // Cancelling is a decision, not a fault, and needs no message. Neither
                    // does dismissing a conflict prompt, which is the same decision.
                    if (e is CancellationException || e is FileStore.TransferAborted) return@onFailure
                    fail(e)
                }
        }
    }

    /**
     * Turns a progress report into what the dialog shows.
     *
     * Called from the worker thread, already throttled by the file layer. The speed is
     * averaged rather than instantaneous: a figure recomputed from one 80ms window jumps
     * between numbers too fast to read, and the one thing a speed is for is estimating.
     */
    private fun report(p: FileStore.Progress) {
        val now = System.currentTimeMillis()
        if (speedAt == 0L) {
            speedAt = now
            speedDone = p.done
        } else {
            val elapsed = now - speedAt
            if (elapsed >= SPEED_WINDOW_MS) {
                val moved = p.done - speedDone
                val instant = if (moved > 0) moved * 1000L / elapsed else 0L
                speed = speed?.let { (it * 7 + instant * 3) / 10 } ?: instant
                speedAt = now
                speedDone = p.done
            }
        }
        _state.update { s ->
            val job = s.job ?: return@update s
            s.copy(job = job.copy(name = p.name ?: job.name, done = p.done, total = p.total, speed = speed))
        }
    }

    /** Abandons a running batch. What has already been written stays written. */
    fun cancelTransfer() {
        transfer?.cancel()
        transfer = null
    }

    /** Puts the progress dialog away without touching the work behind it. */
    fun hideProgress() = _state.update { it.copy(job = it.job?.copy(hidden = true)) }

    fun showProgress() = _state.update { it.copy(job = it.job?.copy(hidden = false)) }

    /**
     * Says what would not go, and nothing at all when everything did.
     *
     * One failure is named, because a name is something to act on. Several are counted,
     * because a message long enough to list them is one nobody reads.
     */
    private fun summarise(result: FileStore.BatchResult) {
        if (result.failures.isEmpty()) return
        val (file, cause) = result.failures.first()
        _state.update {
            it.copy(
                error = if (result.failures.size == 1) {
                    file.name + ": " + (cause.message ?: "could not be done")
                } else {
                    "${result.failures.size} items could not be done, starting with ${file.name}"
                }
            )
        }
    }

    /** Tells the rest of the device what changed on disk. */
    private fun announce(paths: Collection<File>) {
        val app = getApplication<Application>()
        viewModelScope.launch { MediaIndex.refresh(app, paths.distinct()) }
    }

    // ---- Selection ----

    fun setSelecting(on: Boolean) =
        _state.update { it.copy(selecting = on, selected = if (on) it.selected else emptySet()) }

    fun toggleSelected(entry: Entry) = _state.update {
        val path = entry.file.absolutePath
        val next = if (path in it.selected) it.selected - path else it.selected + path
        // Ticking a row is how selection mode is entered from a long press as well.
        it.copy(selecting = true, selected = next)
    }

    /** All or nothing, from the one control: whichever the current state is not. */
    fun toggleSelectAll() = _state.update {
        val all = it.visible.map { entry -> entry.file.absolutePath }.toSet()
        it.copy(selected = if (it.selected.size >= all.size) emptySet() else all)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun fail(t: Throwable) = _state.update { it.copy(error = t.message ?: "Something went wrong") }

    private companion object {
        /** How long a speed sample runs before it is folded into the average. */
        const val SPEED_WINDOW_MS = 400L

        /**
         * How long a keystroke waits before it becomes a search.
         *
         * Long enough that typing a word starts one walk rather than five, short enough
         * that the pause between finishing a word and looking up is not noticeable.
         */
        const val SEARCH_DEBOUNCE_MS = 220L
    }
}
