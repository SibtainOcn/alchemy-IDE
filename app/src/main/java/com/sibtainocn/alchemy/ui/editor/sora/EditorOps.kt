package com.sibtainocn.alchemy.ui.editor.sora

import com.sibtainocn.alchemy.data.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * The block operations behind the key bar that the editor does not already have.
 *
 * Most of what `SmartEdit` used to do is on [CodeEditor] itself - `selectAll`, `copyText`,
 * `cutText`, `pasteText`, `deleteText`, `duplicateLine`, `insertText`, `moveSelection`,
 * `undo`, `redo` - and calling those is better than keeping a second implementation of
 * each beside them. What is left is the handful with an opinion of Alchemy's own: a four
 * space indent that lands on a tab stop, a dedent, and comment tokens per language.
 *
 * Each runs inside one batch edit, so a block of forty lines is one thing to undo rather
 * than forty.
 *
 * Each also comes in two halves: the operation itself, which takes a buffer and the lines
 * to act on, and a wrapper that reads those lines off the editor's selection. That is not
 * ceremony - the first half is index arithmetic over a document, which is where the bugs
 * that silently corrupt a file live, and it is testable on its own only if it does not
 * need a view to exist first.
 */
object EditorOps {

    const val INDENT = "    "

    /** Every line the selection touches, inclusive, which is what a block operation acts on. */
    private fun touchedLines(editor: CodeEditor): IntRange =
        editor.cursor.leftLine..editor.cursor.rightLine

    private inline fun batch(text: Content, body: () -> Unit) {
        text.beginBatchEdit()
        try {
            body()
        } finally {
            text.endBatchEdit()
        }
    }

    /**
     * Indents the touched lines, or moves the caret to the next tab stop when nothing is
     * selected.
     *
     * The second half is why this is not simply `CodeEditor.indentLines`: with no
     * selection, Tab in the middle of a line should advance to the next stop rather than
     * push the whole line right.
     */
    fun indent(editor: CodeEditor) {
        if (!editor.cursor.isSelected) {
            val pad = INDENT.length - (editor.cursor.leftColumn % INDENT.length)
            editor.insertText(" ".repeat(pad), pad)
            return
        }
        val lines = touchedLines(editor)
        indentLines(editor.text, lines.first, lines.last)
    }

    fun indentLines(text: Content, first: Int, last: Int) {
        batch(text) {
            for (line in first..last) text.insert(line, 0, INDENT)
        }
    }

    /** Removes up to one indent from every touched line. Lines with none are left alone. */
    fun dedent(editor: CodeEditor) {
        val lines = touchedLines(editor)
        dedentLines(editor.text, lines.first, lines.last)
    }

    fun dedentLines(text: Content, first: Int, last: Int) {
        batch(text) {
            for (line in first..last) {
                val strip = text.getLineString(line)
                    .takeWhile { it == ' ' }
                    .length
                    .coerceAtMost(INDENT.length)
                if (strip > 0) text.delete(line, 0, line, strip)
            }
        }
    }

    /**
     * Comments or uncomments every line the selection touches.
     *
     * Which way it goes is decided once, for the whole block: if every non-blank line is
     * already commented the block is uncommented, otherwise all of it is commented. Doing
     * it per line instead would invert a mixed block into the other kind of mixed block.
     */
    fun toggleComment(editor: CodeEditor, lang: Language) {
        val lines = touchedLines(editor)
        toggleComment(editor.text, lines.first, lines.last, lang)
    }

    fun toggleComment(text: Content, first: Int, last: Int, lang: Language) {
        val token = when (lang) {
            Language.PYTHON, Language.SHELL, Language.CONFIG -> "# "
            // XML's comments wrap rather than prefix, and a half-written pair is worse
            // than no shortcut at all.
            Language.XML -> return
            else -> "// "
        }
        val bare = token.trimEnd()
        val lines = first..last
        val allCommented = lines.all {
            val line = text.getLineString(it)
            line.isBlank() || line.trimStart().startsWith(bare)
        }
        batch(text) {
            for (line in lines) {
                val content = text.getLineString(line)
                if (content.isBlank()) continue
                val indent = content.takeWhile { it == ' ' || it == '\t' }.length
                if (allCommented) {
                    val rest = content.substring(indent)
                    // The space after the token is ours, but a line commented by hand may
                    // not have one.
                    val cut = if (rest.startsWith(token)) token.length else bare.length
                    text.delete(line, indent, line, indent + cut)
                } else {
                    text.insert(line, indent, token)
                }
            }
        }
    }

    /**
     * Removes the touched lines, and the break that ended them.
     *
     * Taking the break that *follows* keeps the caret at the start of what was the next
     * line, which is where you want to be. On the last line of the file there is none, so
     * the one before is taken instead - otherwise deleting the last line would leave a
     * blank one behind it.
     */
    fun deleteLine(editor: CodeEditor) {
        val lines = touchedLines(editor)
        val landing = deleteLines(editor.text, lines.first, lines.last)
        editor.setSelection(landing, 0)
    }

    /** Returns the line the caret should land on. */
    fun deleteLines(text: Content, first: Int, last: Int): Int {
        batch(text) {
            when {
                last + 1 < text.lineCount -> text.delete(first, 0, last + 1, 0)
                first > 0 -> text.delete(
                    first - 1, text.getColumnCount(first - 1),
                    last, text.getColumnCount(last),
                )
                // The only line there is: empty it rather than removing it.
                else -> text.delete(first, 0, last, text.getColumnCount(last))
            }
        }
        return first.coerceAtMost(text.lineCount - 1)
    }
}
