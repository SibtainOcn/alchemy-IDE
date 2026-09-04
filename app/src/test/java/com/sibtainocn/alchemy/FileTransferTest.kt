package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.FileKind
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.FileStore.Decision
import com.sibtainocn.alchemy.data.FileStore.Resolution
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The copy and move engine, exercised on a real filesystem.
 *
 * Everything here runs on the JVM against a temporary directory, which is the same
 * `java.io.File` API the device uses, so the parts worth testing - naming, conflicts,
 * merges, what is left behind - are the parts actually under test.
 */
class FileTransferTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(parent: File, name: String, body: String = name): File =
        File(parent, name).apply { parentFile?.mkdirs(); writeText(body) }

    private fun always(resolution: Resolution, all: Boolean = false):
        suspend (FileStore.Conflict) -> Decision = { Decision(resolution, all) }

    // ---- Names ----

    @Test
    fun `a free name is left alone`() {
        val dir = temp.newFolder("dir")
        assertEquals("main.py", FileStore.freeNameIn(dir, "main.py"))
    }

    @Test
    fun `a taken name is numbered before the extension`() {
        val dir = temp.newFolder("dir")
        file(dir, "main.py")
        assertEquals("main (2).py", FileStore.freeNameIn(dir, "main.py"))

        file(dir, "main (2).py")
        assertEquals("main (3).py", FileStore.freeNameIn(dir, "main.py"))
    }

    @Test
    fun `a leading dot is part of the name, not an extension`() {
        val dir = temp.newFolder("dir")
        file(dir, ".gitignore")
        assertEquals(".gitignore (2)", FileStore.freeNameIn(dir, ".gitignore"))
    }

    @Test
    fun `an extensionless name is numbered at the end`() {
        val dir = temp.newFolder("dir")
        file(dir, "LICENSE")
        assertEquals("LICENSE (2)", FileStore.freeNameIn(dir, "LICENSE"))
    }

    // ---- Containment ----

    @Test
    fun `a folder contains itself and its descendants`() {
        val root = temp.newFolder("root")
        val deep = File(root, "a/b/c").apply { mkdirs() }
        assertTrue(FileStore.isInside(root, root))
        assertTrue(FileStore.isInside(deep, root))
        assertFalse(FileStore.isInside(root, deep))
    }

    @Test
    fun `pasting a folder into itself is refused`() = runBlocking {
        val root = temp.newFolder("root")
        val inner = File(root, "inner").apply { mkdirs() }
        val result = FileStore.copyInto(root, inner)
        assertTrue(result.isFailure)
    }

    // ---- Copy ----

    @Test
    fun `copying a file leaves both`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val source = file(from, "notes.txt", "hello")

        val landed = FileStore.copyInto(source, to).getOrThrow()

        assertEquals("notes.txt", landed?.name)
        assertEquals("hello", File(to, "notes.txt").readText())
        assertTrue(source.exists())
    }

    @Test
    fun `copying a tree carries every file in it`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val tree = File(from, "src").apply { mkdirs() }
        file(tree, "a.py", "a")
        file(File(tree, "nested"), "b.py", "b")

        FileStore.copyInto(tree, to).getOrThrow()

        assertEquals("a", File(to, "src/a.py").readText())
        assertEquals("b", File(to, "src/nested/b.py").readText())
    }

    @Test
    fun `an empty file still arrives`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val source = file(from, "empty.txt", "")

        FileStore.copyInto(source, to).getOrThrow()

        assertTrue(File(to, "empty.txt").exists())
        assertEquals(0L, File(to, "empty.txt").length())
    }

    @Test
    fun `progress reaches the end`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        file(from, "big.bin", "x".repeat(200_000))

        var last: FileStore.Progress? = null
        FileStore.copyInto(File(from, "big.bin"), to, onProgress = { last = it }).getOrThrow()

        assertEquals(200_000L, last?.total)
        assertEquals(200_000L, last?.done)
        assertEquals(1f, last?.fraction)
    }

    // ---- Move ----

    @Test
    fun `moving a file leaves nothing behind`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val source = file(from, "notes.txt", "hello")

        FileStore.moveInto(source, to).getOrThrow()

        assertFalse(source.exists())
        assertEquals("hello", File(to, "notes.txt").readText())
    }

    @Test
    fun `moving a tree takes the whole thing`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val tree = File(from, "src").apply { mkdirs() }
        file(File(tree, "nested"), "b.py", "b")

        FileStore.moveInto(tree, to).getOrThrow()

        assertFalse(tree.exists())
        assertEquals("b", File(to, "src/nested/b.py").readText())
    }

    // ---- Conflicts ----

    @Test
    fun `skip leaves what was already there`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        file(from, "a.txt", "incoming")
        file(to, "a.txt", "original")

        val landed = FileStore.copyInto(File(from, "a.txt"), to, resolve = always(Resolution.SKIP)).getOrThrow()

        assertEquals(null, landed)
        assertEquals("original", File(to, "a.txt").readText())
    }

    @Test
    fun `overwrite replaces it`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        file(from, "a.txt", "incoming")
        file(to, "a.txt", "original")

        FileStore.copyInto(File(from, "a.txt"), to, resolve = always(Resolution.OVERWRITE)).getOrThrow()

        assertEquals("incoming", File(to, "a.txt").readText())
    }

    @Test
    fun `keep both lands beside it`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        file(from, "a.txt", "incoming")
        file(to, "a.txt", "original")

        val landed = FileStore.copyInto(File(from, "a.txt"), to, resolve = always(Resolution.KEEP_BOTH)).getOrThrow()

        assertEquals("a (2).txt", landed?.name)
        assertEquals("original", File(to, "a.txt").readText())
        assertEquals("incoming", File(to, "a (2).txt").readText())
    }

    @Test
    fun `cancelling stops the run`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        file(from, "a.txt", "incoming")
        file(to, "a.txt", "original")

        val result = FileStore.copyInto(File(from, "a.txt"), to, resolve = always(Resolution.CANCEL))

        assertTrue(result.exceptionOrNull() is FileStore.TransferAborted)
        assertEquals("original", File(to, "a.txt").readText())
    }

    @Test
    fun `two folders of the same name merge`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val incoming = File(from, "src").apply { mkdirs() }
        file(incoming, "new.py", "new")
        file(incoming, "shared.py", "incoming")

        val existing = File(to, "src").apply { mkdirs() }
        file(existing, "old.py", "old")
        file(existing, "shared.py", "original")

        FileStore.copyInto(incoming, to, resolve = always(Resolution.OVERWRITE, all = true)).getOrThrow()

        // Everything that was there survives, everything new arrives, and the one name
        // they share follows the standing decision.
        assertEquals("old", File(to, "src/old.py").readText())
        assertEquals("new", File(to, "src/new.py").readText())
        assertEquals("incoming", File(to, "src/shared.py").readText())
    }

    @Test
    fun `a merge asks about each clash until told to apply to all`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val incoming = File(from, "src").apply { mkdirs() }
        val existing = File(to, "src").apply { mkdirs() }
        listOf("a.py", "b.py", "c.py").forEach {
            file(incoming, it, "incoming")
            file(existing, it, "original")
        }

        var asked = 0
        FileStore.copyInto(incoming, to) {
            asked++
            // The folder itself is the first clash; tick "all" on the first file inside.
            Decision(Resolution.OVERWRITE, all = asked >= 2)
        }.getOrThrow()

        assertEquals(2, asked)
        assertEquals("incoming", File(to, "src/c.py").readText())
    }

    @Test
    fun `a skipped file inside a moved folder keeps the folder`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        val incoming = File(from, "src").apply { mkdirs() }
        file(incoming, "keep.py", "incoming")
        file(incoming, "fresh.py", "fresh")

        val existing = File(to, "src").apply { mkdirs() }
        file(existing, "keep.py", "original")

        FileStore.moveInto(incoming, to) { conflict ->
            // Merge the folders, skip the file they share.
            Decision(if (conflict.isMerge) Resolution.OVERWRITE else Resolution.SKIP)
        }.getOrThrow()

        assertEquals("original", File(to, "src/keep.py").readText())
        assertEquals("fresh", File(to, "src/fresh.py").readText())
        // The skipped file never moved, so its folder cannot have gone either.
        assertTrue(File(from, "src/keep.py").exists())
        assertFalse(File(from, "src/fresh.py").exists())
    }

    @Test
    fun `nothing is asked when there is nothing in the way`() = runBlocking {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        file(from, "a.txt", "incoming")

        var asked = 0
        FileStore.copyInto(File(from, "a.txt"), to) { asked++; Decision(Resolution.SKIP) }.getOrThrow()

        assertEquals(0, asked)
    }

    // ---- Kinds ----

    @Test
    fun `file kinds are read off the extension`() {
        assertEquals(FileKind.CODE, FileKind.of("main.py"))
        assertEquals(FileKind.CODE, FileKind.of("Screen.kt"))
        assertEquals(FileKind.IMAGE, FileKind.of("shot.PNG"))
        assertEquals(FileKind.VIDEO, FileKind.of("clip.mp4"))
        assertEquals(FileKind.AUDIO, FileKind.of("recitation.opus"))
        assertEquals(FileKind.ARCHIVE, FileKind.of("bundle.zip"))
        assertEquals(FileKind.PDF, FileKind.of("form.pdf"))
        assertEquals(FileKind.APP, FileKind.of("build.apk"))
        assertEquals(FileKind.BINARY, FileKind.of("libc.so"))
        assertEquals(FileKind.TEXT, FileKind.of("notes.txt"))
        assertEquals(FileKind.TEXT, FileKind.of("no-extension"))
    }
}
