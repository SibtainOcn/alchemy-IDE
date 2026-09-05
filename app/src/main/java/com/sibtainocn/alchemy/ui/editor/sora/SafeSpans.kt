package com.sibtainocn.alchemy.ui.editor.sora

import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.text.CharPosition

/**
 * Colours that cannot take the app down by being out of date.
 *
 * The renderer asks for the spans on whichever line it is about to draw. The colours for
 * that line are produced on another thread, from a copy of the document taken at some
 * earlier moment, and the two are not locked together - so there is always a window where
 * the buffer has more lines than the colours do. Landing in that window throws from inside
 * a draw call, on the main thread, which is a dead process.
 *
 * The right answer to "I have no colours for line 40,000" is not to throw. It is to draw
 * that line in the colours of the last line there was an answer for, and let the next
 * analysis correct it a few milliseconds later. Nobody sees it. Everybody sees a crash.
 *
 * That window has now been narrowed twice at its source - once by giving every line a span
 * rather than only the ones whose style changed, and once by padding to the buffer's real
 * length rather than the length of a collection that was cut short. This is here because
 * the class of bug is a race in someone else's render loop, and the next one will be found
 * the same way: by someone losing what they were typing.
 */
class SafeSpans(private val real: Spans) : Spans {

    override fun adjustOnInsert(start: CharPosition, end: CharPosition) =
        real.adjustOnInsert(start, end)

    override fun adjustOnDelete(start: CharPosition, end: CharPosition) =
        real.adjustOnDelete(start, end)

    override fun getLineCount(): Int = real.lineCount

    override fun supportsModify(): Boolean = real.supportsModify()

    override fun modify(): Spans.Modifier = real.modify()

    override fun read(): Spans.Reader = ClampedReader(real.read(), real)

    private class ClampedReader(
        private val real: Spans.Reader,
        private val owner: Spans,
    ) : Spans.Reader {

        /** The nearest line that actually has colours. Never negative, never past the end. */
        private fun clamp(line: Int): Int {
            val last = owner.lineCount - 1
            if (last < 0) return 0
            return line.coerceIn(0, last)
        }

        override fun moveToLine(line: Int) = real.moveToLine(clamp(line))

        override fun getSpanCount(): Int = real.spanCount

        override fun getSpanAt(index: Int): Span = real.getSpanAt(index)

        override fun getSpansOnLine(line: Int): List<Span> = real.getSpansOnLine(clamp(line))
    }
}
