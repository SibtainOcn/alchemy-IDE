package com.sibtainocn.alchemy.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sibtainocn.alchemy.data.Language

/**
 * The typing intelligence: bracket and quote pairing, indent inference, and the block
 * operations behind the key bar.
 *
 * It works by diffing the value the IME hands back against the previous one. That is the
 * only reliable signal available from a Compose text field - there is no key event to
 * hook - and it degrades gracefully: anything that is not a recognisable single-character
 * insertion or a single-character delete is passed straight through untouched.
 */
object SmartEdit {

    const val INDENT = "    "

    private val PAIRS = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val CLOSERS = PAIRS.values.toSet()
    private val QUOTES = setOf('"', '\'', '`')

    fun onValueChange(
        old: TextFieldValue,
        new: TextFieldValue,
        lang: Language,
        autoPair: Boolean,
    ): TextFieldValue {
        // Selection-only moves and multi-character changes (paste, IME composition,
        // undo) are none of our business.
        if (new.text == old.text) return new

        val inserted = insertedChar(old, new)
        if (inserted != null) {
            val (ch, at) = inserted
            if (ch == '\n') return afterNewline(new, at, lang)
            if (autoPair) {
                afterPairChar(old, new, ch, at)?.let { return it }
            }
            return new
        }

        if (autoPair && isSingleBackspace(old, new)) {
            deletedPair(old, new)?.let { return it }
        }
        return new
    }

    /** Returns the typed character and its index, or null if this was not a plain insert. */
    private fun insertedChar(old: TextFieldValue, new: TextFieldValue): Pair<Char, Int>? {
        val removed = old.selection.length
        if (new.text.length != old.text.length - removed + 1) return null
        val at = new.selection.start - 1
        if (at < 0 || at >= new.text.length) return null
        // The caret must sit right after the new character for this to be a keystroke.
        if (!new.selection.collapsed) return null
        return new.text[at] to at
    }

    private fun isSingleBackspace(old: TextFieldValue, new: TextFieldValue): Boolean =
        old.selection.collapsed &&
            new.text.length == old.text.length - 1 &&
            new.selection.collapsed &&
            new.selection.start == old.selection.start - 1

    /** Opening brackets and quotes: insert the partner, surround a selection, or step over. */
    private fun afterPairChar(
        old: TextFieldValue,
        new: TextFieldValue,
        ch: Char,
        at: Int,
    ): TextFieldValue? {
        val text = new.text
        val next = text.getOrNull(at + 1)

        // Typing over a selection wraps it instead of replacing it.
        val hadSelection = !old.selection.collapsed
        if (hadSelection && (ch in PAIRS || ch in QUOTES)) {
            val close = PAIRS[ch] ?: ch
            val chunk = old.text.substring(old.selection.min, old.selection.max)
            val wrapped = buildString {
                append(old.text, 0, old.selection.min)
                append(ch).append(chunk).append(close)
                append(old.text, old.selection.max, old.text.length)
            }
            return TextFieldValue(
                text = wrapped,
                selection = TextRange(old.selection.min + 1, old.selection.min + 1 + chunk.length),
            )
        }

        // Stepping over a closer we previously inserted, rather than doubling it.
        if ((ch in CLOSERS || ch in QUOTES) && next == ch) {
            val doubled = ch in CLOSERS || countUnescaped(text, ch, at) % 2 == 0
            if (doubled) {
                return TextFieldValue(
                    text = text.removeRange(at, at + 1),
                    selection = TextRange(at + 1),
                )
            }
        }

        val close = PAIRS[ch]
        if (close != null) {
            // Do not pair when it would collide with the word already sitting there.
            if (next != null && (next.isLetterOrDigit() || next == '_')) return null
            return TextFieldValue(
                text = text.substring(0, at + 1) + close + text.substring(at + 1),
                selection = TextRange(at + 1),
            )
        }

        if (ch in QUOTES) {
            val prev = text.getOrNull(at - 1)
            // Not inside a word (don't pair the apostrophe in "it's"), and not when the
            // quote already has a partner ahead of it.
            if (prev != null && (prev.isLetterOrDigit() || prev == '_')) return null
            if (next != null && (next.isLetterOrDigit() || next == '_')) return null
            if (countUnescaped(text, ch, at) % 2 != 0) return null
            return TextFieldValue(
                text = text.substring(0, at + 1) + ch + text.substring(at + 1),
                selection = TextRange(at + 1),
            )
        }
        return null
    }

    /** Backspacing between an empty pair removes both halves. */
    private fun deletedPair(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        val at = new.selection.start
        val removedChar = old.text[old.selection.start - 1]
        val next = new.text.getOrNull(at) ?: return null
        val expected = PAIRS[removedChar] ?: removedChar.takeIf { it in QUOTES }
        if (expected != next) return null
        return TextFieldValue(
            text = new.text.removeRange(at, at + 1),
            selection = TextRange(at),
        )
    }

