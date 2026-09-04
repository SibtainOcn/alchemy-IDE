package com.sibtainocn.alchemy.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.ui.theme.AlchemyAccents
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.LocalAccents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Text long enough that colouring it is worth moving off the main thread.
 *
 * Below this a scan costs well under a frame, and doing it in the background would show
 * a flash of uncoloured text every time a file is opened. Above it the scan is felt as a
 * stutter on every keystroke, which is the thing worth trading a late frame of colour for.
 */
private const val SYNC_HIGHLIGHT_CHARS = 20_000

/** How long typing must pause before a long file is re-scanned. */
private const val HIGHLIGHT_DEBOUNCE_MS = 90L

/**
 * The code surface.
 *
 * Notable choices:
 * - The gutter is a sibling canvas driven by the field's own [TextLayoutResult], not a
 *   column of Text rows. That is the only way numbers stay aligned when a long line
 *   wraps across three visual rows. It draws only the rows the viewport can show: the
 *   layout covers the whole document, and walking all of it once a frame is what made a
 *   long file take seconds to draw.
 * - Highlighting runs through a [VisualTransformation], so the stored text stays plain
 *   and the caret offsets never need remapping.
 * - With wrapping off, the content width is measured from the longest line and the field
 *   is laid out at that width inside a horizontal scroller, since BasicTextField has no
 *   soft-wrap switch of its own.
 */
@Composable
fun CodeField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: Language,
    fontSizeSp: Int,
    wordWrap: Boolean,
    showLineNumbers: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val style = remember(fontSizeSp, accents) {
        TextStyle(
            fontFamily = CodeFont,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.55f).sp,
            color = accents.codeText,
            // Code is broken at the edge of the line, never rebalanced across it and
            // never hyphenated. The high-quality breaker is the single most expensive
            // part of laying a long file out, and none of what it buys applies here.
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None,
        )
    }
    val gutterStyle = remember(style, fontSizeSp) {
        style.copy(fontSize = (fontSizeSp - 2).coerceAtLeast(8).sp, textAlign = TextAlign.End)
    }

    val highlighted = rememberHighlight(value.text, language, accents)
    val transformation = remember(highlighted) {
        VisualTransformation { input ->
            // The colouring can be a beat behind the buffer while a long file is scanned
            // in the background. What the field was given is what has to be shown either
            // way - a transformation returning different text than it was handed corrupts
            // every offset the caret depends on - so stale spans are trimmed onto the
            // current text rather than the stale text being drawn.
            val coloured =
                if (highlighted.text == input.text) highlighted
                else spansOnto(input.text, highlighted)
            TransformedText(coloured, OffsetMapping.Identity)
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Gutter numbers repeat constantly and would be re-measured every frame otherwise.
    // Bounded, because one entry per line number is a slow leak in a very long file; the
    // cap is far above the number of rows that fit on any screen, so scrolling still
    // never measures.
    val numberCache = remember(gutterStyle, accents) {
        object : LinkedHashMap<Pair<String, Boolean>, TextLayoutResult>(256, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<String, Boolean>, TextLayoutResult>,
            ): Boolean = size > 512
        }
    }

    // Line count drives the gutter width so a 4-digit file does not clip; the longest
    // line drives the scrollable width when wrapping is off. Both come out of one pass
    // over the text, because both were previously their own walk of it and the second
    // allocated a string per line - a few thousand of them on every keystroke.
    val metrics = remember(value.text) { TextMetrics.of(value.text) }
    val gutterWidth = remember(metrics, fontSizeSp) {
        val digits = metrics.lines.toString().length.coerceAtLeast(2)
        ((digits * (fontSizeSp - 2) * 0.62f) + 20f).dp
    }

    // The editor font is monospace, so the width of a line is its length times the width
    // of one character. Measuring the longest line itself meant laying out a few thousand
    // characters again every time that line changed.
    val charWidth = remember(style) { measurer.measure(AnnotatedString("0"), style).size.width }
    val contentWidth = remember(metrics, charWidth, wordWrap) {
        if (wordWrap) 0.dp
        else with(density) { (metrics.longestLine.toLong() * charWidth + 48).toInt().toDp() }
    }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val cursorLine = layout?.let { l ->
        runCatching { l.getLineForOffset(value.selection.start) }.getOrNull()
    }

    // Which line number an offset falls on, answered against the text the layout was
    // built from rather than the field's current buffer. Those two disagree for a frame
    // after every keystroke, and reading the newer one against the older layout is what
    // made the numbers flicker between N and N+1 while typing.
    val lineIndex = remember { LineIndex() }

    Row(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(vScroll),
    ) {
        if (showLineNumbers) {
            val heightDp = with(density) { (layout?.size?.height ?: 0).toDp() }
            Box(
                Modifier
                    .width(gutterWidth)
                    .height(heightDp)
                    .drawBehind {
                        val l = layout ?: return@drawBehind
                        val text = l.layoutInput.text.text
                        val starts = lineIndex.forText(text)

                        // Only the rows the viewport can actually show. The layout spans
                        // the whole document, so this loop used to run over every line in
                        // the file on every frame, measuring the ones its cache had no
                        // room for. That is what a long file froze on.
                        val top = vScroll.value.toFloat()
                        val bottom = top + vScroll.viewportSize
                        val last = l.lineCount - 1
                        val firstRow = l.getLineForVerticalPosition(top).coerceIn(0, last)
                        val lastRow = l.getLineForVerticalPosition(bottom).coerceIn(firstRow, last)

                        // The active row's band continues across the gutter, so the
                        // highlight reads as one line rather than two halves.
                        cursorLine?.takeIf { it in firstRow..lastRow }?.let { line ->
                            drawRect(
                                color = accents.caretLine,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, l.getLineTop(line)),
                                size = androidx.compose.ui.geometry.Size(
                                    width = size.width,
                                    height = l.getLineBottom(line) - l.getLineTop(line),
                                ),
                            )
                        }

                        for (i in firstRow..lastRow) {
                            val start = l.getLineStart(i)
                            // A wrapped continuation carries no number of its own, which
                            // is what makes the gutter count real lines rather than rows.
                            val isParagraphStart = start == 0 || text.getOrNull(start - 1) == '\n'
                            if (!isParagraphStart) continue
                            val active = cursorLine == i
                            val label = starts.lineAt(start).toString()
                            val m = numberCache.getOrPut(label to active) {
                                measurer.measure(
                                    AnnotatedString(label),
                                    gutterStyle.copy(
                                        color = if (active) accents.gutterActive else accents.gutter,
                                        fontWeight = if (active) FontWeight.Medium else null,
                                    ),
                                )
                            }
                            drawText(
                                textLayoutResult = m,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    x = size.width - m.size.width - 10.dp.toPx(),
                                    y = l.getLineTop(i) +
                                        (l.getLineBottom(i) - l.getLineTop(i) - m.size.height) / 2f,
                                ),
                            )
                        }
                    }
            )
        }

        Box(
            Modifier
                .weight(1f)
                // Applied conditionally, not merely disabled: a horizontal scroller
                // measures its content with unbounded width either way, which would make
                // fillMaxWidth() meaningless in wrap mode.
                .then(if (wordWrap) Modifier else Modifier.horizontalScroll(hScroll))
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = transformation,
                onTextLayout = { layout = it },
                keyboardOptions = KeyboardOptions(
                    // Autocorrect and auto-capitalisation are actively hostile to code.
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                modifier = Modifier
                    .then(
                        if (wordWrap) Modifier.fillMaxWidth()
                        else Modifier.width(contentWidth)
                    )
                    .drawBehind {
                        val l = layout ?: return@drawBehind
                        val line = cursorLine ?: return@drawBehind
                        if (line >= l.lineCount) return@drawBehind
                        // Current-line band: barely-there on true black, but enough to
                        // find the caret after scrolling.
                        drawRect(
                            color = accents.caretLine,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, l.getLineTop(line)),
                            size = androidx.compose.ui.geometry.Size(
                                width = size.width,
                                height = l.getLineBottom(line) - l.getLineTop(line),
                            ),
                        )
                    }
                    .padding(end = 24.dp),
            )
        }
    }
}

