package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.CommandHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The history is a plain text file the user can open, so what it holds is what they will
 * read. It has to survive a restart, stay a sane size, and vanish completely when asked.
 */
class CommandHistoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun history(limit: Int = 500) =
        CommandHistory(File(folder.root, "history.txt"), limit)

    @Test
    fun `commands come back in the order they were typed`() {
        val h = history()
        h.add("ls")
        h.add("pwd")
        h.add("python main.py")

        assertEquals(listOf("ls", "pwd", "python main.py"), h.load())
    }

    @Test
    fun `history survives being reopened`() {
        history().add("git status")
        // A second instance over the same file is what a restart looks like.
        assertEquals(listOf("git status"), history().load())
    }

    @Test
    fun `the same command twice in a row is kept once`() {
        // Repeating a command is normal. Three identical lines in a row is what happens
        // when something is not working, and is not worth keeping three times.
        val h = history()
        h.add("ls")
        h.add("ls")
        h.add("ls")
        assertEquals(listOf("ls"), h.load())
    }

    @Test
    fun `the same command later is kept again`() {
        val h = history()
        h.add("ls")
        h.add("pwd")
        h.add("ls")
        assertEquals(listOf("ls", "pwd", "ls"), h.load())
    }

    @Test
    fun `blank input is not recorded`() {
        val h = history()
        h.add("")
        h.add("   ")
        assertTrue(h.load().isEmpty())
    }

    @Test
    fun `surrounding whitespace is trimmed off`() {
        val h = history()
        h.add("  ls -la  ")
        assertEquals(listOf("ls -la"), h.load())
    }

    @Test
    fun `only the most recent commands are kept`() {
        // A terminal used for a year is a file nobody meant to keep.
        val h = history(limit = 5)
        repeat(20) { h.add("command $it") }

        val kept = h.load()
        assertEquals(5, kept.size)
        assertEquals("command 19", kept.last())
        assertEquals("command 15", kept.first())
    }

    @Test
    fun `clearing leaves nothing behind`() {
        val h = history()
        h.add("something private")
        h.clear()

        assertTrue(h.load().isEmpty())
        assertEquals(0, h.size)
        assertFalse(File(h.path).exists())
    }

    @Test
    fun `there is always a file to open, even before anything is typed`() {
        // The history is shown by opening it in the editor, and an editor cannot open a
        // file that is not there.
        val file = history().ensureExists()
        assertTrue(file.exists())
        // Not blank: an editor opening on nothing is indistinguishable from one that
        // failed to open. The note is a comment, so it is not recalled as a command.
        assertTrue(file.readText().isNotBlank())
        assertTrue(history().load().isEmpty())
    }

    @Test
    fun `comments in the file are not offered as commands`() {
        val h = history()
        h.ensureExists()
        h.add("ls -la")
        assertEquals(listOf("ls -la"), h.load())
    }

    @Test
    fun `a missing file reads as empty rather than throwing`() {
        assertTrue(CommandHistory(File(folder.root, "never-written.txt")).load().isEmpty())
    }
}
