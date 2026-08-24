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
     * Undo history. Snapshots are coalesced: a run of ordinary typing collapses into one
     * step, but a newline, a deletion or a pause starts a fresh one, which is what makes
     * undo feel like it steps through edits rather than characters.
     */
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastSnapshotAt = 0L
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
                    readOnly = target.length() > FileStore.EDIT_LIMIT_BYTES || !target.canWrite()
                    if (readOnly && target.length() > FileStore.EDIT_LIMIT_BYTES) {
                        message = "Large file - opened read-only"
                    }
                }
                .onFailure {
                    readOnly = true
                    message = it.message ?: "Could not read this file"
                }
            undoStack.clear(); redoStack.clear(); syncHistoryFlags()
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
        val edited = SmartEdit.onValueChange(current, next, language, autoPair)
        if (edited.text != current.text) {
            pushUndo(current, edited)
        }
        value = edited
        dirty = edited.text != savedText
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
            undoStack.addLast(current)
            redoStack.clear()
            trim()
            lastSnapshotAt = 0L
            syncHistoryFlags()
        }
        value = next
        dirty = next.text != savedText
    }

    private fun pushUndo(previous: TextFieldValue, next: TextFieldValue) {
        val now = System.currentTimeMillis()
        val bigChange = kotlin.math.abs(next.text.length - previous.text.length) > 1
        val newLine = next.text.length > previous.text.length &&
            next.text.getOrNull(next.selection.start - 1) == '\n'
        val stale = now - lastSnapshotAt > 700

        if (undoStack.isEmpty() || stale || bigChange || newLine) {
            undoStack.addLast(previous)
            trim()
        }
        lastSnapshotAt = now
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun trim() {
        while (undoStack.size > 120) undoStack.removeFirst()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(value)
        value = prev
        dirty = prev.text != savedText
        lastSnapshotAt = 0L
        syncHistoryFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(value)
        value = next
        dirty = next.text != savedText
        lastSnapshotAt = 0L
        syncHistoryFlags()
    }

    private fun syncHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
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

    fun consumeMessage() { message = null }
}
