package com.sibtainocn.alchemy.ui.editor.sora

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sibtainocn.alchemy.data.Language
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

/** Where the caret is, one-based, as the status line reads it. */
data class Caret(val line: Int, val column: Int, val selected: Int)

/**
 * The code surface.
 *
 * A view rather than a composable, and deliberately so: drawing only the lines that are on
 * screen means owning the canvas, the scrolling and the input connection together, which
 * is not something a `BasicTextField` can be persuaded into. What that buys is that none
 * of the cost here is a function of how long the file is. See docs/SORA-MIGRATION.md.
 *
 * The buffer belongs to the view model, not to this view. [content] is handed over as the
 * object it already is, so the undo stack that came with it survives being shown, hidden
 * and shown again - which is what makes a tab a place your work is rather than a bookmark.
 */
@Composable
fun SoraCodeField(
    content: Content,
    language: Language,
    palette: EditorPalette,
    fontSizeSp: Int,
    wordWrap: Boolean,
    autoPair: Boolean,
    lineNumbers: Boolean,
    readOnly: Boolean,
    onChanged: () -> Unit,
    onCaret: (Caret) -> Unit,
    /** Handed the view once, so the screen can send it commands the key bar produces. */
    onReady: (CodeEditor) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The callbacks are subscribed once, when the view is built, so they must not close
    // over the versions that were current at that moment.
    val changed by rememberUpdatedState(onChanged)
    val caret by rememberUpdatedState(onCaret)
    val ready by rememberUpdatedState(onReady)

    val scheme = remember(palette) { palette.toColorScheme() }
    val editorLanguage = remember(language, autoPair) { AlchemyLanguage(language, autoPair) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setTypefaceText(Typeface.MONOSPACE)
                setTypefaceLineNumber(Typeface.MONOSPACE)
                subscribeEvent(ContentChangeEvent::class.java) { _, _ -> changed() }
                subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                    caret(
                        Caret(
                            line = event.left.line + 1,
                            column = event.left.column + 1,
                            selected = event.right.index - event.left.index,
                        )
                    )
                }
                ready(this)
            }
        },
        update = { editor ->
            // Only when the buffer is actually a different one. Handing the same text back
            // would drop the caret to the top and clear the undo stack on every
            // recomposition, which is most of them.
            if (editor.text !== content) {
                editor.setText(content, true, null)
            }
            editor.setEditorLanguage(editorLanguage)
            editor.setColorScheme(scheme)
            editor.setTextSize(fontSizeSp.toFloat())
            editor.setWordwrap(wordWrap)
            editor.setLineNumberEnabled(lineNumbers)
            editor.setEditable(!readOnly)
        },
        onRelease = { it.release() },
    )
}