    /** Counts unescaped occurrences of [ch] before [end] on the current line. */
    private fun countUnescaped(text: String, ch: Char, end: Int): Int {
        var count = 0
        var i = lineStart(text, end)
        while (i < end) {
            if (text[i] == '\\') { i += 2; continue }
            if (text[i] == ch) count++
            i++
        }
        return count
    }

    // ---- Newline: carry the indent, open a block, and expand an empty pair ----

    private fun afterNewline(v: TextFieldValue, at: Int, lang: Language): TextFieldValue {
        val text = v.text
        val prevLineStart = lineStart(text, at)
        val prevLine = text.substring(prevLineStart, at)
        val trimmed = prevLine.trimEnd()

        var indent = leadingWhitespace(prevLine)
        val opensBlock = when (lang) {
            // A trailing colon is Python's block opener; the trailing-bracket case below
            // covers the rest.
            Language.PYTHON -> trimmed.endsWith(":")
            else -> false
        } || trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")

        if (opensBlock) indent += INDENT

        // Closing brace already sitting after the caret: give it its own line.
        val next = text.getOrNull(at + 1)
        val outdent = leadingWhitespace(prevLine)
        if (opensBlock && next != null && next in CLOSERS) {
            val body = "\n" + indent
            val tail = "\n" + outdent
            return TextFieldValue(
                text = text.substring(0, at) + body + tail + text.substring(at + 1),
                selection = TextRange(at + body.length),
            )
        }

        if (indent.isEmpty()) return v
        return TextFieldValue(
            text = text.substring(0, at + 1) + indent + text.substring(at + 1),
            selection = TextRange(at + 1 + indent.length),
        )
    }

    // ---- Block operations used by the key bar ----

    fun indent(v: TextFieldValue): TextFieldValue = shiftLines(v, out = false)

    fun dedent(v: TextFieldValue): TextFieldValue = shiftLines(v, out = true)

    private fun shiftLines(v: TextFieldValue, out: Boolean): TextFieldValue {
        val text = v.text
        val from = lineStart(text, v.selection.min)
        val to = lineEnd(text, v.selection.max)

        // A collapsed caret mid-line just inserts a tab stop.
        if (v.selection.collapsed && !out) {
            val col = v.selection.start - from
            val pad = INDENT.length - (col % INDENT.length)
            val spaces = " ".repeat(pad)
            return TextFieldValue(
                text = text.substring(0, v.selection.start) + spaces + text.substring(v.selection.start),
                selection = TextRange(v.selection.start + spaces.length),
            )
        }

        val block = text.substring(from, to)
        var firstDelta = 0
        var total = 0
        val rebuilt = block.split("\n").mapIndexed { i, line ->
            if (out) {
                val strip = line.takeWhile { it == ' ' }.length.coerceAtMost(INDENT.length)
                if (i == 0) firstDelta = -strip
                total -= strip
                line.substring(strip)
            } else {
                if (i == 0) firstDelta = INDENT.length
                total += INDENT.length
                INDENT + line
            }
        }.joinToString("\n")

        return TextFieldValue(
            text = text.substring(0, from) + rebuilt + text.substring(to),
            selection = TextRange(
                (v.selection.min + firstDelta).coerceAtLeast(from),
                (v.selection.max + total).coerceAtLeast(from),
            ),
        )
    }

