package com.sibtainocn.alchemy.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.AlchemyAccents
import com.sibtainocn.alchemy.ui.theme.LocalAccents
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * The Markdown preview.
 *
 * Parsing lives in [MarkdownParser]; this file only draws. Blocks are rendered from a
 * LazyColumn so a long README costs what is on screen rather than what is in the file.
 *
 * Tables scroll horizontally with fixed column widths rather than dividing the screen by
 * the column count. Three columns of prose squeezed into a phone's width produce a
 * paragraph per cell and a row twenty lines tall; a real column you can push sideways is
 * the readable trade.
 */
private val TABLE_COLUMN_WIDTH = 168.dp

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier, zoom: Float = 1f) {
    val a = LocalAccents.current
    val primary = MaterialTheme.colorScheme.primary
    val blocks = remember(text) {
        // A parse failure should show the document as one plain block, not an error
        // screen: the point of the preview is to read the file.
        runCatching { MarkdownParser.parse(text) }
            .getOrElse { listOf(MdBlock.Paragraph(text)) }
    }

    // Zoom is a density change rather than a font size, so headings, code, table columns
    // and the space between them all grow together - a page you move closer to, not one
    // paragraph set larger inside a layout built for another size.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * zoom, density.fontScale),
    ) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp,
            ),
        ) {
            items(blocks.size, key = { it }) { index ->
                when (val block = blocks[index]) {
                    is MdBlock.Heading -> HeadingBlock(block)
                    is MdBlock.Paragraph -> Text(
                        remember(block) { inline(block.text, a, primary) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMid,
                        modifier = Modifier.padding(vertical = 5.dp),
                    )

                    is MdBlock.Code -> CodeBlock(block)
                    is MdBlock.Table -> TableBlock(block)
                    is MdBlock.Item -> ItemBlock(block)
                    is MdBlock.Quote -> QuoteBlock(block)
                    MdBlock.Rule -> {
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MdBlock.Heading) {
    val a = LocalAccents.current
    Spacer(Modifier.height(if (block.level <= 2) 18.dp else 12.dp))
    Text(
        inline(block.text, a, MaterialTheme.colorScheme.primary),
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
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val a = LocalAccents.current
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .background(InkRaised)
            .border(0.7.dp, Hairline, RoundedCornerShape(Radii.sm)),
    ) {
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
                Highlighter.highlight(block.body, Language.of("x." + block.lang), a),
                fontFamily = CodeFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = a.codeText,
                softWrap = false,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TableBlock(block: MdBlock.Table) {
    val a = LocalAccents.current
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .border(0.7.dp, Hairline, RoundedCornerShape(Radii.sm))
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(Modifier.background(InkHigh)) {
                block.headers.forEachIndexed { i, header ->
                    TableCell(
                        text = AnnotatedString(header),
                        align = block.alignments.getOrElse(i) { MdAlign.START },
                        color = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.SemiBold,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))

            block.rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.background(if (rowIndex % 2 == 1) InkRaised else Color.Transparent)) {
                    row.forEachIndexed { i, cell ->
                        TableCell(
                            text = inline(cell, a, MaterialTheme.colorScheme.primary),
                            align = block.alignments.getOrElse(i) { MdAlign.START },
                            color = TextMid,
                        )
                    }
                }
                if (rowIndex != block.rows.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline.copy(alpha = 0.6f)))
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TableCell(
    text: AnnotatedString,
    align: MdAlign,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = weight,
        textAlign = when (align) {
            MdAlign.START -> TextAlign.Start
            MdAlign.CENTER -> TextAlign.Center
            MdAlign.END -> TextAlign.End
        },
        modifier = Modifier
            .width(TABLE_COLUMN_WIDTH)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ItemBlock(block: MdBlock.Item) {
    val a = LocalAccents.current
    Row(
        Modifier.padding(start = (block.depth * 18).dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (block.checked) {
            // Task items get a box, because a list of unticked boxes is the information.
            null -> Text(
                block.marker,
                style = MaterialTheme.typography.bodyLarge,
                color = if (block.ordered) a.number else a.keyword,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(if (block.ordered) 28.dp else 20.dp),
            )

            else -> Box(Modifier.width(26.dp).padding(top = 3.dp)) {
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (block.checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            1.2.dp,
                            if (block.checked) MaterialTheme.colorScheme.primary else Hairline,
                            RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (block.checked) {
                        Text(
                            "✓",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Text(
            inline(block.text, a, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyLarge,
            color = if (block.checked == true) TextMid.copy(alpha = 0.6f) else TextMid,
            textDecoration = if (block.checked == true) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun QuoteBlock(block: MdBlock.Quote) {
    val a = LocalAccents.current
    Row(
        Modifier.padding(vertical = 7.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(a.keyword)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            inline(block.text, a, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyLarge,
            color = a.comment,
            fontStyle = FontStyle.Italic,
        )
    }
}

/**
 * Inline spans: `code`, **bold**, *italic*, ~~strike~~, [text](url), and backslash
 * escapes. Anything unterminated is emitted as the literal characters rather than
 * swallowing the rest of the line.
 */
internal fun inline(src: String, a: AlchemyAccents, link: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c == '\\' && i + 1 < src.length -> {
                append(src[i + 1]); i += 2; continue
            }

            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    // Inline code is monospace on a tinted chip, the way every Markdown
                    // reader renders it. Colouring it cyan made half a table look like
                    // links, and left nothing distinct for actual links.
                    withStyle(
                        SpanStyle(
                            fontFamily = CodeFont,
                            color = a.codeText,
                            background = InkHigh,
                            fontSize = 13.5.sp,
                        )
                    ) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1; continue
                }
            }

            src.startsWith("***", i) -> {
                val end = src.indexOf("***", i + 3)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Italic,
                            color = TextHigh,
                        )
                    ) { append(src.substring(i + 3, end)) }
                    i = end + 3; continue
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

            (c == '*' || c == '_') && i + 1 < src.length && src[i + 1] != ' ' -> {
                val end = src.indexOf(c, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1; continue
                }
            }

            c == '[' -> {
                val close = src.indexOf(']', i)
                if (close > i && src.getOrNull(close + 1) == '(') {
                    val paren = src.indexOf(')', close)
                    if (paren > close) {
                        withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) {
                            append(src.substring(i + 1, close))
                        }
                        i = paren + 1; continue
                    }
                }
            }
        }
        append(c)
        i++
    }
}
