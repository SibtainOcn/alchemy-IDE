package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.ui.editor.sora.BufferStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the store has to get right is losing nothing.
 *
 * It is the only place unwritten work exists: a buffer is in memory for as long as the
 * process is, and the prompt on the way out is built entirely on the answers here. So the
 * two things pinned are that it can name everything that is unsaved, and that looking for
 * something to drop does not fall over when what it finds is unsaved - which it did, from
 * inside an access-ordered map that counts a read as a change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BufferStoreTest {

    @Test
    fun `a buffer is unsaved once it differs from the disk`() {
        val store = BufferStore()
        val content = store.open("/a.py", "x = 1")
        assertFalse(store.isDirty("/a.py"))
        assertTrue(store.unsaved().isEmpty())

        content.insert(0, 0, "# ")
        assertTrue(store.isDirty("/a.py"))
        assertEquals(listOf("/a.py" to "# x = 1"), store.unsaved())
        assertEquals("# x = 1", store.unsavedText("/a.py"))
    }

    @Test
    fun `an edit undone back to the saved text is not unsaved`() {
        val store = BufferStore()
        val content = store.open("/a.py", "x = 1")
        content.insert(0, 0, "y")
        content.delete(0, 0, 0, 1)
        // Same length as the file and the same text, which is the case length alone gets
        // wrong.
        assertFalse(store.isDirty("/a.py"))
        assertTrue(store.unsaved().isEmpty())
    }

    @Test
    fun `saving records what was written`() {
        val store = BufferStore()
        val content = store.open("/a.py", "x = 1")
        content.insert(0, 0, "# ")
        store.markSaved("/a.py", content.toString())
        assertFalse(store.isDirty("/a.py"))
        assertEquals(null, store.unsavedText("/a.py"))
    }

    @Test
    fun `unsaved names every dirty buffer, not only the last one opened`() {
        val store = BufferStore()
        store.open("/a.py", "a").insert(0, 0, "1")
        store.open("/b.py", "b")
        store.open("/c.py", "c").insert(0, 0, "3")
        assertEquals(setOf("/a.py", "/c.py"), store.unsaved().map { it.first }.toSet())
    }

    @Test
    fun `filling the store past its limit does not throw over an unsaved buffer`() {
        // The oldest buffer holds work, so it cannot be dropped and the search has to
        // carry on past it - which is exactly where reading the map while walking it threw.
        val store = BufferStore(maxFiles = 3)
        store.open("/old.py", "old").insert(0, 0, "edited ")
        repeat(6) { store.open("/f$it.py", "text $it") }

        assertTrue("unsaved work must survive eviction", store.holds("/old.py"))
        assertTrue(store.isDirty("/old.py"))
        // Everything clean above the limit went, and the newest is always kept.
        assertTrue(store.holds("/f5.py"))
    }

    @Test
    fun `a clean buffer is re-read when the file has changed underneath it`() {
        val store = BufferStore()
        val first = store.open("/a.py", "x = 1")
        assertTrue(first === store.open("/a.py", "x = 1"))
        assertFalse(first === store.open("/a.py", "x = 2"))
    }

    @Test
    fun `a dirty buffer wins over what is on disk`() {
        val store = BufferStore()
        val content = store.open("/a.py", "x = 1")
        content.insert(0, 0, "# ")
        // Re-opening must not read over unwritten work, whatever the file now says.
        assertTrue(content === store.open("/a.py", "something else entirely"))
    }
}
