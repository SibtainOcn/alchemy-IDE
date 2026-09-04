package com.sibtainocn.alchemy.ui.editor.sora

import android.os.Bundle
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
import io.github.rosemoe.sora.text.ContentReference
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

        /**
         * The buffer being analysed, kept because the text handed to [analyze] can be
         * short of it.
         *
         * The manager collects the document line by line and abandons the collection the
         * moment a newer request arrives, then hands over however much it had. The
         * renderer, meanwhile, asks for whichever line it is about to draw - so styles
         * built to the length of a truncated collection are an index out of bounds in the
         * middle of a frame. Which is what a 3 MB file did: 98,849 lines of styles for a
         * document of 101,521.
         */
        private var buffer: ContentReference? = null

        override fun reset(content: ContentReference, extraArguments: Bundle) {
            buffer = content
            super.reset(content, extraArguments)
        }

        override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
            val source = text.toString()
            val sink = SoraSpanSink(source.length)
            if (!delegate.isCancelled) Highlighter.scan(source, language, sink)
            // Padded to what the buffer actually holds. Spare lines carry the last span
            // and are never drawn; missing ones take the app down.
            return sink.toStyles(source, atLeast = buffer?.lineCount ?: 0)
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

    fun toStyles(text: CharSequence, atLeast: Int = 0): Styles {
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
        // Every line has to exist in the structure, and most of them never asked for a
        // span of their own. `addIfNeeded` adds nothing when the style has not changed -
        // not even the line - so a document with one uniform style, which is exactly what
        // a file past the highlighting cap is, would build a single line and then be asked
        // to draw its ten thousandth. `determine` fills the gap by carrying the last span
        // down, and `addNormalIfNull` covers an empty document.
        spans.determine(maxOf(line, atLeast - 1))
        spans.addNormalIfNull()
        // Wrapped, because padding narrows the window where the colours are behind the
        // buffer but cannot close it: they are produced on another thread from a copy of
        // the document, and the renderer draws whatever line it likes. See [SafeSpans].
        return Styles(SafeSpans(spans.build()))
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
