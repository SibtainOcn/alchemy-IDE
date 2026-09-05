package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.syntax.TokenKind
import com.sibtainocn.alchemy.syntax.TokenSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner reports kinds, not colours.
 *
 * [HighlighterTest] pins what those kinds end up looking like once a palette has been
 * applied. This pins the layer under it: that the same scan can be handed to any renderer,
 * which is what lets the editor and the Markdown preview share one pass and what makes a
 * change of theme a repaint rather than a rescan.
 */
class HighlighterScanTest {

    /** [TokenSink] carries default arguments, so it is not a SAM type; this is the shim. */
    private fun sink(f: (Int, Int, TokenKind) -> Unit) = object : TokenSink {
        override fun token(start: Int, end: Int, kind: TokenKind, italic: Boolean, bold: Boolean) =
            f(start, end, kind)
    }

    /** Records the last kind reported for each character, which is how a sink reads it. */
    private fun kindsOf(text: String, lang: Language): Array<TokenKind?> {
        val out = arrayOfNulls<TokenKind>(text.length)
        Highlighter.scan(text, lang, sink { start, end, kind ->
            for (i in start.coerceAtLeast(0) until end.coerceAtMost(text.length)) out[i] = kind
        })
        return out
    }

    private fun kindOfFirst(text: String, needle: String, lang: Language): TokenKind? =
        kindsOf(text, lang)[text.indexOf(needle)]

    @Test
    fun `python keywords are reported as keywords`() {
        val src = "def f():\n    return 1"
        assertEquals(TokenKind.KEYWORD, kindOfFirst(src, "def", Language.PYTHON))
        assertEquals(TokenKind.KEYWORD, kindOfFirst(src, "return", Language.PYTHON))
    }

    @Test
    fun `a defined name is a function`() {
        assertEquals(TokenKind.FUNCTION, kindOfFirst("def parse_logs():", "parse_logs", Language.PYTHON))
    }

    @Test
    fun `a hash inside a string is not a comment`() {
        val src = """x = "a # b""""
        assertEquals(TokenKind.STRING, kindOfFirst(src, "#", Language.PYTHON))
    }

    @Test
    fun `a quote inside a comment does not open a string`() {
        val src = "# it's fine\ny = 1"
        assertEquals(TokenKind.COMMENT, kindOfFirst(src, "'", Language.PYTHON))
        // The line after the comment is ordinary code, not the inside of a string.
        assertEquals(TokenKind.NUMBER, kindOfFirst(src, "1", Language.PYTHON))
    }

    @Test
    fun `nothing is reported outside the text`() {
        val src = "def f():\n    return 1"
        Highlighter.scan(src, Language.PYTHON, sink { start, end, _ ->
            assertTrue("range $start..$end escapes the text", start >= 0 && end <= src.length)
            assertTrue("range $start..$end is inverted", start <= end)
        })
    }

    @Test
    fun `plain text reports nothing at all`() {
        var reported = 0
        Highlighter.scan("just words\nand more", Language.PLAIN, sink { _, _, _ -> reported++ })
        assertEquals(0, reported)
    }

    @Test
    fun `text past the cap is left alone`() {
        val huge = "def f():\n".repeat(Highlighter.MAX_HIGHLIGHT_CHARS / 9 + 10)
        var reported = 0
        Highlighter.scan(huge, Language.PYTHON, sink { _, _, _ -> reported++ })
        assertEquals(0, reported)
    }
}
