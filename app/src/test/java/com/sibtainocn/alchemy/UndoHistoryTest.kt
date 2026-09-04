package com.sibtainocn.alchemy

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sibtainocn.alchemy.ui.editor.UndoHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * History is stored as edits, not as copies of the file, so these pin three things: that
 * an edit reconstructs the buffer exactly, that a run of typing is one step rather than
 * one per keystroke, and that what it all costs follows the size of the editing rather
 * than the size of the document.
 */
class UndoHistoryTest {

    private var clock = 0L

    private fun history(
        maxEntries: Int = 500,
        maxChars: Int = 300_000,
        minEntries: Int = 1,
        maxRunChars: Int = 4_000,
    ) = UndoHistory(
        maxEntries = maxEntries,
        maxChars = maxChars,
        minEntries = minEntries,
        maxRunChars = maxRunChars,
        now = { clock },
    )

    private fun v(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    /**
     * A buffer the tests type into, so a case reads as the sequence of edits a person
     * makes rather than as pairs of before-and-after values.
     */
    private class Buffer(private val history: UndoHistory, start: String = "") {
        var value = TextFieldValue(start, TextRange(start.length))
            private set

        fun type(text: String, at: Int = value.text.length) {
            val next = value.text.substring(0, at) + text + value.text.substring(at)
            edit(TextFieldValue(next, TextRange(at + text.length)))
        }

        fun backspace(count: Int = 1) {
            repeat(count) {
                val caret = value.selection.start
                if (caret == 0) return
                val next = value.text.removeRange(caret - 1, caret)
                edit(TextFieldValue(next, TextRange(caret - 1)))
            }
        }

        fun operation(next: TextFieldValue) {
            history.recordDiscrete(value, next)
            value = next
        }

        private fun edit(next: TextFieldValue) {
            history.record(value, next)
            value = next
        }

        fun undo(): Boolean = history.undo(value)?.also { value = it } != null
        fun redo(): Boolean = history.redo(value)?.also { value = it } != null
    }

    // ---- What an edit is ----

    @Test
    fun `an insertion is stored as the inserted run alone`() {
        val edit = UndoHistory.between(v("hello world"), v("hello brave world"))
        assertNotNull(edit)
        assertEquals(6, edit!!.at)
        assertEquals("", edit.removed)
        assertEquals("brave ", edit.inserted)
    }

    @Test
    fun `a deletion is stored as the removed run alone`() {
        val edit = UndoHistory.between(v("hello brave world"), v("hello world"))!!
        assertEquals(6, edit.at)
        assertEquals("brave ", edit.removed)
        assertEquals("", edit.inserted)
    }

    @Test
    fun `a replacement stores both halves and nothing either side of them`() {
        val edit = UndoHistory.between(v("print(x)"), v("print(y)"))!!
        assertEquals(6, edit.at)
        assertEquals("x", edit.removed)
        assertEquals("y", edit.inserted)
    }

    @Test
    fun `text that did not change is not an edit`() {
        assertNull(UndoHistory.between(v("same"), v("same")))
    }

    @Test
    fun `an edit costs the size of the change, not the size of the file`() {
        // The whole point of the rewrite: one keystroke in a large file is one character
        // of history. Under snapshots this step cost two copies of the document.
        val big = "x".repeat(500_000)
        val h = history()
        val buffer = Buffer(h, big)
        buffer.type("!")

        assertEquals(1, h.heldChars)
    }

    // ---- Undo and redo reconstruct the buffer ----

    @Test
    fun `undo restores the text and the caret`() {
        val h = history()
        val buffer = Buffer(h, "value = 1")
        buffer.type("0")
        assertEquals("value = 10", buffer.value.text)

        assertTrue(buffer.undo())
        assertEquals("value = 1", buffer.value.text)
        assertEquals(9, buffer.value.selection.start)
    }

    @Test
    fun `redo puts the edit back`() {
        val h = history()
        val buffer = Buffer(h, "value = 1")
        buffer.type("0")
        buffer.undo()

        assertTrue(buffer.redo())
        assertEquals("value = 10", buffer.value.text)
        assertFalse(h.canRedo)
    }

    @Test
    fun `an edit in the middle of a file leaves both ends alone`() {
        val h = history()
        val buffer = Buffer(h, "one\ntwo\nthree")
        buffer.type("!", at = 7)
        assertEquals("one\ntwo!\nthree", buffer.value.text)

        buffer.undo()
        assertEquals("one\ntwo\nthree", buffer.value.text)
    }

    @Test
    fun `a whole session of edits undoes back to the beginning`() {
        val h = history()
        val buffer = Buffer(h, "start")
        listOf(" one", " two", " three").forEach {
            clock += 5_000
            buffer.type(it)
        }
        assertEquals("start one two three", buffer.value.text)

        while (buffer.undo()) Unit
        assertEquals("start", buffer.value.text)
        assertFalse(h.canUndo)
    }

    @Test
    fun `undo on an empty history returns null rather than throwing`() {
        assertNull(history().undo(v("x")))
        assertNull(history().redo(v("x")))
    }

    @Test
    fun `a history that no longer matches the buffer is dropped, not applied`() {
        // Deltas are only safe if they check. Replacing text that is not where the edit
        // says it is would corrupt the file - the one thing an undo stack must not do.
        val h = history()
        val buffer = Buffer(h, "hello")
        buffer.type(" world")

        assertNull(h.undo(v("something else entirely")))
        assertFalse("The unusable history must be cleared", h.canUndo)
        assertEquals(0, h.heldChars)
    }

    // ---- Runs of typing are one step ----

    @Test
    fun `a run of typing is one step, not one per character`() {
        val h = history()
        val buffer = Buffer(h, "")
        "hello".forEach { buffer.type(it.toString()) }

        assertEquals(1, h.depth)
        buffer.undo()
        assertEquals("", buffer.value.text)
    }

    @Test
    fun `a pause starts a new step`() {
        val h = history()
        val buffer = Buffer(h, "")
        buffer.type("one")
        clock += 5_000
        buffer.type(" two")

        assertEquals(2, h.depth)
        buffer.undo()
        assertEquals("one", buffer.value.text)
    }

    @Test
    fun `a line break closes the run`() {
        // Undo should step through lines, not through the whole paragraph.
        val h = history()
        val buffer = Buffer(h, "")
        buffer.type("first")
        buffer.type("\n")
        buffer.type("second")

        buffer.undo()
        assertEquals("The word comes back before the line does", "first\n", buffer.value.text)
        buffer.undo()
        assertEquals("first", buffer.value.text)
    }

    @Test
    fun `backspacing is one step, and gives the characters back in order`() {
        val h = history()
        val buffer = Buffer(h, "hello")
        buffer.backspace(3)
        assertEquals("he", buffer.value.text)

        assertEquals(1, h.depth)
        buffer.undo()
        assertEquals("hello", buffer.value.text)
    }

    @Test
    fun `typing then deleting are two steps, not one confused run`() {
        val h = history()
        val buffer = Buffer(h, "")
        buffer.type("hello")
        buffer.backspace(2)

        assertEquals(2, h.depth)
        buffer.undo()
        assertEquals("hello", buffer.value.text)
    }

    @Test
    fun `typing somewhere else starts a new step`() {
        val h = history()
        val buffer = Buffer(h, "one two")
        buffer.type("!")
        buffer.type("?", at = 0)

        assertEquals(2, h.depth)
        buffer.undo()
        assertEquals("one two!", buffer.value.text)
    }

    @Test
    fun `a long run is broken up rather than kept as one enormous step`() {
        val h = history(maxRunChars = 10)
        val buffer = Buffer(h, "")
        repeat(30) { buffer.type("x") }

        assertTrue("A run must not grow without limit", h.depth > 1)
        while (buffer.undo()) Unit
        assertEquals("", buffer.value.text)
    }

    @Test
    fun `a toolbar operation is always its own step`() {
        val h = history()
        val buffer = Buffer(h, "line")
        buffer.type("!")
        // Duplicating a line is one action; it must not join the typing before it.
        buffer.operation(v("line!\nline!"))

        assertEquals(2, h.depth)
        buffer.undo()
        assertEquals("line!", buffer.value.text)
    }

    @Test
    fun `a new edit after an undo drops the redo branch`() {
        val h = history()
        val buffer = Buffer(h, "")
        buffer.type("one")
        buffer.undo()
        assertTrue(h.canRedo)

        clock += 5_000
        buffer.type("two")
        assertFalse(h.canRedo)
        assertEquals(0, h.heldChars - h.depth * 3)
    }

    @Test
    fun `an undone edit is not merged into by what follows it`() {
        val h = history()
        val buffer = Buffer(h, "")
        buffer.type("abc")
        buffer.undo()
        buffer.type("xyz")

        assertEquals(1, h.depth)
        buffer.undo()
        assertEquals("", buffer.value.text)
    }

    // ---- What it costs ----

    @Test
    fun `the budget drops the oldest steps first`() {
        val h = history(maxChars = 20)
        val buffer = Buffer(h, "")
        repeat(10) {
            clock += 5_000
            buffer.type("12345")
        }

        assertTrue("Held ${h.heldChars} chars against a 20 char budget", h.heldChars <= 20)
        assertTrue(h.depth in 1..4)
    }

    @Test
    fun `one step always survives, however large the edit was`() {
        val h = history(maxChars = 10)
        val buffer = Buffer(h, "")
        buffer.operation(v("x".repeat(5_000)))

        assertTrue("A huge edit is exactly the one worth taking back", h.canUndo)
        buffer.undo()
        assertEquals("", buffer.value.text)
    }

    @Test
    fun `the step count is capped`() {
        val h = history(maxEntries = 5)
        val buffer = Buffer(h, "")
        repeat(20) {
            clock += 5_000
            buffer.type("x")
        }

        assertEquals(5, h.depth)
    }

    @Test
    fun `the running total is kept honest as steps come and go`() {
        val h = history()
        val buffer = Buffer(h, "")
        assertEquals(0, h.heldChars)

        buffer.type("hello")
        assertEquals(5, h.heldChars)

        buffer.undo()
        assertEquals("The step moved to redo, it did not vanish", 5, h.heldChars)

        buffer.redo()
        assertEquals(5, h.heldChars)

        h.clear()
        assertEquals(0, h.heldChars)
        assertFalse(h.canUndo)
    }

    @Test
    fun `clearing the redo branch gives its memory back`() {
        val h = history()
        val buffer = Buffer(h, "")
        buffer.type("hello")
        buffer.undo()
        assertEquals(5, h.heldChars)

        clock += 5_000
        buffer.type("hi")
        assertEquals(2, h.heldChars)
    }

    @Test
    fun `a thousand keystrokes across a large file cost kilobytes, not megabytes`() {
        // The number that made the rewrite worth doing: this session under snapshots was
        // hundreds of megabytes, and is now smaller than the file itself.
        val h = history()
        val buffer = Buffer(h, "x".repeat(1_000_000))
        repeat(1_000) {
            clock += 5_000
            buffer.type("y", at = 10)
        }

        assertEquals(500, h.depth)
        assertTrue("Held ${h.heldChars} characters", h.heldChars < 2_000)
    }
}
