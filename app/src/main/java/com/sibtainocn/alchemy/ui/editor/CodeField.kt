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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.syntax.Highlighter
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.LocalAccents

/**
 * The code surface.
 *
 * Notable choices:
 * - The gutter is a sibling canvas driven by the field's own [TextLayoutResult], not a
 *   column of Text rows. That is the only way numbers stay aligned when a long line
 *   wraps across three visual rows.
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

    val style = remember(fontSizeSp) {
        TextStyle(
            fontFamily = CodeFont,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.55f).sp,
            color = accents.codeText,
        )
    }
    val gutterStyle = remember(fontSizeSp) {
        style.copy(fontSize = (fontSizeSp - 2).coerceAtLeast(8).sp, textAlign = TextAlign.End)
    }

    val highlighted = remember(value.text, language, accents) {
        // Highlighting is a nice-to-have; the text is not. A scanner bug on some
        // pathological input shows plain text rather than crashing the screen.
        runCatching { Highlighter.highlight(value.text, language, accents) }
            .getOrElse { AnnotatedString(value.text) }
    }
    val transformation = remember(highlighted) {
        VisualTransformation { TransformedText(highlighted, OffsetMapping.Identity) }
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

    // Line count drives the gutter width so a 4-digit file does not clip.
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutterWidth = remember(lineCount, fontSizeSp) {
        val digits = lineCount.toString().length.coerceAtLeast(2)
        ((digits * (fontSizeSp - 2) * 0.62f) + 20f).dp
    }

    // Only relevant when wrapping is off, where the longest line sets the scrollable
    // width. Keyed on the longest line's *length* rather than the text: the editor font
    // is monospace, so width is a function of character count, and typing inside a line
    // that is not the longest re-measures nothing at all.
    val longestLineLength = remember(value.text, wordWrap) {
        if (wordWrap) 0 else value.text.lineSequence().maxOfOrNull { it.length } ?: 0
    }
    val contentWidth = remember(longestLineLength, fontSizeSp, wordWrap) {
        if (wordWrap) 0.dp else {
            val sample = "0".repeat(longestLineLength.coerceAtMost(4000))
            val px = measurer.measure(AnnotatedString(sample), style).size.width
            with(density) { (px + 48).toDp() }
        }
    }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val cursorLine = layout?.let { l ->
        runCatching { l.getLineForOffset(value.selection.start) }.getOrNull()
    }

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
                        // The layout's own text, not the field's current text. Those two
                        // disagree for a frame after every keystroke, and reading the
                        // newer one against the older layout is what made the numbers
                        // flicker between N and N+1 while typing.
                        val text = l.layoutInput.text.text

                        // The active row's band continues across the gutter, so the
                        // highlight reads as one line rather than two halves.
                        cursorLine?.takeIf { it < l.lineCount }?.let { line ->
                            drawRect(
                                color = accents.caretLine,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, l.getLineTop(line)),
                                size = androidx.compose.ui.geometry.Size(
                                    width = size.width,
                                    height = l.getLineBottom(line) - l.getLineTop(line),
                                ),
                            )
                        }

                        var lineNo = 1
                        for (i in 0 until l.lineCount) {
                            val start = l.getLineStart(i)
                            // A wrapped continuation carries no number of its own, which
                            // is what makes the gutter count real lines rather than rows.
                            val isParagraphStart = start == 0 || text.getOrNull(start - 1) == '\n'
                            if (isParagraphStart) {
                                val active = cursorLine == i
                                val label = lineNo.toString()
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
                                lineNo++
                            }
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