/**
 * The coloured form of [text].
 *
 * A short file is scanned in place, because the scan costs less than the frame it happens
 * on and a background pass would show a beat of plain text on every open. A long one is
 * scanned on a background thread after typing pauses, and the previous colouring stays on
 * screen until the new one lands. Colour is the part of an editor that can afford to be
 * late; the character you just typed is not.
 */
@Composable
private fun rememberHighlight(
    text: String,
    language: Language,
    accents: AlchemyAccents,
): AnnotatedString =
    if (text.length <= SYNC_HIGHLIGHT_CHARS) {
        remember(text, language, accents) { highlightOrPlain(text, language, accents) }
    } else {
        produceState(AnnotatedString(text), text, language, accents) {
            // A run of keystrokes should be scanned once, when it stops, not once each.
            delay(HIGHLIGHT_DEBOUNCE_MS)
            value = withContext(Dispatchers.Default) { highlightOrPlain(text, language, accents) }
        }.value
    }

/**
 * Highlighting is a nice-to-have; the text is not. A scanner bug on some pathological
 * input shows plain text rather than crashing the screen.
 */
private fun highlightOrPlain(
    text: String,
    language: Language,
    accents: AlchemyAccents,
): AnnotatedString =
    runCatching { Highlighter.highlight(text, language, accents) }
        .getOrElse { AnnotatedString(text) }

/** [source]'s colours over [text], for the frames where the scan is behind the buffer. */
private fun spansOnto(text: String, source: AnnotatedString): AnnotatedString {
    val limit = text.length
    val spans = source.spanStyles.mapNotNull { span ->
        if (span.start >= limit) null
        else AnnotatedString.Range(span.item, span.start, span.end.coerceAtMost(limit))
    }
    return AnnotatedString(text, spans)
}

/** What one pass over the buffer tells the gutter and the horizontal scroller. */
private class TextMetrics(val lines: Int, val longestLine: Int) {
    companion object {
        fun of(text: String): TextMetrics {
            var lines = 1
            var longest = 0
            var lineStart = 0
            for (i in text.indices) {
                if (text[i] != '\n') continue
                lines++
                if (i - lineStart > longest) longest = i - lineStart
                lineStart = i + 1
            }
            if (text.length - lineStart > longest) longest = text.length - lineStart
            return TextMetrics(lines, longest)
        }
    }
}

/**
 * Where each line of one text begins, so the gutter can name the rows it is drawing.
 *
 * Held against the text itself rather than rebuilt per frame, and looked up by halving
 * rather than by counting from the top: the gutter asks this once per visible row, and
 * counting newlines from the start of the file would put the whole document back in the
 * draw path that clipping to the viewport just took it out of.
 */
private class LineIndex {
    private var source: String? = null
    private var starts = intArrayOf(0)

    fun forText(text: String): LineIndex {
        if (source === text) return this
        var count = 1
        for (c in text) if (c == '\n') count++
        val out = IntArray(count)
        var at = 0
        var n = 1
        while (n < count) {
            val nl = text.indexOf('\n', at)
            out[n++] = nl + 1
            at = nl + 1
        }
        starts = out
        source = text
        return this
    }

    /** The 1-based line containing [offset]. */
    fun lineAt(offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo + 1
    }
}
