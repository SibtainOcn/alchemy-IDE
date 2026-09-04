package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.exec.InstallPlanner
import com.sibtainocn.alchemy.exec.Runtime
import com.sibtainocn.alchemy.exec.lastMeaningfulLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the installer decides before it spends anyone's data. Reinstalling something that
 * is already there is a quarter of a gigabyte for nothing, and an order that puts the
 * longest download first is several minutes of a progress bar before anything works.
 */
class InstallPlannerTest {

    @Test
    fun `what is already installed is not installed again`() {
        val plan = InstallPlanner.plan(
            selected = setOf(Runtime.PYTHON, Runtime.GO),
            present = setOf(Runtime.GO),
        )
        assertEquals(listOf(Runtime.PYTHON), plan)
    }

    @Test
    fun `nothing to do produces an empty plan rather than a no-op install`() {
        assertTrue(
            InstallPlanner.plan(setOf(Runtime.PYTHON), setOf(Runtime.PYTHON)).isEmpty()
        )
        assertTrue(InstallPlanner.plan(emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `the smallest download goes first`() {
        // Something works within a minute, instead of after the longest download in the set.
        val plan = InstallPlanner.plan(Runtime.entries.toSet(), emptySet())
        assertEquals(listOf(Runtime.PYTHON, Runtime.C, Runtime.GO), plan)
    }

    @Test
    fun `the order is the same every time it is asked for`() {
        val once = InstallPlanner.plan(Runtime.entries.toSet(), emptySet())
        val again = InstallPlanner.plan(Runtime.entries.reversed().toSet(), emptySet())
        assertEquals("A plan that shuffles cannot be reasoned about", once, again)
    }

    @Test
    fun `a package wanted by two runtimes is only asked for once`() {
        // apt refuses a command that names the same package twice, so this would fail the
        // install rather than install anything.
        val packages = InstallPlanner.packagesFor(Runtime.entries)
        assertEquals(packages.size, packages.distinct().size)
        assertTrue(packages.containsAll(Runtime.PYTHON.packages))
        assertTrue(packages.containsAll(Runtime.C.packages))
    }

    @Test
    fun `the total is what the user is about to download`() {
        val plan = listOf(Runtime.PYTHON, Runtime.C)
        assertEquals(
            Runtime.PYTHON.approximateMb + Runtime.C.approximateMb,
            InstallPlanner.totalMb(plan),
        )
        assertEquals(0, InstallPlanner.totalMb(emptyList()))
    }

    @Test
    fun `a failure is reported by its last useful line, not its whole log`() {
        val log = """
            Reading package lists...
            Building dependency tree...

            E: Unable to locate package golang
        """.trimIndent()
        assertEquals("E: Unable to locate package golang", lastMeaningfulLine(log, "fallback"))
    }

    @Test
    fun `silence falls back to something worth reading`() {
        assertEquals("fallback", lastMeaningfulLine("", "fallback"))
        assertEquals("fallback", lastMeaningfulLine("\n\n   \n", "fallback"))
    }

    @Test
    fun `a very long line is cut rather than filling the dialog`() {
        val line = "x".repeat(1_000)
        assertTrue(lastMeaningfulLine(line, "fallback").length <= 300)
    }
}
