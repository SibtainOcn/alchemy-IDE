package dev.hazel.code.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hazel.code.data.Language
import dev.hazel.code.syntax.Highlighter
import dev.hazel.code.ui.theme.CodeFont
import dev.hazel.code.ui.theme.Hairline
import dev.hazel.code.ui.theme.InkRaised
import dev.hazel.code.ui.theme.LocalAccents
import dev.hazel.code.ui.theme.Radii
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextMid

/**
 * A deliberately small Markdown renderer.
 *
 * Pulling in a full CommonMark implementation would roughly double the app's method
 * count for a feature that exists to read a README on a phone. This covers headings,
 * fenced and inline code, lists, quotes, rules, tables' worst case (shown as-is) and the
 * inline run of bold/italic/strike/code/link - and anything it does not understand
 * survives as plain text rather than disappearing.
 *
 * Fenced blocks are syntax-highlighted with the same engine as the editor, so a Python
 * snippet in a README reads exactly like the file it came from.
 */

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Code(val lang: String, val body: String) : Block
    data class Bullet(val depth: Int, val marker: String, val text: String) : Block
    data class Quote(val text: String) : Block
    data object Rule : Block
}

private fun parse(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = src.lines()
    var i = 0
    val para = StringBuilder()

    fun flush() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.setLength(0)
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                flush()
                val lang = trimmed.removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    body.appendLine(lines[i]); i++
                }
                out += Block.Code(lang, body.toString().trimEnd())
            }

            trimmed.startsWith("#") && trimmed.takeWhile { it == '#' }.length <= 6 &&
                trimmed.dropWhile { it == '#' }.startsWith(" ") -> {
                flush()
                val level = trimmed.takeWhile { it == '#' }.length
                out += Block.Heading(level, trimmed.drop(level).trim())
            }

            trimmed.isNotEmpty() && trimmed.all { it == '-' || it == '*' || it == '_' } && trimmed.length >= 3 -> {
                flush(); out += Block.Rule
            }

            trimmed.startsWith(">") -> {
                flush(); out += Block.Quote(trimmed.removePrefix(">").trim())
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flush()
                val depth = (line.length - line.trimStart().length) / 2
                out += Block.Bullet(depth, "•", trimmed.drop(2))
            }

            trimmed.matches(Regex("^\\d+[.)] .*")) -> {
                flush()
                val depth = (line.length - line.trimStart().length) / 2
                val marker = trimmed.takeWhile { it.isDigit() } + "."
                out += Block.Bullet(depth, marker, trimmed.dropWhile { it.isDigit() }.drop(2))
            }

            trimmed.isEmpty() -> flush()

            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(trimmed)
            }
        }
        i++
    }
    flush()
    return out
}

/** Inline spans: `code`, **bold**, *italic*, ~~strike~~, [text](url). */
private fun inline(src: String, code: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < src.length) {
            when {
                src[i] == '`' -> {
                    val end = src.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = CodeFont, color = code, fontSize = 13.5.sp)) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1; continue
                    }
                }
                src.startsWith("**", i) -> {
                    val end = src.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextHigh)) {
                            append(src.substring(i + 2, end))
                        }
                        i = end + 2; continue
                    }
                }
                src.startsWith("~~", i) -> {
                    val end = src.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(src.substring(i + 2, end))
                        }
                        i = end + 2; continue
                    }
                }
                (src[i] == '*' || src[i] == '_') && i + 1 < src.length && src[i + 1] != ' ' -> {
                    val end = src.indexOf(src[i], i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1; continue
                    }
                }
                src[i] == '[' -> {
                    val close = src.indexOf(']', i)
                    if (close > i && src.getOrNull(close + 1) == '(') {
                        val paren = src.indexOf(')', close)
                        if (paren > close) {
                            withStyle(SpanStyle(color = code, textDecoration = TextDecoration.Underline)) {
                                append(src.substring(i + 1, close))
                            }
                            i = paren + 1; continue
                        }
                    }
                }
            }
            append(src[i])
            i++
        }
    }

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val a = LocalAccents.current
    val blocks = remember(text) { parse(text) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 18.dp else 12.dp))
                    Text(
                        inline(block.text, a.builtin),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.bodyLarge
                        },
                        color = TextHigh,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (block.level <= 2) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is Block.Paragraph -> {
                    Text(
                        inline(block.text, a.builtin),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMid,
                        modifier = Modifier.padding(vertical = 5.dp),
                    )
                }

                is Block.Code -> {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(InkRaised, RoundedCornerShape(Radii.sm))
                            .padding(1.dp),
                    ) {
                        Column {
                            if (block.lang.isNotBlank()) {
                                Text(
                                    block.lang,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = a.comment,
                                    modifier = Modifier.padding(start = 14.dp, top = 9.dp),
                                )
                            }
                            Box(Modifier.horizontalScroll(rememberScrollState())) {
                                Text(
                                    Highlighter.highlight(block.body, Language.of("x.${block.lang}"), a),
                                    fontFamily = CodeFont,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = a.codeText,
                                    softWrap = false,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is Block.Bullet -> {
                    Row(
                        Modifier.padding(start = (block.depth * 16).dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            block.marker,
                            style = MaterialTheme.typography.bodyLarge,
                            color = a.keyword,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            inline(block.text, a.builtin),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextMid,
                        )
                    }
                }

                is Block.Quote -> {
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(a.keyword, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            inline(block.text, a.builtin),
                            style = MaterialTheme.typography.bodyLarge,
                            color = a.comment,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                Block.Rule -> {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}
