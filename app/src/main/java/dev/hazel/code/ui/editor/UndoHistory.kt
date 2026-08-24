package dev.hazel.code.ui.editor

import androidx.compose.ui.text.input.TextFieldValue

/**
 * Undo and redo, with a memory budget.
 *
 * Every snapshot is a whole copy of the buffer. That is fine for a 4 KB script and ruinous
 * for a large one: a 900 KB file is 1.8 MB per snapshot in UTF-16, so a hundred of them is
 * a couple of hundred megabytes and an out-of-memory kill on a mid-range phone. Counting
 * snapshots is therefore the wrong limit; the limit that matters is total characters held.
 *
 * A minimum depth is always kept regardless of budget, because an editor where a big file
 * cannot be undone even once is worse than one that uses some memory.
 *
 * Snapshots are coalesced as they arrive: a run of ordinary typing collapses into one
 * step, but a newline, a deletion or a pause starts a fresh one, which is what makes undo
 * step through edits rather than characters.
 */
class UndoHistory(
    private val maxEntries: Int = 120,
    /** Characters held across both stacks. Defaults to a share of this device's heap. */
    private val maxChars: Int = budgetCharsFor(Runtime.getRuntime().maxMemory()),
    private val minEntries: Int = 3,
    private val coalesceWindowMs: Long = 700,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Never squander a big heap, and never assume one. */
        private const val FLOOR_CHARS = 4_000_000      // 8 MB of UTF-16
        private const val CEILING_CHARS = 32_000_000   // 64 MB of UTF-16

        /** The share of the app heap undo history is allowed to occupy. */
        private const val HEAP_SHARE = 0.15

        /**
         * How much history this device can afford.
         *
         * The limit that matters is not the phone's RAM but the per-app heap Android
         * grants, which the manufacturer sets and which is commonly 128-256 MB even on a
         * 6 GB device. Allocating past it throws OutOfMemoryError while gigabytes sit
         * free, so the budget is taken from the heap actually granted rather than from a
         * number guessed at build time: a generous device gets a deep history, a
         * constrained one still gets a usable one.
         *
         * Divided by two because a Kotlin String is UTF-16 - two bytes per character.
         */
        fun budgetCharsFor(maxHeapBytes: Long): Int {
            val chars = (maxHeapBytes * HEAP_SHARE / 2).toLong()
            return chars.coerceIn(FLOOR_CHARS.toLong(), CEILING_CHARS.toLong()).toInt()
        }
    }

    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private var lastPushAt = 0L

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Total characters held across both stacks. Exposed for tests and diagnostics. */
    val heldChars: Int get() = undo.sumOf { it.text.length } + redo.sumOf { it.text.length }

    val depth: Int get() = undo.size

    fun clear() {
        undo.clear()
        redo.clear()
        lastPushAt = 0L
    }

    /**
     * Records [previous] as a restore point, given that the buffer has become [next].
     * Coalescing means most keystrokes record nothing at all.
     */
    fun record(previous: TextFieldValue, next: TextFieldValue) {
        val at = now()
        val bigChange = kotlin.math.abs(next.text.length - previous.text.length) > 1
        val newLine = next.text.length > previous.text.length &&
            next.text.getOrNull(next.selection.start - 1) == '\n'
        val stale = at - lastPushAt > coalesceWindowMs

        if (undo.isEmpty() || stale || bigChange || newLine) {
            undo.addLast(previous)
            enforceBudget()
        }
        lastPushAt = at
        redo.clear()
    }

    /** Records a restore point unconditionally - used for whole operations, not typing. */
    fun recordDiscrete(previous: TextFieldValue) {
        undo.addLast(previous)
        redo.clear()
        enforceBudget()
        lastPushAt = 0L
    }

    /** Returns the value to restore, or null when there is nothing to undo. */
    fun undo(current: TextFieldValue): TextFieldValue? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        enforceBudget()
        lastPushAt = 0L
        return previous
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        enforceBudget()
        lastPushAt = 0L
        return next
    }

    private fun enforceBudget() {
        while (undo.size > maxEntries) undo.removeFirst()
        while (redo.size > maxEntries) redo.removeFirst()

        // Drop the oldest restore points until the budget is met, but never go below the
        // minimum depth - one undo on a huge file is worth the memory.
        var held = heldChars
        while (held > maxChars && undo.size > minEntries) {
            held -= undo.removeFirst().text.length
        }
        while (held > maxChars && redo.isNotEmpty()) {
            held -= redo.removeFirst().text.length
        }
    }
}
