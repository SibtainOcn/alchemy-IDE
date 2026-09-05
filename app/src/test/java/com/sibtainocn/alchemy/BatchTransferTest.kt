package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.FileStore.Decision
import com.sibtainocn.alchemy.data.FileStore.Resolution
import com.sibtainocn.alchemy.data.Transfer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A selection moved or removed as one operation.
 *
 * The single-item path is covered by [FileTransferTest]; what is pinned here is what only
 * a batch can get wrong. One unreadable file must not abandon the other thirty-nine. An
 * answer given to a conflict prompt with "apply to all" has to carry across the rest of
 * the selection rather than stopping at the end of the folder it was asked in. And a move
 * inside one volume has to stay a set of renames, because measuring a tree that is about to
 * be renamed is the slowest thing that could happen to an operation that should be instant.
 */
class BatchTransferTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(parent: File, name: String, body: String = name): File =
        File(parent, name).apply { parentFile?.mkdirs(); writeText(body) }

    private fun always(resolution: Resolution, all: Boolean = false):
        suspend (FileStore.Conflict) -> Decision = { Decision(resolution, all) }

    @Test
    fun `every item lands`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val sources = (1..5).map { file(from, "f$it.py", "print($it)") }

        val result = FileStore.transferAll(sources, to, Transfer.COPY)

        assertTrue(result.ok)
        assertEquals(5, result.done.size)
        sources.forEach { assertTrue(it.name, File(to, it.name).exists()) }
        // A copy leaves the originals where they were.
        sources.forEach { assertTrue(it.exists()) }
    }

    @Test
    fun `a move takes the originals with it`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val sources = listOf(file(from, "a.txt"), file(from, "b.txt"))

        val result = FileStore.transferAll(sources, to, Transfer.MOVE)

        assertTrue(result.ok)
        assertEquals(2, result.done.size)
        sources.forEach { assertFalse(it.exists()) }
        assertEquals("a.txt", File(to, "a.txt").readText())
    }

    @Test
    fun `a folder goes with everything under it`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val tree = File(from, "pkg").apply { mkdirs() }
        file(tree, "one.py")
        file(File(tree, "inner"), "two.py")

        val result = FileStore.transferAll(listOf(tree, file(from, "loose.txt")), to, Transfer.COPY)

        assertTrue(result.ok)
        assertTrue(File(to, "pkg/one.py").exists())
        assertTrue(File(to, "pkg/inner/two.py").exists())
        assertTrue(File(to, "loose.txt").exists())
    }

    @Test
    fun `one item that cannot go does not stop the rest`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val good = file(from, "good.txt")
        // A path that is not there any more, which is what a stale selection is.
        val gone = File(from, "vanished.txt")
        val alsoGood = file(from, "also.txt")

        val result = FileStore.transferAll(listOf(good, gone, alsoGood), to, Transfer.COPY)

        assertFalse(result.ok)
        assertEquals(1, result.failures.size)
        assertEquals("vanished.txt", result.failures.first().first.name)
        // The other two are through.
        assertTrue(File(to, "good.txt").exists())
        assertTrue(File(to, "also.txt").exists())
        assertEquals(2, result.done.size)
    }

    @Test
    fun `apply to all carries across the whole selection`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val sources = (1..3).map { file(from, "f$it.txt", "new $it") }
        sources.forEach { file(to, it.name, "old") }

        var asked = 0
        val result = FileStore.transferAll(sources, to, Transfer.COPY, null) { conflict ->
            asked++
            Decision(Resolution.OVERWRITE, all = true)
        }

        assertTrue(result.ok)
        // Asked once, answered for all three.
        assertEquals(1, asked)
        assertEquals("new 3", File(to, "f3.txt").readText())
    }

    @Test
    fun `skips are counted rather than reported as failures`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val sources = (1..3).map { file(from, "f$it.txt", "new") }
        file(to, "f2.txt", "old")

        val result = FileStore.transferAll(sources, to, Transfer.COPY, null, always(Resolution.SKIP))

        assertTrue(result.ok)
        assertEquals(1, result.skipped)
        assertEquals(2, result.done.size)
        assertEquals("old", File(to, "f2.txt").readText())
    }

    @Test
    fun `keep both leaves the original alone`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val source = file(from, "main.py", "new")
        file(to, "main.py", "old")

        val result = FileStore.transferAll(listOf(source), to, Transfer.COPY, null, always(Resolution.KEEP_BOTH))

        assertTrue(result.ok)
        assertEquals("old", File(to, "main.py").readText())
        assertEquals("new", File(to, "main (2).py").readText())
    }

    @Test
    fun `a move inside one volume is renames, not reads`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val sources = (1..4).map { file(from, "f$it.bin", "x".repeat(4096)) }

        var reports = 0
        val result = FileStore.transferAll(sources, to, Transfer.MOVE, { reports++ })

        assertTrue(result.ok)
        assertEquals(4, result.done.size)
        // Nothing was carried, so nothing was measured: the only report is the one that
        // says it is over. A per-byte progress here would mean the bytes had travelled.
        assertEquals(1, reports)
    }

    @Test
    fun `progress runs across the batch rather than restarting per item`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val sources = (1..3).map { file(from, "f$it.bin", "y".repeat(200_000)) }

        val seen = mutableListOf<FileStore.Progress>()
        val result = FileStore.transferAll(sources, to, Transfer.COPY, { seen += it })

        assertTrue(result.ok)
        assertTrue(seen.isNotEmpty())
        val last = seen.last()
        // One total for the whole selection, and it is reached.
        assertEquals(600_000L, last.total)
        assertEquals(600_000L, last.done)
        // The counts only ever go up, which is what "across the batch" means.
        assertEquals(seen.map { it.done }.sorted(), seen.map { it.done })
        assertNotNull(seen.first { it.name != null }.name)
    }

    @Test
    fun `delete removes every item and reports item by item`() = runBlocking {
        val dir = temp.newFolder("dir")
        val files = (1..4).map { file(dir, "f$it.txt") }
        val tree = File(dir, "pkg").apply { mkdirs() }
        file(tree, "inner.txt")

        val seen = mutableListOf<FileStore.Progress>()
        val result = FileStore.deleteAll(files + tree) { seen += it }

        assertTrue(result.ok)
        assertEquals(5, result.done.size)
        files.forEach { assertFalse(it.exists()) }
        assertFalse(tree.exists())
        assertEquals(5L, seen.last().total)
        assertEquals(5L, seen.last().done)
    }

    @Test
    fun `deleting something already gone is not a failure`() = runBlocking {
        val dir = temp.newFolder("dir")
        val present = file(dir, "here.txt")
        val absent = File(dir, "not-here.txt")

        val result = FileStore.deleteAll(listOf(present, absent))

        assertTrue(result.ok)
        assertEquals(1, result.done.size)
        assertFalse(present.exists())
    }

    @Test
    fun `an empty selection does nothing at all`() = runBlocking {
        val to = temp.newFolder("to")
        assertTrue(FileStore.transferAll(emptyList(), to, Transfer.COPY).ok)
        assertTrue(FileStore.deleteAll(emptyList()).ok)
        assertEquals(0, to.listFiles()?.size)
    }
}
