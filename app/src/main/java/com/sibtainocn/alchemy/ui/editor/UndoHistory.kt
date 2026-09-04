package com.sibtainocn.alchemy.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Undo and redo, stored as edits rather than as copies of the file.
 *
 * A snapshot history costs the size of the document per step, which made depth a function
 * of file size: 120 snapshots of a 900 KB file is 208 MB and an out-of-memory kill, and
 * capping the memory instead bought a 2 MB file about seven steps. Storing what changed
 * costs the size of the *edit* - a keystroke is a keystroke whether the file around it is
 * 4 KB or 2 MB - so depth stops being a memory question and becomes a product one. This
 * is how Vim, Emacs, VS Code, IntelliJ and Compose's own text field all do it.
 *
 * An edit is a replacement: at some offset, [Edit.removed] became [Edit.inserted]. Undo
 * applies it backwards, redo forwards, and each carries the selection either side so the
 * caret lands where it was rather than at the end of the change.
 *
 * Runs of typing are merged into the edit in progress, so undo steps through words and
 * lines rather than characters. A pause, a line break, a change of direction or a jump
 * elsewhere in the file all close the run.
 */
class UndoHistory(
    /** Steps kept. Deep enough to be worth having; deltas make the count the real limit. */
    private val maxEntries: Int = 500,
    /** Characters of edit text held across both stacks - not characters of document. */
    private val maxChars: Int = 300_000,
    /** Always undoable at least this far, however large the edits are. */
    private val minEntries: Int = 1,
    private val coalesceWindowMs: Long = 700,
    /** How long a single merged run may grow before a new step is started. */
    private val maxRunChars: Int = 4_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * One edit: at [at], [removed] became [inserted].
     *
     * Either side may be empty - an insertion removes nothing, a deletion inserts nothing
     * - and both are set when text was replaced.
     */
    data class Edit(
        val at: Int,
        val removed: String,
        val inserted: String,
        val before: TextRange,
        val after: TextRange,
    ) {
        /** What this step costs to keep. */
        val chars: Int get() = removed.length + inserted.length
    }

    companion object {
        /**
         * The single replacement that turns [before] into [after], or null when they are
         * the same text.
         *
         * Common ends are skipped from both directions, so what is left is the run that
         * actually changed. That is not a minimal diff in the Myers sense and does not
         * need to be: one edit produced this change, and the shortest replacement that
         * reproduces it is the one worth storing.
         */
        fun between(before: TextFieldValue, after: TextFieldValue): Edit? {
            val old = before.text
            val new = after.text
            if (old == new) return null

            val shortest = minOf(old.length, new.length)
            var head = 0
            while (head < shortest && old[head] == new[head]) head++

            var tail = 0
            while (tail < shortest - head && old[old.length - 1 - tail] == new[new.length - 1 - tail]) tail++

            return Edit(
                at = head,
                removed = old.substring(head, old.length - tail),
                inserted = new.substring(head, new.length - tail),
                before = before.selection,
                after = after.selection,
            )
        }
    }

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()

    /** When the run in progress was last extended, or null when no run is open. */
    private var openedAt: Long? = null
    private var held = 0

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Characters of edit text held across both stacks, carried as a running total rather
     * than summed when asked: the budget is checked on every push.
     */
    val heldChars: Int get() = held

    val depth: Int get() = undoStack.size

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        held = 0
        openedAt = null
    }

    /**
     * Records the edit that turned [previous] into [next], merging it into the run in
     * progress where it continues one.
     */
    fun record(previous: TextFieldValue, next: TextFieldValue) {
        val edit = between(previous, next) ?: return
        val at = now()
        val open = openedAt
        val last = undoStack.lastOrNull()
        val merged = if (open != null && last != null && at - open <= coalesceWindowMs) {
            merge(last, edit)
        } else {
            null
        }

        if (merged != null) {
            held += merged.chars - undoStack.last().chars
            undoStack[undoStack.lastIndex] = merged
        } else {
            undoStack.addLast(edit)
            held += edit.chars
        }
        openedAt = at
        clearRedo()
        enforceBudget()
    }

    /**
     * Records an edit as its own step, whatever came before it. Used for whole operations
     * - duplicate a line, toggle a comment, paste - which are one action each and should
     * never be swallowed into a run of typing.
     */
    fun recordDiscrete(previous: TextFieldValue, next: TextFieldValue) {
        val edit = between(previous, next) ?: return
        undoStack.addLast(edit)
        held += edit.chars
        openedAt = null
        clearRedo()
        enforceBudget()
    }

    /**
     * Returns [current] with the last edit undone, or null when there is nothing to undo.
     *
     * A history that no longer matches the buffer is thrown away rather than applied:
     * replacing text that is not where the edit says it is would corrupt the file, which
     * is the one thing an undo stack must never do. Snapshots could not fail this way,
     * and deltas earn their memory back on the condition that they check.
     */
    fun undo(current: TextFieldValue): TextFieldValue? {
        val edit = undoStack.lastOrNull() ?: return null
        val restored = replace(current, edit.at, edit.inserted, edit.removed, edit.before)
            ?: return abandon()

        undoStack.removeLast()
        held -= edit.chars
        redoStack.addLast(edit)
        held += edit.chars
        openedAt = null
        return restored
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        val edit = redoStack.lastOrNull() ?: return null
        val applied = replace(current, edit.at, edit.removed, edit.inserted, edit.after)
            ?: return abandon()

        redoStack.removeLast()
        held -= edit.chars
        undoStack.addLast(edit)
        held += edit.chars
        openedAt = null
        return applied
    }

    /**
     * Extends [last] with [next] where the two are one continuous action.
     *
     * Only the three shapes a keyboard produces are merged - typing on, backspacing, and
     * deleting forward - and only while they stay on one line and inside [maxRunChars].
     * Anything else, including typing after a replacement or a jump elsewhere in the
     * file, starts a step of its own.
     */
    private fun merge(last: Edit, next: Edit): Edit? {
        if (last.chars + next.chars > maxRunChars) return null
        // A line break is a step of its own, on both sides: nothing merges into it and it
        // merges into nothing. Undo should step through lines rather than whole
        // paragraphs, and should hand back the break separately from the words after it.
        if (next.inserted.contains('\n') || next.removed.contains('\n')) return null
        if (last.inserted.contains('\n') || last.removed.contains('\n')) return null

        return when {
            last.removed.isEmpty() && next.removed.isEmpty() &&
                next.at == last.at + last.inserted.length ->
                last.copy(inserted = last.inserted + next.inserted, after = next.after)

            last.inserted.isEmpty() && next.inserted.isEmpty() &&
                next.at + next.removed.length == last.at ->
                last.copy(at = next.at, removed = next.removed + last.removed, after = next.after)

            last.inserted.isEmpty() && next.inserted.isEmpty() && next.at == last.at ->
                last.copy(removed = last.removed + next.removed, after = next.after)

            else -> null
        }
    }

    /** Swaps [expected] for [replacement] at [at], or null when the buffer disagrees. */
    private fun replace(
        value: TextFieldValue,
        at: Int,
        expected: String,
        replacement: String,
        selection: TextRange,
    ): TextFieldValue? {
        val text = value.text
        val end = at + expected.length
        if (at < 0 || end > text.length) return null
        if (!text.regionMatches(at, expected, 0, expected.length)) return null

        val updated = text.substring(0, at) + replacement + text.substring(end)
        val caret = selection.coerceWithin(updated.length)
        return value.copy(text = updated, selection = caret)
    }

    private fun abandon(): TextFieldValue? {
        clear()
        return null
    }

    private fun clearRedo() {
        redoStack.forEach { held -= it.chars }
        redoStack.clear()
    }

    private fun enforceBudget() {
        while (undoStack.size > maxEntries) held -= undoStack.removeFirst().chars
        while (redoStack.size > maxEntries) held -= redoStack.removeFirst().chars

        // Drop the oldest steps until the budget is met, but never the last one - a single
        // huge edit is exactly the one worth being able to take back.
        while (held > maxChars && undoStack.size > minEntries) {
            held -= undoStack.removeFirst().chars
        }
        while (held > maxChars && redoStack.isNotEmpty()) {
            held -= redoStack.removeFirst().chars
        }
    }
}

/** A selection recorded against one version of the text, clamped to fit another. */
private fun TextRange.coerceWithin(length: Int): TextRange =
    TextRange(start.coerceIn(0, length), end.coerceIn(0, length))
