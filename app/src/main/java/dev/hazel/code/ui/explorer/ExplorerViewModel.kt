package dev.hazel.code.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hazel.code.data.AccessDenied
import dev.hazel.code.data.Entry
import dev.hazel.code.data.FileStore
import dev.hazel.code.data.Prefs
import dev.hazel.code.data.SortBy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
) {
    val visible: List<Entry>
        get() = if (query.isBlank()) entries
        else entries.filter { it.name.contains(query.trim(), ignoreCase = true) }

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
        FileStore.rename(entry.file, name).onSuccess { refresh() }.onFailure { fail(it) }
    }

    fun delete(entry: Entry) = viewModelScope.launch {
        FileStore.delete(entry.file).onSuccess { refresh() }.onFailure { fail(it) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun fail(t: Throwable) = _state.update { it.copy(error = t.message ?: "Something went wrong") }
}
