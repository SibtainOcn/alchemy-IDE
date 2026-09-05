package com.sibtainocn.alchemy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.ui.theme.AlchemyAccents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's whole reason for existing is that it stays correct where regex
 * highlighting breaks: a hash inside a string, a quote inside a comment, a triple-quoted
 * block that spans lines. Those are the cases pinned here.
 */
class HighlighterTest {

    private val a = AlchemyAccents()

    private fun colorAt(text: String, index: Int, lang: Language): Color? {
        val out = Highlighter.highlight(text, lang, a)
        // Later spans win in Compose, so the last one covering the index is what shows.
        return out.spanStyles.lastOrNull { index >= it.start && index < it.end }?.item?.color
    }

    private fun colorOfFirst(text: String, needle: String, lang: Language): Color? =
        colorAt(text, text.indexOf(needle), lang)

    @Test
    fun `python keywords are monokai pink`() {
        assertEquals(a.keyword, colorOfFirst("def f():\n    return 1", "def", Language.PYTHON))
        assertEquals(a.keyword, colorOfFirst("def f():\n    return 1", "return", Language.PYTHON))
    }

    @Test
    fun `a defined function name is green`() {
        assertEquals(a.function, colorOfFirst("def parse_logs():", "parse_logs", Language.PYTHON))
    }

    @Test
    fun `a call is green too`() {
        assertEquals(a.function, colorOfFirst("print(x)", "print", Language.PYTHON))
    }

    @Test
    fun `self is orange, not a plain identifier`() {
        assertEquals(a.selfRef, colorOfFirst("self.name = 1", "self", Language.PYTHON))
    }

    @Test
    fun `numbers are purple`() {
        assertEquals(a.number, colorOfFirst("count = 42", "42", Language.PYTHON))
    }

    @Test
    fun `a hash inside a string does not start a comment`() {
        val src = """url = "http://x/#anchor" """
        // The '#' sits inside the string, so it must carry the string colour.
        assertEquals(a.string, colorAt(src, src.indexOf('#'), Language.PYTHON))
    }

    @Test
    fun `a quote inside a comment does not open a string`() {
        val src = "# it's fine\nx = 1"
        assertEquals(a.comment, colorAt(src, src.indexOf('\''), Language.PYTHON))
        // The code after the comment line is still scanned normally.
        assertEquals(a.number, colorOfFirst(src, "1", Language.PYTHON))
    }

    @Test
    fun `triple quoted blocks span lines`() {
        val src = "\"\"\"\ndoc: def not_a_keyword\n\"\"\"\nx = 1"
        assertEquals(a.string, colorAt(src, src.indexOf("def"), Language.PYTHON))
        assertEquals(a.number, colorOfFirst(src, "1", Language.PYTHON))
    }

    @Test
    fun `an unterminated quote does not swallow the rest of the file`() {
        val src = "x = \"oops\ny = 1"
        assertEquals(a.number, colorOfFirst(src, "1", Language.PYTHON))
    }

    @Test
    fun `f-string prefix is part of the string span`() {
        val src = """msg = f"hi {name}""""
        assertEquals(a.string, colorAt(src, src.indexOf('f'), Language.PYTHON))
    }

    @Test
    fun `an f-string hole is purple around white, inside a yellow string`() {
        val src = """msg = f"hi {name}""""
        assertEquals(a.string, colorAt(src, src.indexOf("hi"), Language.PYTHON))
        assertEquals(a.interpolation, colorAt(src, src.indexOf('{'), Language.PYTHON))
        assertEquals(a.codeText, colorOfFirst(src, "name", Language.PYTHON))
        assertNotEquals(a.string, colorAt(src, src.indexOf('{'), Language.PYTHON))
    }

    @Test
    fun `the string colour is not spent on markdown code`() {
        // Yellow means a quoted string. A fenced block is raw text and carries its own.
        val src = "a `snippet` here"
        assertEquals(a.codeSpan, colorAt(src, src.indexOf('`'), Language.MARKDOWN))
        assertNotEquals(a.string, colorAt(src, src.indexOf('`'), Language.MARKDOWN))
    }

    @Test
    fun `decorators are highlighted whole`() {
        val src = "@property\ndef x(self): pass"
        assertEquals(a.decorator, colorAt(src, src.indexOf("propert"), Language.PYTHON))
    }

    @Test
    fun `json keys and values are told apart`() {
        val src = """{"name": "alchemy"}"""
        assertEquals(a.function, colorAt(src, src.indexOf("\"name\""), Language.JSON))
        assertEquals(a.string, colorAt(src, src.indexOf("\"alchemy\""), Language.JSON))
    }

    @Test
    fun `c overrides replace the monokai defaults`() {
        val src = """int n = 42; char *s = "x";"""
        // The author's own VS Code rules: white numerics, orange strings.
        assertEquals(Color(0xFFF9F5F5), colorOfFirst(src, "42", Language.C_LIKE))
        assertNotEquals(a.number, colorOfFirst(src, "42", Language.C_LIKE))
        assertEquals(Color(0xFFFF8C00), colorAt(src, src.indexOf("\"x\""), Language.C_LIKE))
    }

    @Test
    fun `xml tags attributes and values get separate colours`() {
        val src = """<item name="x">text</item>"""
        assertEquals(a.keyword, colorOfFirst(src, "item", Language.XML))
        assertEquals(a.builtin, colorOfFirst(src, "name", Language.XML))
        assertEquals(a.string, colorAt(src, src.indexOf("\"x\""), Language.XML))
    }

    @Test
    fun `plain text is returned untouched`() {
        val out = Highlighter.highlight("just words", Language.PLAIN, a)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `highlighting is skipped past the size cap`() {
        val huge = "x = 1\n".repeat(Highlighter.MAX_HIGHLIGHT_CHARS / 3)
        val out = Highlighter.highlight(huge, Language.PYTHON, a)
        assertTrue(huge.length > Highlighter.MAX_HIGHLIGHT_CHARS)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `output text always matches the input exactly`() {
        // The editor relies on this: the visual transformation must not change offsets.
        val samples = listOf(
            "def f(): return '\\''" to Language.PYTHON,
            "<a href=\"x\">y</a>" to Language.XML,
            "{\"a\": [1, 2, null]}" to Language.JSON,
            "# heading\n- item\n```py\nx=1\n```" to Language.MARKDOWN,
            "int main() { /* c */ return 0; }" to Language.C_LIKE,
        )
        samples.forEach { (src, lang) ->
            assertEquals(AnnotatedString(src).text, Highlighter.highlight(src, lang, a).text)
        }
    }
}
