package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.syntax.TokenKind
import com.sibtainocn.alchemy.syntax.TokenSink
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A hole in a string is not string content.
 *
 * `f"{total}"` is a string with an expression in it, and drawing the whole thing in one
 * colour hides the only part that is code. What is pinned here is both halves of that: the
 * marks are reported apart from the string, and a literal that carries no holes - because
 * the language has none, or because this particular literal did not ask for them - is still
 * reported as one unbroken string. The second half is the one that goes wrong quietly: a
 * brace in a JSON string painted as an expression is a lie the reader has no way to check.
 */
class InterpolationScanTest {

    private fun sink(f: (Int, Int, TokenKind) -> Unit) = object : TokenSink {
        override fun token(start: Int, end: Int, kind: TokenKind, italic: Boolean, bold: Boolean) =
            f(start, end, kind)
    }

    /** The last kind reported for each character, which is how a sink reads it. */
    private fun kindsOf(text: String, lang: Language): Array<TokenKind?> {
        val out = arrayOfNulls<TokenKind>(text.length)
        Highlighter.scan(text, lang, sink { start, end, kind ->
            for (i in start.coerceAtLeast(0) until end.coerceAtMost(text.length)) out[i] = kind
        })
        return out
    }

    private fun kindAt(text: String, index: Int, lang: Language): TokenKind? = kindsOf(text, lang)[index]

    private fun kindOfFirst(text: String, needle: String, lang: Language): TokenKind? =
        kindsOf(text, lang)[text.indexOf(needle)]

    @Test
    fun `an f-string reports its braces apart from its text`() {
        val src = """msg = f"hi {name}!""""
        // The prefix and the quote are still the string.
        assertEquals(TokenKind.STRING, kindOfFirst(src, "f\"", Language.PYTHON))
        assertEquals(TokenKind.STRING, kindOfFirst(src, "hi", Language.PYTHON))
        assertEquals(TokenKind.INTERPOLATION, kindAt(src, src.indexOf('{'), Language.PYTHON))
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "name", Language.PYTHON))
        assertEquals(TokenKind.INTERPOLATION, kindAt(src, src.indexOf('}'), Language.PYTHON))
        // And the string closes as a string.
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('!'), Language.PYTHON))
    }

    @Test
    fun `a string without the prefix keeps its braces`() {
        val src = """tpl = "{not a hole}""""
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('{'), Language.PYTHON))
        assertEquals(TokenKind.STRING, kindOfFirst(src, "not", Language.PYTHON))
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('}'), Language.PYTHON))
    }

    @Test
    fun `a doubled brace is a brace, not a hole`() {
        val src = """f"{{literal}}""""
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('{'), Language.PYTHON))
        assertEquals(TokenKind.STRING, kindOfFirst(src, "literal", Language.PYTHON))
    }

    @Test
    fun `a format spec closes at the right brace`() {
        val src = """f"{total:.2f} done""""
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "total", Language.PYTHON))
        assertEquals(TokenKind.INTERPOLATION, kindAt(src, src.indexOf('}'), Language.PYTHON))
        // Everything after the hole is string again, including the closing quote.
        assertEquals(TokenKind.STRING, kindOfFirst(src, "done", Language.PYTHON))
        assertEquals(TokenKind.STRING, kindAt(src, src.lastIndexOf('"'), Language.PYTHON))
    }

    @Test
    fun `a nested brace does not end the hole early`() {
        val src = """f"{value:{width}}!""""
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "width", Language.PYTHON))
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('!'), Language.PYTHON))
    }

    @Test
    fun `a triple quoted f-string carries holes too`() {
        val src = "text = f\"\"\"hello {who}\nsecond line\"\"\"\nx = 1"
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "who", Language.PYTHON))
        assertEquals(TokenKind.STRING, kindOfFirst(src, "second", Language.PYTHON))
        // The block still closes, so what follows it is ordinary code.
        assertEquals(TokenKind.NUMBER, kindOfFirst(src, "1", Language.PYTHON))
    }

    @Test
    fun `an unclosed hole stops at the line end`() {
        val src = "f\"{oops\ny = 1"
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "oops", Language.PYTHON))
        // The next line is code, not the inside of a string that never closed.
        assertEquals(TokenKind.NUMBER, kindOfFirst(src, "1", Language.PYTHON))
    }

    @Test
    fun `kotlin templates report both the short and the braced form`() {
        val src = """val s = "hi ${'$'}name and ${'$'}{other.thing}!""""
        assertEquals(TokenKind.INTERPOLATION, kindAt(src, src.indexOf('$'), Language.KOTLIN))
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "name", Language.KOTLIN))
        assertEquals(TokenKind.STRING, kindOfFirst(src, " and ", Language.KOTLIN))
        assertEquals(TokenKind.TEXT, kindOfFirst(src, "other.thing", Language.KOTLIN))
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('!'), Language.KOTLIN))
    }

    @Test
    fun `javascript holes are for templates only`() {
        val template = "const a = `x ${'$'}{y} z`"
        assertEquals(TokenKind.TEXT, kindOfFirst(template, "y}", Language.JS))

        // The same text in quotes is a plain string: JavaScript does not interpolate one.
        val quoted = "const a = \"x ${'$'}{y} z\""
        assertEquals(TokenKind.STRING, kindAt(quoted, quoted.indexOf('$'), Language.JS))
        assertEquals(TokenKind.STRING, kindOfFirst(quoted, "y}", Language.JS))
    }

    @Test
    fun `a c string keeps its braces`() {
        // Neither C nor Rust announces an interpolated literal, so nothing here is a hole.
        val src = """char *j = "{\"a\": 1}";"""
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('{'), Language.C_LIKE))
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf('}'), Language.C_LIKE))
    }

    @Test
    fun `json braces in a string are string, and outside one are punctuation`() {
        val src = """{"tpl": "{x}"}"""
        assertEquals(TokenKind.PUNCTUATION, kindAt(src, 0, Language.JSON))
        assertEquals(TokenKind.STRING, kindAt(src, src.indexOf("\"{x}\"") + 1, Language.JSON))
    }

    @Test
    fun `markdown code is not painted as a string`() {
        val src = "text `code` here\n```\nfenced\n```\n"
        assertEquals(TokenKind.CODE_SPAN, kindAt(src, src.indexOf('`'), Language.MARKDOWN))
        assertEquals(TokenKind.CODE_SPAN, kindOfFirst(src, "fenced", Language.MARKDOWN))
    }

    @Test
    fun `every reported range stays inside the text`() {
        val samples = listOf(
            """f"{a}{b}" + f'{c:{d}}'""" to Language.PYTHON,
            "\"\"\"f{x}\"\"\"" to Language.PYTHON,
            "f\"{" to Language.PYTHON,
            "`${'$'}{" to Language.JS,
            "\"${'$'}" to Language.KOTLIN,
            "echo \"${'$'}{HOME}\"" to Language.SHELL,
        )
        samples.forEach { (src, lang) ->
            Highlighter.scan(src, lang, sink { start, end, _ ->
                assert(start >= 0 && end <= src.length) { "range $start..$end escapes \"$src\"" }
                assert(start <= end) { "range $start..$end is inverted in \"$src\"" }
            })
        }
    }
}
