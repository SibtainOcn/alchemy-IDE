package dev.hazel.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.hazel.code.data.Language
import dev.hazel.code.ui.editor.SmartEdit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The typing rules are the one part of the editor that is invisible when it works and
 * infuriating when it does not, so they are pinned here rather than trusted to manual
 * poking. Each case models what the IME actually hands back: the text with the new
 * character already inserted, and the caret sitting after it.
 */
class SmartEditTest {

    /** Simulates typing [ch] at the caret of [before]. */
    private fun type(before: TextFieldValue, ch: Char, lang: Language = Language.PYTHON): TextFieldValue {
        val at = before.selection.min
        val next = TextFieldValue(
            text = before.text.replaceRange(at, before.selection.max, ch.toString()),
            selection = TextRange(at + 1),
        )
        return SmartEdit.onValueChange(before, next, lang, autoPair = true)
    }

    private fun at(text: String, caret: Int) = TextFieldValue(text, TextRange(caret))

    private fun caretMarked(v: TextFieldValue): String =
        v.text.substring(0, v.selection.start) + "|" + v.text.substring(v.selection.start)

    @Test
    fun `opening bracket inserts its partner and keeps the caret inside`() {
        val out = type(at("print", 5), '(')
        assertEquals("print(|)", caretMarked(out))
    }

    @Test
    fun `typing the closing bracket steps over the auto-inserted one`() {
        val out = type(at("print()", 6), ')')
        assertEquals("print()|", caretMarked(out))
    }

    @Test
    fun `quotes pair up in empty space`() {
        val out = type(at("x = ", 4), '"')
        assertEquals("x = \"|\"", caretMarked(out))
    }

    @Test
    fun `apostrophe inside a word is not paired`() {
        // "it's" must not become "it''s".
        val out = type(at("# it", 4), '\'')
        assertEquals("# it'|", caretMarked(out))
    }

    @Test
    fun `typing over a selection wraps it instead of replacing it`() {
        val selected = TextFieldValue("call value here", TextRange(5, 10))
        // What the IME reports: the selection replaced by the single typed character.
        val typed = TextFieldValue("call ( here", TextRange(6))
        val out = SmartEdit.onValueChange(selected, typed, Language.PYTHON, autoPair = true)
        assertEquals("call (value) here", out.text)
        assertEquals(TextRange(6, 11), out.selection)
    }

    @Test
    fun `backspace between an empty pair removes both halves`() {
        val before = at("print()", 6)
        val afterBackspace = TextFieldValue("print)", TextRange(5))
        val out = SmartEdit.onValueChange(before, afterBackspace, Language.PYTHON, autoPair = true)
        assertEquals("print|", caretMarked(out))
    }

    @Test
    fun `newline carries the current indent`() {
        val out = type(at("    x = 1", 9), '\n')
        assertEquals("    x = 1\n    |", caretMarked(out))
    }

    @Test
    fun `newline after a python colon opens a block`() {
        val out = type(at("def f():", 8), '\n')
        assertEquals("def f():\n    |", caretMarked(out))
    }

    @Test
    fun `newline between braces puts the closer on its own line`() {
        val out = type(at("fun f() {}", 9), '\n', Language.KOTLIN)
        assertEquals("fun f() {\n    |\n}", caretMarked(out))
    }

    @Test
    fun `auto-pair off leaves typing untouched`() {
        val before = at("print", 5)
        val typed = TextFieldValue("print(", TextRange(6))
        val out = SmartEdit.onValueChange(before, typed, Language.PYTHON, autoPair = false)
        assertEquals("print(|", caretMarked(out))
    }

    @Test
    fun `comment toggle adds and removes the language token`() {
        val commented = SmartEdit.toggleComment(at("x = 1", 0), Language.PYTHON)
        assertEquals("# x = 1", commented.text)
        val back = SmartEdit.toggleComment(commented.copy(selection = TextRange(0)), Language.PYTHON)
        assertEquals("x = 1", back.text)
    }

    @Test
    fun `indent and dedent move a whole selected block`() {
        val block = TextFieldValue("a\nb", TextRange(0, 3))
        val inned = SmartEdit.indent(block)
        assertEquals("    a\n    b", inned.text)
        val outed = SmartEdit.dedent(inned.copy(selection = TextRange(0, inned.text.length)))
        assertEquals("a\nb", outed.text)
    }

    @Test
    fun `tab at the caret fills to the next stop rather than a fixed four`() {
        val out = SmartEdit.indent(at("ab", 2))
        assertEquals("ab  ", out.text)
    }

    @Test
    fun `line and column are one-based`() {
        val text = "one\ntwo\nthree"
        assertEquals(2, SmartEdit.lineNumberAt(text, 5))
        assertEquals(2, SmartEdit.columnAt(text, 5))
    }
}
