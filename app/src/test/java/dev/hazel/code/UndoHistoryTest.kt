package dev.hazel.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.hazel.code.ui.editor.UndoHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget exists because snapshots are whole copies of the buffer: a 900 KB file is
 * 1.8 MB per snapshot, so a hundred of them is an out-of-memory kill. These pin both
 * halves of that trade - the memory ceiling, and the guarantee that undo still works on a
 * file large enough to hit it.
 */
class UndoHistoryTest {

    private var clock = 0L
    private fun history(
        maxEntries: Int = 120,
        maxChars: Int = 4_000_000,
        minEntries: Int = 3,
    ) = UndoHistory(
        maxEntries = maxEntries,
        maxChars = maxChars,
        minEntries = minEntries,
        now = { clock },
    )

    private fun v(text: String) = TextFieldValue(text, TextRange(text.length))

    @Test
    fun `undo returns the previous value`() {
        val h = history()
        h.recordDiscrete(v("one"))
        assertTrue(h.canUndo)
        assertEquals("one", h.undo(v("two"))?.text)
        assertFalse(h.canUndo)
    }

    @Test
    fun `undo on an empty history returns null rather than throwing`() {
        assertNull(history().undo(v("x")))
        assertNull(history().redo(v("x")))
    }

    @Test
    fun `redo replays what undo took back`() {
        val h = history()
        h.recordDiscrete(v("one"))
        val undone = h.undo(v("two"))!!
        assertEquals("one", undone.text)
        assertTrue(h.canRedo)
        assertEquals("two", h.redo(undone)?.text)
    }

    @Test
    fun `a new edit clears the redo stack`() {
        val h = history()
        h.recordDiscrete(v("one"))
        h.undo(v("two"))
        assertTrue(h.canRedo)
        h.recordDiscrete(v("three"))
        assertFalse("Editing after undo must drop the redo branch", h.canRedo)
    }

    // ---- Coalescing ----

    @Test
    fun `a run of typing collapses into one step`() {
        val h = history()
        var text = ""
        "hello".forEach { c ->
            val before = v(text)
            text += c
            clock += 10
            h.record(before, v(text))
        }
        assertEquals("Five keystrokes inside the window is one step", 1, h.depth)
    }

    @Test
    fun `a pause starts a new step`() {
        val h = history()
        h.record(v("a"), v("ab"))
        clock += 5_000
        h.record(v("ab"), v("abc"))
        assertEquals(2, h.depth)
    }

    @Test
    fun `a newline starts a new step`() {
        val h = history()
        h.record(v("a"), v("ab"))
        clock += 10
        h.record(v("ab"), TextFieldValue("ab\n", TextRange(3)))
        assertEquals(2, h.depth)
    }

    @Test
    fun `a paste starts a new step`() {
        val h = history()
        h.record(v("a"), v("ab"))
        clock += 10
        h.record(v("ab"), v("ab-a-whole-pasted-chunk"))
        assertEquals(2, h.depth)
    }

    // ---- The budget ----

    @Test
    fun `the entry count is capped`() {
        val h = history(maxEntries = 10)
        repeat(50) { h.recordDiscrete(v("edit $it")) }
        assertEquals(10, h.depth)
    }

    @Test
    fun `memory is capped by characters, not by snapshot count`() {
        // Ten snapshots of a 100k file is 1M chars; a 300k budget must not hold them all.
        val big = "x".repeat(100_000)
        val h = history(maxChars = 300_000, minEntries = 1)
        repeat(10) { h.recordDiscrete(v(big)) }

        assertTrue("Budget exceeded: held ${h.heldChars}", h.heldChars <= 300_000)
        assertTrue("Should have dropped old snapshots", h.depth < 10)
    }

    @Test
    fun `a file bigger than the whole budget can still be undone once`() {
        // The case that matters: one snapshot alone blows the budget. Undo must survive
        // anyway, or a large file becomes uneditable in practice.
        val huge = "y".repeat(500_000)
        val h = history(maxChars = 100_000, minEntries = 3)
        h.recordDiscrete(v(huge))

        assertTrue("Undo must remain available on a large file", h.canUndo)
        assertEquals(huge, h.undo(v("changed"))?.text)
    }

    @Test
    fun `the oldest snapshots are dropped first`() {
        val chunk = "z".repeat(1_000)
        val h = history(maxChars = 3_500, minEntries = 1)
        listOf("first", "second", "third", "fourth", "fifth").forEach {
            h.recordDiscrete(v(it + chunk))
        }
        // Whatever survives, the most recent restore point must be among it.
        assertTrue(h.undo(v("now"))!!.text.startsWith("fifth"))
    }

    // ---- The budget adapts to the device ----

    @Test
    fun `the budget scales with the heap the device actually grants`() {
        // Android grants a per-app heap, not the phone's RAM: a 6 GB device commonly caps
        // an app at 128-256 MB, and allocating past that throws while RAM sits free.
        val small = UndoHistory.budgetCharsFor(128L * 1024 * 1024)
        val mid = UndoHistory.budgetCharsFor(256L * 1024 * 1024)
        val large = UndoHistory.budgetCharsFor(512L * 1024 * 1024)

        assertTrue("A bigger heap must buy more history", small < mid)
        assertTrue("A bigger heap must buy more history", mid < large)
    }

    @Test
    fun `the budget never exceeds a sane share of the heap`() {
        val heap = 256L * 1024 * 1024
        val bytesHeld = UndoHistory.budgetCharsFor(heap).toLong() * 2
        assertTrue(
            "Undo would claim ${bytesHeld * 100 / heap}% of the heap",
            bytesHeld <= heap / 4,
        )
    }

    @Test
    fun `the budget is clamped at both ends`() {
        // A tiny or absurd heap reading must not produce a useless or reckless budget.
        assertEquals(4_000_000, UndoHistory.budgetCharsFor(1024))
        assertEquals(32_000_000, UndoHistory.budgetCharsFor(64L * 1024 * 1024 * 1024))
    }

    @Test
    fun `clear empties both stacks`() {
        val h = history()
        h.recordDiscrete(v("one"))
        h.undo(v("two"))
        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals(0, h.heldChars)
    }
}
