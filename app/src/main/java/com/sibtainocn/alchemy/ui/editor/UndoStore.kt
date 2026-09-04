package com.sibtainocn.alchemy.ui.editor

/**
 * One undo history per file, for as long as the app is running.
 *
 * Closing a file and coming back to it finds its history intact; killing the app does
 * not. That is the lifetime an editor tab has, and it is deliberate rather than a
 * shortcut: nothing is written to disk, so there is no saved history to go stale against
 * a file edited by something else between sessions, and no undo step can outlive the
 * buffer it describes.
 *
 * The budget is shared across files rather than granted to each of them. A per-file
 * budget is really "budget times however many files were opened", which is the same
 * unbounded number that counting snapshots was. When the total is exceeded the least
 * recently edited file loses its history whole: dropping one closed file's history
 * entirely is a better trade than shortening the history of the file in front of you.
 *
 * The figure is a flat one rather than a share of the device heap. It was read from the
 * heap when a step cost as much as the whole file; a step now costs as much as the edit
 * that made it, so a dozen files' worth of history is a couple of megabytes on any phone
 * that can run the app at all.
 *
 * The total is checked whenever the store is told about a buffer, which the editor does
 * on every edit. It cannot be checked as a history grows: the store hands the history out
 * and does not see what is recorded into it.
 */
class UndoStore(
    /** Characters of edit text held across every file. Roughly 2 MB of UTF-16. */
    private val budgetChars: Int = 1_000_000,
    private val maxFiles: Int = 12,
) {
    private class Entry(val history: UndoHistory, var text: String)

    /** Access-ordered, so the first entry is always the least recently edited file. */
    private val entries = LinkedHashMap<String, Entry>(0, 0.75f, true)

    val fileCount: Int get() = entries.size

    val heldChars: Int get() = entries.values.sumOf { it.history.heldChars }

    /** True when [path] has a history that survived from earlier in the session. */
    fun remembers(path: String): Boolean = entries.containsKey(path)

    /**
     * The history for [path], given the [text] just read for it.
     *
     * A remembered history whose buffer no longer matches what was read is thrown away
     * rather than reused. The file has been changed by something else - another app, a
     * git checkout, this app's own discarded edits - and undoing into it would restore
     * text that was never in the version now on screen.
     */
    fun of(path: String, text: String): UndoHistory {
        entries[path]?.let { if (it.text == text) return it.history }
        val entry = Entry(UndoHistory(maxChars = budgetChars), text)
        entries[path] = entry
        evict(keep = path)
        return entry.history
    }

    /**
     * Records what [path]'s buffer holds now, so that reopening it can tell whether the
     * history still describes the file.
     */
    fun noteText(path: String, text: String) {
        val entry = entries[path] ?: return
        entry.text = text
        evict(keep = path)
    }

    fun forget(path: String) {
        entries.remove(path)
    }

    fun clear() {
        entries.clear()
    }

    private fun evict(keep: String) {
        while (entries.size > maxFiles || (heldChars > budgetChars && entries.size > 1)) {
            val eldest = entries.keys.firstOrNull { it != keep } ?: break
            entries.remove(eldest)
        }
    }
}
