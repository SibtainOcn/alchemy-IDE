package com.sibtainocn.alchemy

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sibtainocn.alchemy.ui.editor.UndoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-file history is the thing people notice when it is missing: closing a file to look
 * at another one and coming back to find undo empty. These pin what survives - and, just
 * as importantly, what must not: a history that no longer describes the file on disk.
 */
class UndoStoreTest {

    private fun v(text: String) = TextFieldValue(text, TextRange(text.length))

    @Test
    fun `a file's history survives leaving it and coming back`() {
        val store = UndoStore()
        store.of("/a.py", "one").recordDiscrete(v("one"), v("one changed"))

        // Off to another file and back again.
        store.of("/b.py", "other")
        val back = store.of("/a.py", "one")

        assertTrue("The history was dropped by looking at another file", back.canUndo)
        assertEquals("one", back.undo(v("one changed"))?.text)
    }

    @Test
    fun `a history is dropped when the file has changed underneath it`() {
        val store = UndoStore()
        store.of("/a.py", "one").recordDiscrete(v("one"), v("one changed"))

        // Something else wrote the file between visits - another app, a checkout, or this
        // app's own discarded edits. Undoing into the old text would restore a version
        // that was never here.
        val fresh = store.of("/a.py", "one, rewritten elsewhere")
        assertFalse(fresh.canUndo)
        assertNull(fresh.undo(v("x")))
    }

    @Test
    fun `an edited buffer is remembered, so saving and reopening keeps the history`() {
        val store = UndoStore()
        val history = store.of("/a.py", "one")
        history.recordDiscrete(v("one"), v("one and two"))
        store.noteText("/a.py", "one and two")

        // The file was saved, so what is on disk is what the buffer held.
        assertTrue(store.of("/a.py", "one and two").canUndo)
    }

    @Test
    fun `the budget is shared across files, not handed to each of them`() {
        // Two files of 400 chars each cannot both keep a history inside a 600 char budget.
        // Edits arrive as record-then-note, the order the editor writes them in, because
        // the store cannot see a history grow after it has handed it out.
        val store = UndoStore(budgetChars = 600)
        val text = "x".repeat(400)
        store.of("/a.py", text).recordDiscrete(v(""), v(text))
        store.noteText("/a.py", text)
        store.of("/b.py", text).recordDiscrete(v(""), v(text))
        store.noteText("/b.py", text)

        assertEquals("The older file should have been dropped whole", 1, store.fileCount)
        assertFalse(store.remembers("/a.py"))
        assertTrue(store.remembers("/b.py"))
        assertTrue(store.heldChars <= 600)
    }

    @Test
    fun `the file being edited is never the one evicted`() {
        val store = UndoStore(budgetChars = 600)
        val text = "x".repeat(400)
        store.of("/a.py", text).recordDiscrete(v(""), v(text))
        store.noteText("/a.py", text)
        store.of("/b.py", text).recordDiscrete(v(""), v(text))

        // Editing /b.py further must not cost /b.py its own history.
        store.noteText("/b.py", text)
        assertTrue(store.remembers("/b.py"))
        assertTrue(store.of("/b.py", text).canUndo)
    }

    @Test
    fun `only so many files are remembered at once`() {
        val store = UndoStore(maxFiles = 3)
        repeat(5) { i -> store.of("/file$i.py", "body") }

        assertEquals(3, store.fileCount)
        assertFalse("The first files opened are the first forgotten", store.remembers("/file0.py"))
        assertTrue(store.remembers("/file4.py"))
    }

    @Test
    fun `reopening a file counts as using it, so it is not the next one dropped`() {
        val store = UndoStore(maxFiles = 2)
        store.of("/a.py", "a")
        store.of("/b.py", "b")
        store.of("/a.py", "a")

        // /b.py is now the stale one, even though /a.py was opened first.
        store.of("/c.py", "c")
        assertTrue(store.remembers("/a.py"))
        assertFalse(store.remembers("/b.py"))
    }

    @Test
    fun `a forgotten file starts over, and clearing forgets everything`() {
        val store = UndoStore()
        store.of("/a.py", "one").recordDiscrete(v("one"), v("one changed"))
        store.forget("/a.py")
        assertFalse(store.of("/a.py", "one").canUndo)

        store.of("/b.py", "two")
        store.clear()
        assertEquals(0, store.fileCount)
    }
}
