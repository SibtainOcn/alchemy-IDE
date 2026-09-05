package com.sibtainocn.alchemy.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.theme.AlchemyAccents

/**
 * What a run of source text is, as far as the scanner is concerned.
 *
 * The scanner names kinds, never colours. Two things follow from that, and both are the
 * reason for the split: the same scan can be drawn by two different renderers - Compose
 * spans for the Markdown preview, the editor's own span format for the code surface - and
 * a change of theme repaints without rescanning, because nothing the scan produced
 * mentioned a colour in the first place.
 */
enum class TokenKind {
    TEXT,
    KEYWORD,
    OPERATOR,
    FUNCTION,
    BUILTIN,
    STRING,
    NUMBER,
    COMMENT,
    DECORATOR,

    /** `self`, `cls`, and parameter names - the things that are not quite identifiers. */
    SELF_REF,
    PUNCTUATION,

    /** Brackets specifically, which some languages colour apart from other punctuation. */
    BRACE,

    /**
     * The marks that open and close a hole in an interpolated string: Python's `{` and
     * `}`, the `$` and `${` of Kotlin, the shell and a JavaScript template.
     *
     * Named apart from [BRACE] because it answers a different question. A brace in code is
     * punctuation; a brace inside a string means the quotes are no longer telling the
     * truth, and what sits between them is an expression rather than text. What is inside
     * the hole is reported as [TEXT], because that is what it is: code.
     */
    INTERPOLATION,

    /**
     * Markdown's fenced blocks and backtick spans.
     *
     * Raw text rather than a string literal, and coloured apart from one so that the
     * string colour means a quoted string and nothing else.
     */
    CODE_SPAN,
}

/**
 * Where a scan is delivered.
 *
 * Called in source order with half-open ranges. A sink may be given overlapping runs -
 * the scanners paint a whole string and then its escapes, for instance - and later runs
 * win, which is what both renderers already do.
 */
interface TokenSink {
    fun token(start: Int, end: Int, kind: TokenKind, italic: Boolean = false, bold: Boolean = false)
}

/**
 * The colour a [TokenKind] is drawn in.
 *
 * C is the one language that does not take the shared palette: its numbers, strings,
 * types and brackets are the colours a C programmer expects rather than the Python ones.
 * That used to be done by handing the scanner a doctored copy of the accents, which meant
 * the scanner had to be given colours at all. It is a property of how a language is drawn,
 * so it lives here with the rest of the drawing.
 */
fun AlchemyAccents.colorOf(kind: TokenKind, lang: Language): Color {
    if (lang == Language.C_LIKE) {
        when (kind) {
            TokenKind.NUMBER -> return Color(0xFFF9F5F5)
            TokenKind.STRING -> return Color(0xFFFF8C00)
            TokenKind.BUILTIN -> return Color(0xFFFF4036)
            TokenKind.BRACE -> return Color(0xFF00FFFF)
            else -> Unit
        }
    }
    return when (kind) {
        TokenKind.TEXT -> codeText
        TokenKind.KEYWORD -> keyword
        TokenKind.OPERATOR -> operator
        TokenKind.FUNCTION -> function
        TokenKind.BUILTIN -> builtin
        TokenKind.STRING -> string
        TokenKind.NUMBER -> number
        TokenKind.COMMENT -> comment
        TokenKind.DECORATOR -> decorator
        TokenKind.SELF_REF -> selfRef
        TokenKind.PUNCTUATION -> punctuation
        TokenKind.BRACE -> brace
        TokenKind.INTERPOLATION -> interpolation
        TokenKind.CODE_SPAN -> codeSpan
    }
}

/**
 * A sink that builds a Compose [AnnotatedString], for the Markdown preview's code blocks.
 *
 * The editor's own sink lives beside the code surface and speaks in colour slots instead,
 * so that switching themes there repaints without rescanning.
 */
class AnnotatedStringSink(
    text: String,
    private val accents: AlchemyAccents,
    private val lang: Language,
) : TokenSink {
    private val builder = AnnotatedString.Builder(text)

    override fun token(start: Int, end: Int, kind: TokenKind, italic: Boolean, bold: Boolean) {
        if (end <= start) return
        builder.addStyle(
            SpanStyle(
                color = accents.colorOf(kind, lang),
                fontStyle = if (italic) FontStyle.Italic else null,
                fontWeight = if (bold) FontWeight.Medium else null,
            ),
            start, end,
        )
    }

    fun build(): AnnotatedString = builder.toAnnotatedString()
}
