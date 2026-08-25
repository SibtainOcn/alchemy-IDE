package dev.hazel.code

import dev.hazel.code.exec.Console
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A command runs in its own process and forgets everything when it ends, so the terminal
 * has to remember where it is. Getting that wrong sends every command after a `cd` to the
 * wrong place, which looks like the file is missing rather than like the terminal is lost.
 */
class ConsoleTest {

    private val home = "/data/data/com.termux/files/home"

    // ---- Recognising a directory change ----

    @Test
    fun `a bare cd is taken over`() {
        assertEquals("src", Console.cdTarget("cd src"))
        assertEquals("/tmp", Console.cdTarget("  cd /tmp  "))
    }

    @Test
    fun `cd with nothing after it means home`() {
        assertEquals("~", Console.cdTarget("cd"))
    }

    @Test
    fun `a quoted path keeps its spaces`() {
        assertEquals("my code", Console.cdTarget("cd 'my code'"))
        assertEquals("my code", Console.cdTarget("cd \"my code\""))
    }

    @Test
    fun `a cd inside a longer command is left to the shell`() {
        // Here the cd correctly applies to that command and no further, which is what the
        // person writing it meant. Taking it over would change what their line does.
        assertNull(Console.cdTarget("cd build && make"))
        assertNull(Console.cdTarget("cd src; ls"))
        assertNull(Console.cdTarget("cd $(cat where.txt)"))
    }

    @Test
    fun `a command that merely starts with the letters cd is not a cd`() {
        assertNull(Console.cdTarget("cdrom"))
        assertNull(Console.cdTarget("cdda2wav"))
        assertNull(Console.cdTarget("ls"))
    }

    // ---- Working out where it lands ----

    @Test
    fun `a relative path is resolved against where we are`() {
        assertEquals("/sdcard/code/src", Console.resolve("/sdcard/code", "src", home))
    }

    @Test
    fun `dot dot goes up one`() {
        assertEquals("/sdcard", Console.resolve("/sdcard/code", "..", home))
        assertEquals("/sdcard/other", Console.resolve("/sdcard/code", "../other", home))
    }

    @Test
    fun `an absolute path ignores where we are`() {
        assertEquals("/tmp", Console.resolve("/sdcard/code", "/tmp", home))
    }

    @Test
    fun `tilde is the runner's home, not ours`() {
        assertEquals(home, Console.resolve("/sdcard/code", "~", home))
        assertEquals("$home/notes", Console.resolve("/sdcard/code", "~/notes", home))
    }

    @Test
    fun `climbing past the root stays at the root`() {
        assertEquals("/", Console.resolve("/sdcard", "../../../..", home))
    }

    @Test
    fun `stray slashes and dots are tidied away`() {
        assertEquals("/sdcard/code", Console.normalise("/sdcard//./code/"))
        assertEquals("/sdcard/code", Console.normalise("/sdcard/tmp/../code"))
    }

    // ---- Summarising a finished command ----

    @Test
    fun `a successful command does not announce its exit code`() {
        // Zero on every single line is noise, and noise is what stops a real exit code
        // from being noticed.
        val summary = Console.summarise(exitCode = 0, millis = 420)
        assertTrue(summary.contains("done"))
        assertTrue("A successful run must not report a code", !summary.contains("exit"))
    }

    @Test
    fun `a failure leads with the code that explains it`() {
        assertTrue(Console.summarise(exitCode = 1, millis = 90).startsWith("exit 1"))
        assertTrue(Console.summarise(exitCode = 127, millis = 90).startsWith("exit 127"))
    }

    @Test
    fun `time is read in the unit that suits it`() {
        assertTrue(Console.summarise(0, 420).contains("420ms"))
        assertTrue(Console.summarise(0, 4_200).contains("4.2s"))
    }

    @Test
    fun `a command that never ran says so`() {
        assertEquals("did not run", Console.summarise(exitCode = null, millis = 10))
    }
}
