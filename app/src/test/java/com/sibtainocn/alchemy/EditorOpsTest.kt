package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.editor.sora.EditorOps
import io.github.rosemoe.sora.text.Content
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The block operations, against the real buffer they run on.
 *
 * These are index arithmetic over a document, and the way they fail is by quietly
 * mangling a file rather than by throwing, so they are tested against a real [Content]
 * rather than against a stand-in that could disagree with it about line endings or column
 * counts. That is what the Robolectric dependency buys: `Content` reaches `android.text`
 * and `android.os`, so it cannot be built in a bare JVM test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EditorOpsTest {

    private fun buffer(vararg lines: String) = Content(lines.joinToString("\n"))

    private fun Content.lines(): List<String> = (0 until lineCount).map { getLineString(it) }

    // ---- Indent ----

    @Test
    fun `indenting adds one level to every line in range`() {
        val text = buffer("a", "b", "c")
        EditorOps.indentLines(text, 0, 1)
        assertEquals(listOf("    a", "    b", "c"), text.lines())
    }

    @Test
    fun `indenting an already indented line adds another level`() {
        val text = buffer("    a")
        EditorOps.indentLines(text, 0, 0)
        assertEquals(listOf("        a"), text.lines())
    }

    // ---- Dedent ----

    @Test
    fun `dedenting removes one level`() {
        val text = buffer("        a", "    b")
        EditorOps.dedentLines(text, 0, 1)
        assertEquals(listOf("    a", "b"), text.lines())
    }

    @Test
    fun `dedenting a line with no indent leaves it alone`() {
        val text = buffer("a", "    b")
        EditorOps.dedentLines(text, 0, 1)
        assertEquals(listOf("a", "b"), text.lines())
    }

    @Test
    fun `dedenting takes at most one level even from a deeply indented line`() {
        val text = buffer("            a")
        EditorOps.dedentLines(text, 0, 0)
        assertEquals(listOf("        a"), text.lines())
    }

    @Test
    fun `dedenting a partly indented line takes what is there`() {
        val text = buffer("  a")
        EditorOps.dedentLines(text, 0, 0)
        assertEquals(listOf("a"), text.lines())
    }

    // ---- Comments ----

    @Test
    fun `python comments with a hash`() {
        val text = buffer("x = 1", "y = 2")
        EditorOps.toggleComment(text, 0, 1, Language.PYTHON)
        assertEquals(listOf("# x = 1", "# y = 2"), text.lines())
    }

    @Test
    fun `kotlin comments with slashes`() {
        val text = buffer("val x = 1")
        EditorOps.toggleComment(text, 0, 0, Language.KOTLIN)
        assertEquals(listOf("// val x = 1"), text.lines())
    }

    @Test
    fun `commenting keeps the indent in front of the token`() {
        val text = buffer("    x = 1")
        EditorOps.toggleComment(text, 0, 0, Language.PYTHON)
        assertEquals(listOf("    # x = 1"), text.lines())
    }

    @Test
    fun `a fully commented block is uncommented`() {
        val text = buffer("# x = 1", "# y = 2")
        EditorOps.toggleComment(text, 0, 1, Language.PYTHON)
        assertEquals(listOf("x = 1", "y = 2"), text.lines())
    }

    @Test
    fun `a mixed block is commented rather than inverted`() {
        val text = buffer("# x = 1", "y = 2")
        EditorOps.toggleComment(text, 0, 1, Language.PYTHON)
        assertEquals(listOf("# # x = 1", "# y = 2"), text.lines())
    }

    @Test
    fun `uncommenting handles a token written without its space`() {
        val text = buffer("#x = 1")
        EditorOps.toggleComment(text, 0, 0, Language.PYTHON)
        assertEquals(listOf("x = 1"), text.lines())
    }

    @Test
    fun `blank lines are skipped, and do not decide the direction`() {
        val text = buffer("# a", "", "# b")
        EditorOps.toggleComment(text, 0, 2, Language.PYTHON)
        assertEquals(listOf("a", "", "b"), text.lines())
    }

    @Test
    fun `xml is left alone`() {
        val text = buffer("<a/>")
        EditorOps.toggleComment(text, 0, 0, Language.XML)
        assertEquals(listOf("<a/>"), text.lines())
    }

    // ---- Delete line ----

    @Test
    fun `deleting a line takes the break after it`() {
        val text = buffer("a", "b", "c")
        val landing = EditorOps.deleteLines(text, 1, 1)
        assertEquals(listOf("a", "c"), text.lines())
        assertEquals(1, landing)
    }

    @Test
    fun `deleting several lines takes all of them`() {
        val text = buffer("a", "b", "c", "d")
        EditorOps.deleteLines(text, 1, 2)
        assertEquals(listOf("a", "d"), text.lines())
    }

    @Test
    fun `deleting the last line leaves no blank line behind it`() {
        val text = buffer("a", "b")
        val landing = EditorOps.deleteLines(text, 1, 1)
        assertEquals(listOf("a"), text.lines())
        assertEquals(0, landing)
    }

    @Test
    fun `deleting the only line empties it rather than removing it`() {
        val text = buffer("a")
        val landing = EditorOps.deleteLines(text, 0, 0)
        assertEquals(listOf(""), text.lines())
        assertEquals(0, landing)
    }

    @Test
    fun `deleting the first line lands on what was the second`() {
        val text = buffer("a", "b", "c")
        val landing = EditorOps.deleteLines(text, 0, 0)
        assertEquals(listOf("b", "c"), text.lines())
        assertEquals(0, landing)
    }

    // ---- Undo ----

    @Test
    fun `a block operation is one undo step, not one per line`() {
        val text = buffer("a", "b", "c")
        EditorOps.indentLines(text, 0, 2)
        assertEquals(listOf("    a", "    b", "    c"), text.lines())
        text.undo()
        assertEquals(listOf("a", "b", "c"), text.lines())
    }

    @Test
    fun `uncommenting a block is also one undo step`() {
        val text = buffer("# a", "# b")
        EditorOps.toggleComment(text, 0, 1, Language.PYTHON)
        assertEquals(listOf("a", "b"), text.lines())
        text.undo()
        assertEquals(listOf("# a", "# b"), text.lines())
    }
}
