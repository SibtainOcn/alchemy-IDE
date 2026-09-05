package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.Search
import com.sibtainocn.alchemy.data.SearchKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The recursive search, on a real tree.
 *
 * What matters here is what it finds and what it refuses to: a search that quietly misses
 * a folder three levels down is worse than no search, and one that lists the whole volume
 * the moment the screen opens is not a search at all.
 */
class SearchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(parent: File, name: String): File =
        File(parent, name).apply { parentFile?.mkdirs(); writeText(name) }

    private fun search(
        root: File,
        query: String = "",
        kind: SearchKind? = null,
        showHidden: Boolean = false,
    ) = runBlocking { Search.run(root, query, kind, showHidden) {} }

    private fun names(root: File, query: String = "", kind: SearchKind? = null) =
        search(root, query, kind).entries.map { it.name }.toSet()

    @Test
    fun `nothing is searched for until something is asked`() {
        val root = temp.newFolder("root")
        file(root, "a.py")
        assertTrue(search(root).entries.isEmpty())
    }

    @Test
    fun `a name is found however deep it is`() {
        val root = temp.newFolder("root")
        file(root, "notes.md")
        file(File(root, "one/two/three"), "notes.md")
        file(File(root, "one"), "other.txt")

        assertEquals(2, search(root, query = "notes").entries.size)
    }

    @Test
    fun `matching is case insensitive and matches anywhere in the name`() {
        val root = temp.newFolder("root")
        file(root, "MyReport.PDF")
        assertEquals(setOf("MyReport.PDF"), names(root, query = "report"))
    }

    @Test
    fun `a kind on its own is a search`() {
        val root = temp.newFolder("root")
        file(root, "a.py")
        file(File(root, "deep/deeper"), "b.py")
        file(root, "c.md")
        file(root, "d.png")

        assertEquals(setOf("a.py", "b.py"), names(root, kind = SearchKind.PYTHON))
        assertEquals(setOf("c.md"), names(root, kind = SearchKind.MARKDOWN))
        assertEquals(setOf("d.png"), names(root, kind = SearchKind.IMAGES))
    }

    @Test
    fun `a kind and a name narrow each other`() {
        val root = temp.newFolder("root")
        file(root, "report.pdf")
        file(root, "report.md")
        file(root, "other.pdf")

        assertEquals(setOf("report.pdf"), names(root, query = "report", kind = SearchKind.PDF))
    }

    @Test
    fun `folders are their own kind and files are never in it`() {
        val root = temp.newFolder("root")
        File(root, "pictures").mkdirs()
        file(root, "pictures.txt")

        assertEquals(setOf("pictures"), names(root, kind = SearchKind.FOLDERS))
        assertTrue(search(root, kind = SearchKind.FOLDERS).entries.all { it.isDir })
    }

    @Test
    fun `documents cover text and pdf, audio and video stay apart`() {
        val root = temp.newFolder("root")
        file(root, "a.txt")
        file(root, "b.pdf")
        file(root, "c.opus")
        file(root, "d.mp4")

        assertEquals(setOf("a.txt", "b.pdf"), names(root, kind = SearchKind.DOCUMENTS))
        assertEquals(setOf("c.opus"), names(root, kind = SearchKind.AUDIO))
        assertEquals(setOf("d.mp4"), names(root, kind = SearchKind.VIDEO))
    }

    @Test
    fun `hidden files are left out unless they are asked for`() {
        val root = temp.newFolder("root")
        file(root, ".secret.py")
        file(root, "plain.py")

        assertEquals(setOf("plain.py"), names(root, kind = SearchKind.PYTHON))
        assertEquals(
            setOf(".secret.py", "plain.py"),
            search(root, kind = SearchKind.PYTHON, showHidden = true).entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `a hidden folder is not walked into`() {
        val root = temp.newFolder("root")
        file(File(root, ".git"), "config.py")
        file(root, "main.py")

        assertEquals(setOf("main.py"), names(root, kind = SearchKind.PYTHON))
    }

    @Test
    fun `results arrive in batches as they are found`() = runBlocking {
        val root = temp.newFolder("root")
        repeat(120) { file(root, "f$it.py") }

        var batches = 0
        var lastSize = 0
        val result = Search.run(root, "", SearchKind.PYTHON, false) { found ->
            batches++
            // Every batch is at least as long as the one before: it is the same list
            // growing, not a fresh page.
            assertTrue(found.size >= lastSize)
            lastSize = found.size
        }

        assertEquals(120, result.entries.size)
        assertTrue("expected more than one batch, got $batches", batches > 1)
        assertFalse(result.truncated)
    }

    @Test
    fun `the walk stops at the cap and says so`() {
        val root = temp.newFolder("root")
        // Comfortably past the ceiling, spread over a few folders.
        repeat(Search.MAX_RESULTS + 50) { file(File(root, "d${it % 5}"), "f$it.py") }

        val result = search(root, kind = SearchKind.PYTHON)
        assertTrue(result.truncated)
        assertEquals(Search.MAX_RESULTS, result.entries.size)
    }

    @Test
    fun `an unreadable folder does not stop the walk`() {
        val root = temp.newFolder("root")
        val locked = File(root, "locked").apply { mkdirs() }
        file(File(root, "open"), "found.py")
        locked.setReadable(false)

        // The one that cannot be listed is skipped; the rest of the tree still answers.
        assertEquals(setOf("found.py"), names(root, kind = SearchKind.PYTHON))
        locked.setReadable(true)
    }
}
