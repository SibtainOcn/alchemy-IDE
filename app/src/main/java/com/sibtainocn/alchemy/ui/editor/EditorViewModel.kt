package com.sibtainocn.alchemy.ui.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.data.Prefs
import com.sibtainocn.alchemy.ui.editor.sora.BufferStore
import com.sibtainocn.alchemy.ui.editor.sora.Caret
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

enum class ViewMode { EDIT, PREVIEW }

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var file by mutableStateOf<File?>(null)
        private set

    /**
     * The buffer on screen.
     *
     * The editor is handed this object rather than its text, so it is the same buffer the
     * store is holding and the same one carrying the undo stack. Null only before the
     * first file has been read.
     */
    var content by mutableStateOf<Content?>(null)
        private set

    var loading by mutableStateOf(true)
        private set

    /**
     * Fraction of the file decoded so far, 0..1. Only meaningful while [loading], and only
     * advanced for files at or above [FileStore.PROGRESS_FLOOR_BYTES].
     */
    var loadProgress by mutableStateOf(0f)
        private set

    /** Size of the file being opened, in bytes. Drives the choice of loader. */
    var loadingBytes by mutableStateOf(0L)
        private set
    var saving by mutableStateOf(false)
        private set
    var readOnly by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var mode by mutableStateOf(ViewMode.EDIT)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var caret by mutableStateOf(Caret(1, 1, 0))
        private set

    /**
     * Bumped on every change to the buffer, whether typed, undone or redone.
     *
     * Anything that would otherwise have to copy the whole document to notice a change
     * watches this instead - the Markdown preview being the one that matters, since
     * rendering it means reading the buffer out as a string.
     */
    var revision by mutableStateOf(0)
        private set

    var wordWrap by mutableStateOf(prefs.wordWrap)
        private set
    var autoPair by mutableStateOf(prefs.autoPair)
        private set
    var lineNumbers by mutableStateOf(prefs.lineNumbers)
        private set
    var keyBar by mutableStateOf(prefs.keyBar)
        private set
    var fontSizeSp by mutableStateOf(prefs.fontSizeSp)
        private set
    var previewZoomPct by mutableStateOf(prefs.previewZoomPct)
        private set

    val language: Language get() = file?.let { Language.of(it.name) } ?: Language.PLAIN

    /** Live key-bar modifier latches. Reset whenever a different file is opened. */
    var modifiers by mutableStateOf(Modifiers())
        private set

    /** The user's dragged key order for the current language. */
    var keyOrder by mutableStateOf(emptyList<String>())
        private set

    fun updateModifiers(next: Modifiers) { modifiers = next }

    fun updateKeyOrder(order: List<String>) {
        keyOrder = order
        prefs.setKeyOrder(language.name, order)
    }

    private fun loadKeyOrder() {
        keyOrder = prefs.keyOrder(language.name)
    }

    /**
     * Every buffer opened this session.
     *
     * Session-lived, like the strip that lists them: a record of where you have been while
     * the app has been open, not a project the app is managing.
     */
    private val buffers = BufferStore(maxFiles = MAX_TABS, budgetChars = MAX_HELD_CHARS)

    /**
     * Every file opened this session, oldest first, as the strip under the bar lists them.
     */
    var tabs by mutableStateOf(emptyList<File>())
        private set

    /**
     * Open paths in the order they were last shown, least recent first.
     *
     * Separate from [tabs], which stays in the order files were opened: a strip that
     * reordered itself every time you looked at something would move the tab you were
     * aiming for out from under your finger.
     */
    private val visits = LinkedHashSet<String>()

    /** The read in flight, held so a newer open can cancel it. */
    private var loadJob: Job? = null

    fun load(target: File) {
        if (file?.absolutePath == target.absolutePath && !loading) return
        file = target
        rememberTab(target)
        loading = true
        loadProgress = 0f
        loadingBytes = target.length()
        mode = if (Language.of(target.name) == Language.MARKDOWN) ViewMode.PREVIEW else ViewMode.EDIT
        modifiers = Modifiers()
        loadKeyOrder()
        // Supersede any read still in flight. Without this, backing out of a large file
        // and opening another leaves the first decode running to completion and racing the
        // second for `content`.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val path = target.absolutePath
            if (FileStore.looksBinary(target)) {
                readOnly = true
                message = "Binary file - cannot be shown as text"
                content = Content("")
                loading = false
                return@launch
            }
            FileStore.read(target) { loadProgress = it }
                .onSuccess { text ->
                    content = buffers.open(path, text)
                    readOnly = target.length() > FileStore.EDIT_LIMIT_BYTES || !target.canWrite()
                    if (readOnly && target.length() > FileStore.EDIT_LIMIT_BYTES) {
                        message = "Large file - opened read-only"
                    }
                }
                .onFailure {
                    readOnly = true
                    message = it.message ?: "Could not read this file"
                    content = Content("")
                }
            refreshFlags()
            // Same reasoning as the explorer: let the loader own at least a frame or two
            // instead of blinking.
            delay(80)
            loading = false
        }
    }

    /**
     * Called by the editor whenever the buffer changes, from typing, undo or redo alike.
     *
     * One signal recomputes all three answers. [dirty] is the one worth reading twice:
     * `BufferStore` settles it on length almost every time, and only compares the text
     * when the buffer has come back to the length of what is on disk - which is exactly
     * the case that has to be right, because it is undoing back to a saved file.
     */
    fun onContentChanged() {
        revision++
        refreshFlags()
    }

    fun onCaret(next: Caret) { caret = next }

    private fun refreshFlags() {
        val path = file?.absolutePath
        dirty = path != null && !readOnly && buffers.isDirty(path)
        canUndo = content?.canUndo() == true
        canRedo = content?.canRedo() == true
    }

    // ---- Tabs ----

    /** True when [target] holds work that is not on disk, whether or not it is on screen. */
    fun hasUnsavedWork(target: File): Boolean = buffers.isDirty(target.absolutePath)

    /**
     * Every open tab holding work that is not on disk.
     *
     * [dirty] answers only for the file on screen, so it cannot gate leaving the editor:
     * a buffer edited and then switched away from is still unwritten, and closing on that
     * basis discards it without asking.
     */
    fun unsavedTabs(): List<File> = tabs.filter { hasUnsavedWork(it) }

    /**
     * Throws away every unwritten buffer, and the tabs that were holding them.
     *
     * What Discard means. Leaving the tabs behind left the strip listing files whose edits
     * had just been destroyed, so re-opening one showed the version on disk under a tab
     * still marked as carrying work - and the next prompt asked about files that had
     * nothing left to lose.
     */
    fun discardUnsaved() {
        val doomed = unsavedTabs()
        if (doomed.isEmpty()) return
        doomed.forEach { buffers.forget(it.absolutePath) }
        val gone = doomed.map { it.absolutePath }.toSet()
        tabs = tabs.filterNot { it.absolutePath in gone }
        visits.removeAll(gone)
        if (file?.absolutePath in gone) file = null
        refreshFlags()
    }

    /**
     * Closes a tab and says what should be shown instead.
     *
     * Returns the file to move to, or null when that was the last one and the editor has
     * nothing left to hold. Closing throws away the tab's buffer, which is what closing
     * something means; the screen asks first.
     */
    fun closeTab(target: File): File? {
        val index = tabs.indexOfFirst { it.absolutePath == target.absolutePath }
        if (index < 0) return file
        val wasCurrent = file?.absolutePath == target.absolutePath
        buffers.forget(target.absolutePath)
        visits.remove(target.absolutePath)
        // Forget that this is what is being held, too. The buffer has gone, so opening the
        // same path again has to read it and register a tab for it - and [load] answers
        // "already showing that" if this still names it. Closing the open file's tab from
        // the explorer, where nothing navigates afterwards, is how that is reached.
        if (wasCurrent) file = null
        val remaining = tabs.filterIndexed { i, _ -> i != index }
        tabs = remaining
        if (!wasCurrent) return file
        // The one on screen went: fall to its left neighbour, or to the new first when it
        // was already leftmost.
        return remaining.getOrNull(index - 1) ?: remaining.firstOrNull()
    }

    private fun rememberTab(target: File) {
        val path = target.absolutePath
        // Re-inserted either way: opening a file again is what makes it recent, whether or
        // not the strip already lists it.
        visits.remove(path)
        visits.add(path)
        if (tabs.none { it.absolutePath == path }) tabs = tabs + target
        // The store drops what it can no longer hold; the strip follows it, so a tab never
        // survives the buffer behind it.
        tabs = tabs.filter { buffers.holds(it.absolutePath) || it.absolutePath == path }
        visits.retainAll(tabs.map { it.absolutePath }.toSet())
    }

    fun undo() {
        content?.takeIf { it.canUndo() }?.undo()
        onContentChanged()
    }

    fun redo() {
        content?.takeIf { it.canRedo() }?.redo()
        onContentChanged()
    }

    fun save(onDone: (Boolean) -> Unit = {}) {
        val target = file ?: return
        val buffer = content ?: return
        if (readOnly || saving) return
        saving = true
        viewModelScope.launch {
            val text = buffer.toString()
            val result = FileStore.write(target, text)
            // A save that returns instantly reads as "nothing happened"; the loader needs
            // long enough to register as feedback.
            delay(220)
            saving = false
            result
                .onSuccess {
                    buffers.markSaved(target.absolutePath, text)
                    refreshFlags()
                    message = "Saved"
                    onDone(true)
                }
                .onFailure {
                    message = it.message ?: "Could not save"
                    onDone(false)
                }
        }
    }

    /**
     * Writes every buffer holding work that is not on disk, whether or not it is on screen.
     *
     * The files are independent of one another and somebody is waiting on all of them, so
     * they go at once rather than one after another: three writes take as long as the
     * slowest, not as long as the sum. Only the bookkeeping comes back to this thread, and
     * only for the ones that landed - a buffer whose write failed is still unsaved, and
     * marking it otherwise would lose it at the next prompt.
     *
     * [onDone] is told whether all of them were written, which is what a caller about to
     * close the app needs to know.
     */
    fun saveAll(onDone: (Boolean) -> Unit = {}) = write(buffers.unsaved(), onDone)

    /**
     * Writes one tab, on screen or not.
     *
     * [save] can only write the file being looked at, which is no use to a tab being closed
     * from the strip while another one is open.
     */
    fun saveTab(target: File, onDone: (Boolean) -> Unit = {}) {
        val path = target.absolutePath
        val text = buffers.unsavedText(path)
        write(if (text == null) emptyList() else listOf(path to text), onDone)
    }

    private fun write(pending: List<Pair<String, String>>, onDone: (Boolean) -> Unit) {
        if (pending.isEmpty()) {
            onDone(true)
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            val results = coroutineScope {
                pending.map { (path, text) ->
                    async { Triple(path, text, FileStore.write(File(path), text).isSuccess) }
                }.awaitAll()
            }
            results.forEach { (path, text, written) -> if (written) buffers.markSaved(path, text) }
            // Same reasoning as the single save: a write that returns instantly reads as
            // nothing having happened.
            delay(220)
            saving = false
            refreshFlags()
            val failed = results.count { !it.third }
            message = when (failed) {
                0 -> if (results.size == 1) "Saved" else "Saved ${results.size} files"
                1 -> "One file could not be saved"
                else -> "$failed files could not be saved"
            }
            onDone(failed == 0)
        }
    }

    fun switchMode(m: ViewMode) { mode = m }

    fun toggleWrap() { wordWrap = !wordWrap; prefs.wordWrap = wordWrap }
    fun toggleAutoPair() { autoPair = !autoPair; prefs.autoPair = autoPair }
    fun toggleLineNumbers() { lineNumbers = !lineNumbers; prefs.lineNumbers = lineNumbers }
    fun toggleKeyBar() { keyBar = !keyBar; prefs.keyBar = keyBar }
    fun setFontSize(sp: Int) {
        fontSizeSp = sp.coerceIn(9, 26)
        prefs.fontSizeSp = fontSizeSp
    }

    /** Zooms the rendered preview. Steps of ten read as a zoom; single percent does not. */
    fun setPreviewZoom(pct: Int) {
        previewZoomPct = pct.coerceIn(MIN_PREVIEW_ZOOM, MAX_PREVIEW_ZOOM)
        prefs.previewZoomPct = previewZoomPct
    }

    fun consumeMessage() { message = null }

    companion object {
        /**
         * How many files the strip will carry.
         *
         * A tab is a path and a name, so the strip itself costs nothing worth counting;
         * this is where a row of them stops being something you can aim at on a phone.
         * What actually has to be bounded is the memory behind them, and that is
         * [MAX_HELD_CHARS].
         */
        const val MAX_TABS = 20

        /**
         * Characters held across every open buffer. Roughly 8 MB of UTF-16.
         *
         * A buffer holding unsaved work is never dropped to honour this, so it is a
         * ceiling on what can be discarded rather than on what can be kept.
         */
        const val MAX_HELD_CHARS = 4_000_000

        const val MIN_PREVIEW_ZOOM = 60
        const val MAX_PREVIEW_ZOOM = 250
        const val PREVIEW_ZOOM_STEP = 10
    }
}
