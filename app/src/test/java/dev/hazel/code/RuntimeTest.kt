package dev.hazel.code

import dev.hazel.code.exec.Runtime
import dev.hazel.code.exec.ShellQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Run button is only as good as what it hands the shell. A path that loses half of
 * itself to a space, or a C program compiled somewhere Android will not let it run, both
 * fail in ways that look like the language is broken rather than the command.
 */
class RuntimeTest {

    @Test
    fun `each supported extension resolves to its runtime`() {
        assertEquals(Runtime.PYTHON, Runtime.forFile("main.py"))
        assertEquals(Runtime.C, Runtime.forFile("hello.c"))
        assertEquals(Runtime.GO, Runtime.forFile("server.go"))
    }

    @Test
    fun `extensions are matched whatever their case`() {
        assertEquals(Runtime.PYTHON, Runtime.forFile("MAIN.PY"))
        assertEquals(Runtime.C, Runtime.forFile("Hello.C"))
    }

    @Test
    fun `a language we do not run has no runtime rather than a broken one`() {
        // These stay editable and highlighted; they simply get no Run button. Offering
        // one that fails would be worse than not offering it.
        listOf("app.js", "Main.java", "index.html", "style.css", "script.rb", "notes.md")
            .forEach { assertNull(it, Runtime.forFile(it)) }
    }

    @Test
    fun `a file with no extension is not guessed at`() {
        assertNull(Runtime.forFile("Makefile"))
        assertNull(Runtime.forFile("LICENSE"))
        assertNull(Runtime.forFile(""))
    }

    @Test
    fun `a dotfile is not read as an extension`() {
        // ".py" as a whole filename is a hidden file, not a Python script.
        assertNull(Runtime.forFile(".py"))
        assertNull(Runtime.forFile(".gitignore"))
        assertEquals(Runtime.PYTHON, Runtime.forFile(".hidden.py"))
    }

    @Test
    fun `a whole path resolves by its filename`() {
        assertEquals(Runtime.GO, Runtime.forFile("/storage/emulated/0/src/main.go"))
        assertNull(Runtime.forFile("/storage/emulated/0/py.files/README"))
    }

    @Test
    fun `a path with a space survives being handed to the shell`() {
        val command = Runtime.PYTHON.commandFor("/sdcard/my code/main.py")
        assertEquals("python '/sdcard/my code/main.py'", command)
    }

    @Test
    fun `a path with a quote in it cannot break out of its quoting`() {
        // Single quotes cannot be escaped inside single quotes, so the value has to leave
        // the quoting, contribute one quote, and go back in.
        assertEquals("""'it'\''s.py'""", ShellQuote.single("it's.py"))
    }

    @Test
    fun `C compiles into Termux storage, not next to the source`() {
        // Android mounts shared storage non-executable. A binary written beside the file
        // would compile and then be refused at the moment it was run.
        val command = Runtime.C.commandFor("/sdcard/code/hello.c")
        assertTrue("Compiles the source", command.startsWith("clang '/sdcard/code/hello.c'"))
        assertTrue("Writes the program into TMPDIR", command.contains("TMPDIR"))
        assertTrue(
            "Falls back to a real path when TMPDIR is unset, rather than to the root",
            command.contains(Runtime.TERMUX_TMP),
        )
        assertTrue("Runs it only if the compile succeeded", command.contains("&&"))
    }

    @Test
    fun `C installs a toolchain, not just a compiler`() {
        // clang alone cannot get from source to a program: it needs an assembler and a
        // linker, which is what binutils carries.
        assertTrue(Runtime.C.packages.containsAll(listOf("clang", "binutils")))
        assertTrue(Runtime.C.packages.size > 1)
    }

    @Test
    fun `every runtime can state how to install itself and how to check for itself`() {
        Runtime.entries.forEach { runtime ->
            assertTrue(runtime.packages.isNotEmpty())
            assertTrue(runtime.probe.isNotBlank())
            assertTrue(runtime.extensions.isNotEmpty())
            assertTrue(
                "${runtime.label} install command",
                runtime.installCommand.startsWith("pkg install -y "),
            )
            runtime.packages.forEach {
                assertTrue("${runtime.label} lists $it", runtime.installCommand.contains(it))
            }
        }
    }

    @Test
    fun `no two runtimes claim the same extension`() {
        val seen = mutableSetOf<String>()
        Runtime.entries.forEach { runtime ->
            runtime.extensions.forEach {
                assertTrue("$it is claimed twice", seen.add(it))
            }
        }
    }
}
