package dev.hazel.code.data

import java.io.File

/**
 * Every command typed at the terminal prompt, kept in the app's own storage.
 *
 * One command per line, oldest first, plain text. Not a database and not a format: it is
 * meant to be readable when it is shown back to the person who typed it, and deletable in
 * one action.
 *
 * It lives in internal storage rather than in shared storage, so it is private to the app
 * and goes when the app does. Commands can carry paths, hostnames and occasionally things
 * that should not be lying around in a folder any other app can read.
 *
 * Bounded, because a terminal used for a year is a file nobody meant to keep.
 */
class CommandHistory(
    private val file: File,
    private val limit: Int = 500,
) {

    /** Oldest first. Empty when nothing has been typed, or the file cannot be read. */
    fun load(): List<String> = runCatching {
        if (!file.exists()) emptyList()
        else file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    /**
     * Adds [command], unless it is the one just added.
     *
     * Repeating a command is normal and worth remembering once. Three identical lines in a
     * row is what happens when something is not working, and is not worth keeping three
     * times.
     */
    fun add(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        runCatching {
            val existing = load()
            if (existing.lastOrNull() == trimmed) return

            val kept = (existing + trimmed).takeLast(limit)
            file.parentFile?.mkdirs()
            file.writeText(kept.joinToString("\n", postfix = "\n"))
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    /**
     * Makes sure there is a file to open, even before anything has been typed.
     *
     * The history is shown by opening it in the editor like any other text file, and an
     * editor cannot open a file that is not there.
     */
    fun ensureExists(): File {
        runCatching {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.writeText("")
            }
        }
        return file
    }

    val size: Int get() = load().size

    /** Where the file is, for showing someone what they are about to delete. */
    val path: String get() = file.absolutePath
}
