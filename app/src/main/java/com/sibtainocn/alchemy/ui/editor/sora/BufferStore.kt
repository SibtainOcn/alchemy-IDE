package com.sibtainocn.alchemy.ui.editor.sora

import io.github.rosemoe.sora.text.Content

/**
 * The open buffers, one per file, for as long as the app is running.
 *
 * A [Content] carries its own undo stack, and the editor can be handed the same object
 * back rather than its text, so a tab that keeps its buffer keeps its history with it.
 * That is why this replaces two stores rather than one: the parked-drafts map and the
 * separate per-file undo store were both working around a buffer that could not survive
 * being taken off screen.
 *
 * What is bounded is characters, not files. A tab costs nothing until it holds work that
 * is not on disk, so counting files was never measuring the thing that runs out.
 */
class BufferStore(
    private val maxFiles: Int = 20,
    /** Characters held across every buffer. Roughly 8 MB of UTF-16. */
    private val budgetChars: Int = 4_000_000,
) {

    private class Entry(val content: Content, var savedText: String)

    /** Access-ordered, so the first entry is always the least recently opened. */
    private val entries = LinkedHashMap<String, Entry>(0, 0.75f, true)

    val heldChars: Int get() = entries.values.sumOf { it.content.length }

    val size: Int get() = entries.size

    fun holds(path: String): Boolean = path in entries

    /**
     * The buffer for [path], given what is on disk right now.
     *
     * A buffer with unsaved work always wins: it is what this session did and has not
     * written, and reading over it would lose it. A clean one is kept only while the file
     * on disk still matches it - which is what keeps its undo history across a tab switch
     * - and replaced when it does not, which is what picks up an edit made from outside
     * the app.
     */
    fun open(path: String, diskText: String): Content {
        entries[path]?.let { existing ->
            if (isDirty(path) || existing.content.toString() == diskText) return existing.content
        }
        val entry = Entry(Content(diskText), diskText)
        entries[path] = entry
        evict(keep = path)
        return entry.content
    }

    /** True when [path]'s buffer holds work that is not on disk. */
    fun isDirty(path: String): Boolean {
        val entry = entries[path] ?: return false
        // Length is O(1) and settles it almost every time: an edit that leaves the buffer
        // the same length as the file is rare, and is exactly the case - an undo landing
        // back on the saved text - that has to be answered exactly rather than guessed.
        if (entry.content.length != entry.savedText.length) return true
        return entry.content.toString() != entry.savedText
    }

    /** Records that [path] now matches what was written to it. */
    fun markSaved(path: String, text: String) {
        entries[path]?.savedText = text
    }

    fun forget(path: String) {
        entries.remove(path)
    }

    /**
     * Drops buffers until both limits are met, least recently opened first.
     *
     * Only clean buffers are ever dropped. If every candidate holds unsaved work the
     * limits are simply exceeded: that work is not this store's to throw away, and a
     * ceiling is a smaller thing to break than someone's afternoon.
     */
    private fun evict(keep: String) {
        while (entries.size > maxFiles || heldChars > budgetChars) {
            val victim = entries.keys.firstOrNull { it != keep && !isDirty(it) } ?: return
            entries.remove(victim)
        }
    }
}
