package dev.hazel.code.ui.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hazel.code.data.FileStore
import dev.hazel.code.data.Language
import dev.hazel.code.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class ViewMode { EDIT, PREVIEW }

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var file by mutableStateOf<File?>(null)
        private set
    var value by mutableStateOf(TextFieldValue(""))
        private set
    var loading by mutableStateOf(true)
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

    var wordWrap by mutableStateOf(prefs.wordWrap)
        private set
    var autoPair by mutableStateOf(prefs.autoPair)
        private set
    var lineNumbers by mutableStateOf(prefs.lineNumbers)
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
     * Undo history for every file opened this session, and the current file's within it.
     *
     * The store lives on the view model, which the activity owns, so switching files
     * keeps each file's history and leaving the app discards all of them. See [UndoStore]
     * for why that lifetime is the one worth having.
     */
    private val histories = UndoStore()
    private var history = UndoHistory()
    private var savedText = ""

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    fun load(target: File) {
        if (file?.absolutePath == target.absolutePath && !loading) return
        file = target
        loading = true
        mode = if (Language.of(target.name) == Language.MARKDOWN) ViewMode.PREVIEW else ViewMode.EDIT
        modifiers = Modifiers()
        loadKeyOrder()
        // Until the read lands there is no buffer to undo into, and the outgoing file's
        // history must not answer for the incoming one.
        history = UndoHistory()
        syncHistoryFlags()
        viewModelScope.launch {
            val binary = FileStore.looksBinary(target)
            if (binary) {
                loading = false
                readOnly = true
                message = "Binary file - cannot be shown as text"
                value = TextFieldValue("")
                return@launch
            }
            FileStore.read(target)
                .onSuccess { text ->
                    savedText = text
                    value = TextFieldValue(text)
                    // Reopening a file this session picks its history back up, unless the
                    // file has changed since, in which case the store hands back a new one.
                    history = histories.of(target.absolutePath, text)
                    readOnly = target.length() > FileStore.EDIT_LIMIT_BYTES || !target.canWrite()
                    if (readOnly && target.length() > FileStore.EDIT_LIMIT_BYTES) {
                        message = "Large file - opened read-only"
                    }
                }
                .onFailure {
                    readOnly = true
                    message = it.message ?: "Could not read this file"
                }
            syncHistoryFlags()
            dirty = false
            // Same reasoning as the explorer: let the loader own at least a frame or two
            // instead of blinking.
            delay(80)
            loading = false
        }
    }

    fun onValueChange(next: TextFieldValue) {
        if (readOnly) return
        val current = value
        val edited = runCatching { SmartEdit.onValueChange(current, next, language, autoPair) }
            .getOrDefault(next)
        if (edited.text != current.text) {
            history.record(current, edited)
            syncHistoryFlags()
        }
        commit(edited)
    }

    /**
     * Applies a toolbar operation, always as its own undo step.
     *
     * The operations do index arithmetic against the buffer, so a bad edge case would
     * otherwise throw straight through composition and take the screen down. A failure
     * here leaves the text exactly as it was.
     */
    fun apply(op: (TextFieldValue) -> TextFieldValue) {
        if (readOnly) return
        val current = value
        val next = runCatching { op(current) }.getOrElse {
            message = "That did not work here"
            return
        }
        if (next.text != current.text) {
            history.recordDiscrete(current, next)
            syncHistoryFlags()
        }
        commit(next)
    }

    fun undo() {
        val previous = history.undo(value) ?: return
        commit(previous)
        syncHistoryFlags()
    }

    fun redo() {
        val next = history.redo(value) ?: return
        commit(next)
        syncHistoryFlags()
    }

    /**
     * Puts [next] in the buffer and tells the store what this file now holds, which is
     * what a later reopen compares against to decide whether its history still applies.
     */
    private fun commit(next: TextFieldValue) {
        value = next
        dirty = next.text != savedText
        file?.let { histories.noteText(it.absolutePath, next.text) }
    }

    private fun syncHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    fun save(onDone: (Boolean) -> Unit = {}) {
        val target = file ?: return
        if (readOnly || saving) return
        saving = true
        viewModelScope.launch {
            val text = value.text
            val result = FileStore.write(target, text)
            // A save that returns instantly reads as "nothing happened"; the loader needs
            // long enough to register as feedback.
            delay(220)
            saving = false
            result
                .onSuccess {
                    savedText = text
                    dirty = false
                    message = "Saved"
                    onDone(true)
                }
                .onFailure {
                    message = it.message ?: "Could not save"
                    onDone(false)
                }
        }
    }

    fun switchMode(m: ViewMode) { mode = m }

    fun toggleWrap() { wordWrap = !wordWrap; prefs.wordWrap = wordWrap }
    fun toggleAutoPair() { autoPair = !autoPair; prefs.autoPair = autoPair }
    fun toggleLineNumbers() { lineNumbers = !lineNumbers; prefs.lineNumbers = lineNumbers }
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
        const val MIN_PREVIEW_ZOOM = 60
        const val MAX_PREVIEW_ZOOM = 250
        const val PREVIEW_ZOOM_STEP = 10
    }
}
