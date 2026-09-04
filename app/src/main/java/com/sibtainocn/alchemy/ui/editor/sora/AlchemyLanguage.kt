package com.sibtainocn.alchemy.ui.editor.sora

import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.syntax.TokenKind
import com.sibtainocn.alchemy.syntax.TokenSink
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * Alchemy's scanner, as something the editor can be handed.
 *
 * [SimpleAnalyzeManager] is what makes this cheap: sora already runs it on a worker
 * thread, re-runs it when the buffer changes, and cancels a run whose result nobody wants
 * any more. The scan itself is the same one the Markdown preview uses - the only
 * difference is the sink it reports to.
 */
class AlchemyLanguage(
    private val language: Language,
    /** Whether typing an opener should write its partner. A user preference. */
    private val autoPair: Boolean = true,
) : EmptyLanguage() {

    private val analyzer = object : SimpleAnalyzeManager<Unit>() {
        override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
            val source = text.toString()
            val sink = SoraSpanSink(source.length)
            if (!delegate.isCancelled) Highlighter.scan(source, language, sink)
            return sink.toStyles(source)
        }
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun useTab(): Boolean = false

    /**
     * Brackets and quotes that write their partner.
     *
     * This used to be worked out by diffing the value the IME handed back, because a
     * Compose text field offers no key event to hook. The editor has real key events, so
     * the rules can simply be declared - and stepping over a closer, wrapping a selection
     * and not pairing inside a word all come with them rather than being re-derived.
     */
    private val pairs = SymbolPairMatch().apply {
        if (autoPair) {
            putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
            putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
            putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
            putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
            putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
            putPair('`', SymbolPairMatch.SymbolPair("`", "`"))
        }
    }

    override fun getSymbolPairs(): SymbolPairMatch = pairs

    private val newline = arrayOf<NewlineHandler>(BlockIndent(language))

    override fun getNewlineHandlers(): Array<NewlineHandler> = newline
}

/**
 * What Enter does: carry the current indent down, and add one more when the line just
 * opened a block.
 *
 * A line that opens a block *and* already has its closer sitting after the caret gets
 * three lines out of one Enter - the opener, an indented blank one to type into, and the
 * closer on its own line - which is the only arrangement that does not leave you
 * reformatting what you just typed.
 */
private class BlockIndent(private val lang: Language) : NewlineHandler {

    override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean =
        true

    override fun handleNewline(
        text: Content,
        position: CharPosition,
        style: Styles?,
        tabSize: Int,
    ): NewlineHandleResult {
        val line = text.getLineString(position.line)
        val at = position.column.coerceIn(0, line.length)
        val before = line.substring(0, at)
        val after = line.substring(at)
        val indent = before.takeWhile { it == ' ' || it == '\t' }
        val trimmed = before.trimEnd()

        val opens = when (lang) {
            // A trailing colon is Python's block opener; the bracket cases cover the rest.
            Language.PYTHON -> trimmed.endsWith(":")
            else -> false
        } || trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")

        val body = indent + if (opens) EditorOps.INDENT else ""
        val closerAhead = after.firstOrNull() in setOf('}', ')', ']')

        if (opens && closerAhead) {
            val tail = "\n" + indent
            return NewlineHandleResult("\n" + body + tail, tail.length)
        }
        return NewlineHandleResult("\n" + body, 0)
    }
}

/**
 * Collects a scan into the editor's span format.
 *
 * The two models do not line up, so this is where they are reconciled. The scanner reports
 * half-open character ranges that may overlap and need not cover everything; the editor
 * wants, per line, spans in column order, each running until the next one starts. So the
 * runs are flattened into one style per character first - later runs winning, which is
 * what overlapping means here - and the result is walked once, emitting a span only where
 * the style actually changes.
 *
 * A style per character is a byte array the size of the file, which sounds worse than it
 * is: it is transient, it is on a worker thread, and the scanner already refuses anything
 * past [Highlighter.MAX_HIGHLIGHT_CHARS].
 */
class SoraSpanSink(length: Int) : TokenSink {

    /** One packed style per character; zero means nothing was reported for it. */
    private val cover = IntArray(length)

    override fun token(start: Int, end: Int, kind: TokenKind, italic: Boolean, bold: Boolean) {
        val from = start.coerceIn(0, cover.size)
        val to = end.coerceIn(from, cover.size)
        val packed = pack(kind, italic, bold)
        for (i in from until to) cover[i] = packed
    }

    fun toStyles(text: CharSequence): Styles {
        val spans = MappedSpans.Builder()
        var line = 0
        var column = 0
        // Nothing has been reported at the start of a line until something is, and every
        // line has to open with a span or the editor has no style to draw its first
        // character in.
        var current = -1
        for (i in text.indices) {
            if (text[i] == '\n') {
                line++
                column = 0
                current = -1
                continue
            }
            val here = cover[i]
            if (here != current) {
                spans.addIfNeeded(line, column, styleOf(here))
                current = here
            }
            column++
        }
        // A file whose last line is empty still needs that line to exist.
        spans.addIfNeeded(line, column, styleOf(0))
        return Styles(spans.build())
    }

    private companion object {
        const val ITALIC = 1 shl 8
        const val BOLD = 1 shl 9

        /** Ordinals are shifted by one so that zero can mean "nothing reported". */
        fun pack(kind: TokenKind, italic: Boolean, bold: Boolean): Int =
            (kind.ordinal + 1) or (if (italic) ITALIC else 0) or (if (bold) BOLD else 0)

        fun styleOf(packed: Int): Long {
            if (packed == 0) return TextStyle.makeStyle(CodeSlot.TEXT)
            val kind = TokenKind.entries[(packed and 0xFF) - 1]
            return TextStyle.makeStyle(
                slotOf(kind),
                0,
                packed and BOLD != 0,
                packed and ITALIC != 0,
                false,
            )
        }

        fun slotOf(kind: TokenKind): Int = when (kind) {
            TokenKind.TEXT -> CodeSlot.TEXT
            TokenKind.KEYWORD -> CodeSlot.KEYWORD
            TokenKind.OPERATOR -> CodeSlot.OPERATOR
            TokenKind.FUNCTION -> CodeSlot.FUNCTION
            TokenKind.BUILTIN -> CodeSlot.BUILTIN
            TokenKind.STRING -> CodeSlot.STRING
            TokenKind.NUMBER -> CodeSlot.NUMBER
            TokenKind.COMMENT -> CodeSlot.COMMENT
            TokenKind.DECORATOR -> CodeSlot.DECORATOR
            TokenKind.SELF_REF -> CodeSlot.SELF_REF
            TokenKind.PUNCTUATION -> CodeSlot.PUNCTUATION
            TokenKind.BRACE -> CodeSlot.BRACE
        }
    }
}
