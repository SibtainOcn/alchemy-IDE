package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.syntax.TokenKind
import com.sibtainocn.alchemy.ui.editor.sora.SoraSpanSink
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every line of a document must exist in the span structure.
 *
 * The renderer asks for the spans on whichever line it is about to draw, so a structure
 * shorter than the document is an index out of bounds in the middle of a frame. That is
 * how this arrived: a file past the highlighting cap carries one uniform style, the
 * builder adds nothing when the style has not changed - not even the line it was asked
 * for - and a hundred thousand lines built exactly one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SoraSpanSinkTest {

    private fun stylesFor(text: String, paint: (SoraSpanSink) -> Unit = {}) =
        SoraSpanSink(text.length).also(paint).toStyles(text)

    /** True when the structure can answer for every line from 0 to [lastLine]. */
    private fun covers(text: String, lastLine: Int, paint: (SoraSpanSink) -> Unit = {}): Boolean {
        val reader = stylesFor(text, paint).spans.read()
        for (line in 0..lastLine) {
            val spans = runCatching { reader.getSpansOnLine(line) }.getOrNull()
            if (spans.isNullOrEmpty()) return false
        }
        return true
    }

    @Test
    fun `an unhighlighted document has a span for every line`() {
        // Nothing is painted, so every line shares one style - the case that built one line.
        val text = (1..500).joinToString("\n") { "line $it" }
        assertTrue("a line was missing its spans", covers(text, 499))
    }

    @Test
    fun `blank lines still get a span`() {
        assertTrue(covers("a\n\n\n\nb", 4))
    }

    @Test
    fun `a document coloured only at the top still covers the bottom`() {
        val text = (1..200).joinToString("\n") { "def f$it():" }
        assertTrue(
            covers(text, 199) { sink ->
                sink.token(0, 3, TokenKind.KEYWORD, italic = false, bold = false)
            }
        )
    }

    @Test
    fun `a fully coloured document covers every line`() {
        val text = (1..50).joinToString("\n") { "def f():" }
        assertTrue(
            covers(text, 49) { sink ->
                var at = 0
                repeat(50) {
                    sink.token(at, at + 3, TokenKind.KEYWORD, italic = false, bold = false)
                    at += "def f():".length + 1
                }
            }
        )
    }

    @Test
    fun `an empty document still builds`() {
        assertTrue(covers("", 0))
    }

    @Test
    fun `a document that is one blank line still builds`() {
        assertTrue(covers("\n", 1))
    }
}