    /** Comments or uncomments every line the selection touches. */
    fun toggleComment(v: TextFieldValue, lang: Language): TextFieldValue {
        val token = when (lang) {
            Language.PYTHON, Language.SHELL, Language.CONFIG -> "# "
            Language.XML -> return v
            else -> "// "
        }
        val text = v.text
        val from = lineStart(text, v.selection.min)
        val to = lineEnd(text, v.selection.max)
        val lines = text.substring(from, to).split("\n")
        val bare = token.trimEnd()
        val allCommented = lines.all { it.isBlank() || it.trimStart().startsWith(bare) }

        var delta = 0
        val rebuilt = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                allCommented -> {
                    val ws = leadingWhitespace(line)
                    val rest = line.substring(ws.length)
                    val cut = if (rest.startsWith(token)) token.length else bare.length
                    delta -= cut
                    ws + rest.substring(cut)
                }
                else -> {
                    val ws = leadingWhitespace(line)
                    delta += token.length
                    ws + token + line.substring(ws.length)
                }
            }
        }
        return TextFieldValue(
            text = text.substring(0, from) + rebuilt + text.substring(to),
            selection = TextRange((to + delta).coerceIn(from, from + rebuilt.length)),
        )
    }

    fun duplicateLine(v: TextFieldValue): TextFieldValue {
        val text = v.text
        val from = lineStart(text, v.selection.min)
        val to = lineEnd(text, v.selection.max)
        val block = text.substring(from, to)
        return TextFieldValue(
            text = text.substring(0, to) + "\n" + block + text.substring(to),
            selection = TextRange(to + 1 + block.length),
        )
    }

    fun deleteLine(v: TextFieldValue): TextFieldValue {
        val text = v.text
        val from = lineStart(text, v.selection.min)
        var to = lineEnd(text, v.selection.max)
        if (to < text.length) to++ else if (from > 0) return TextFieldValue(
            text = text.substring(0, from - 1),
            selection = TextRange(from - 1),
        )
        return TextFieldValue(
            text = text.removeRange(from, to),
            selection = TextRange(from),
        )
    }

    // ---- Caret movement, for the arrow keys on the key bar ----

    /**
     * Moves the caret [delta] characters. With [extend] the anchor stays put and the
     * selection grows, which is how a keyboard's Shift+arrow behaves.
     */
    fun moveCaret(v: TextFieldValue, delta: Int, extend: Boolean): TextFieldValue {
        val from = if (extend) v.selection.end else {
            // Collapsing a selection with a plain arrow lands on the near edge, not on
            // wherever the moving end happened to be.
            if (!v.selection.collapsed) {
                return TextFieldValue(
                    v.text,
                    TextRange(if (delta < 0) v.selection.min else v.selection.max),
                )
            }
            v.selection.start
        }
        val target = (from + delta).coerceIn(0, v.text.length)
        return TextFieldValue(
            v.text,
            if (extend) TextRange(v.selection.start, target) else TextRange(target),
        )
    }

    /**
     * Moves the caret a whole line, keeping the column where possible.
     *
     * This walks logical lines rather than visual ones: without the text layout there is
     * no way to know where a wrapped row breaks, and stepping by paragraph is the
     * predictable choice when they differ.
     */
    fun moveCaretLine(v: TextFieldValue, deltaLines: Int, extend: Boolean): TextFieldValue {
        val text = v.text
        val head = if (extend) v.selection.end else v.selection.start
        val start = lineStart(text, head)
        val column = head - start

        val target = if (deltaLines < 0) {
            if (start == 0) return moveCaret(v, -head, extend)
            val prevStart = lineStart(text, start - 1)
            (prevStart + column).coerceAtMost(start - 1)
        } else {
            val end = lineEnd(text, head)
            if (end >= text.length) return moveCaret(v, text.length - head, extend)
            val nextStart = end + 1
            (nextStart + column).coerceAtMost(lineEnd(text, nextStart))
        }
        return TextFieldValue(
            text,
            if (extend) TextRange(v.selection.start, target) else TextRange(target),
        )
    }

    fun selectAll(v: TextFieldValue): TextFieldValue =
        TextFieldValue(v.text, TextRange(0, v.text.length))

    /** Removes the selection, if there is one. Backs Cut. */
    fun deleteSelection(v: TextFieldValue): TextFieldValue {
        if (v.selection.collapsed) return v
        return TextFieldValue(
            v.text.removeRange(v.selection.min, v.selection.max),
            TextRange(v.selection.min),
        )
    }

    /** The selected text, or the whole line when nothing is selected. */
    fun selectedTextOrLine(v: TextFieldValue): String {
        if (!v.selection.collapsed) return v.text.substring(v.selection.min, v.selection.max)
        return v.text.substring(lineStart(v.text, v.selection.start), lineEnd(v.text, v.selection.start))
    }

    /** Jumps to the first non-blank character of the line, then to column 0. */
    fun toLineStart(v: TextFieldValue, extend: Boolean): TextFieldValue {
        val start = lineStart(v.text, v.selection.start)
        val firstWord = start + v.text.substring(start, lineEnd(v.text, start))
            .takeWhile { it == ' ' || it == '\t' }.length
        val target = if (v.selection.start == firstWord) start else firstWord
        return TextFieldValue(
            v.text,
            if (extend) TextRange(v.selection.start, target) else TextRange(target),
        )
    }

    fun toLineEnd(v: TextFieldValue, extend: Boolean): TextFieldValue {
        val target = lineEnd(v.text, v.selection.start)
        return TextFieldValue(
            v.text,
            if (extend) TextRange(v.selection.start, target) else TextRange(target),
        )
    }

    /** Inserts a literal snippet at the caret, replacing any selection. */
    fun insert(v: TextFieldValue, snippet: String, caretOffset: Int = snippet.length): TextFieldValue {
        val start = v.selection.min
        return TextFieldValue(
            text = v.text.replaceRange(start, v.selection.max, snippet),
            selection = TextRange(start + caretOffset),
        )
    }

    fun lineStart(text: String, index: Int): Int {
        val i = index.coerceIn(0, text.length)
        return text.lastIndexOf('\n', (i - 1).coerceAtLeast(0)).let { if (it < 0 || i == 0) 0 else it + 1 }
    }

    fun lineEnd(text: String, index: Int): Int {
        val i = index.coerceIn(0, text.length)
        val n = text.indexOf('\n', i)
        return if (n < 0) text.length else n
    }

    fun lineNumberAt(text: String, index: Int): Int {
        var count = 1
        var i = 0
        val stop = index.coerceIn(0, text.length)
        while (i < stop) {
            if (text[i] == '\n') count++
            i++
        }
        return count
    }

    fun columnAt(text: String, index: Int): Int = index - lineStart(text, index) + 1

    private fun leadingWhitespace(line: String): String = line.takeWhile { it == ' ' || it == '\t' }
}
