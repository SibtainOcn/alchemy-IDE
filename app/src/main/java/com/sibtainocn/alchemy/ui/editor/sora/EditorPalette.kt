package com.sibtainocn.alchemy.ui.editor.sora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sibtainocn.alchemy.ui.theme.AlchemyAccents
import com.sibtainocn.alchemy.ui.theme.Cyan
import com.sibtainocn.alchemy.ui.theme.CyanDim
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.Ink
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * The colour slots the highlighter paints with.
 *
 * The point of naming slots rather than colours is that the scanner never learns what
 * anything looks like. It says "this run is a keyword"; the scheme in force decides what a
 * keyword is. Swapping themes is then swapping one object, with no re-scan of the file and
 * no invalidation of anything the analyzer has already produced - which is the whole
 * reason a light theme, or a second highlighting scheme, is cheap to add later.
 *
 * sora's own slots are reused wherever one means the same thing. The rest start above
 * [EditorColorScheme.END_COLOR_ID] (83), which is the last id sora will ever assign
 * itself, so a future version of the library cannot collide with these.
 */
object CodeSlot {
    const val KEYWORD = EditorColorScheme.KEYWORD
    const val OPERATOR = EditorColorScheme.OPERATOR
    const val COMMENT = EditorColorScheme.COMMENT
    const val STRING = EditorColorScheme.LITERAL
    const val FUNCTION = EditorColorScheme.FUNCTION_NAME
    const val TEXT = EditorColorScheme.TEXT_NORMAL

    /** Alchemy's own, past the end of sora's range. */
    const val NUMBER = 100
    const val BUILTIN = 101
    const val DECORATOR = 102
    const val SELF_REF = 103
    const val PUNCTUATION = 104
    const val BRACE = 105
}

/**
 * One complete look for the code surface: the syntax colours, and the furniture around
 * them.
 *
 * [AlchemyAccents] already carries everything the syntax scanner distinguishes, plus the
 * gutter and the caret band, and it is a data class - so a second theme is one more
 * instance of this rather than a branch anywhere. The fields here are the parts of the
 * editor that are not syntax and not currently expressed anywhere else: the page it sits
 * on, the selection, the handles, the scrollbars.
 *
 * Defaults are the dark theme the app ships today, so nothing changes until a second
 * palette is actually written.
 */
data class EditorPalette(
    val accents: AlchemyAccents = AlchemyAccents(),
    val background: Color = Ink,
    val selection: Color = CyanDim,
    /** The caret, and the grab handles either side of a selection. */
    val handle: Color = Cyan,
    val scrollBar: Color = Hairline,
    val divider: Color = Hairline,
)

/**
 * Builds the scheme sora renders with.
 *
 * Every slot is set explicitly rather than left to sora's default, because its defaults
 * are a light theme and anything missed would arrive as a bright band on a black page.
 */
fun EditorPalette.toColorScheme(): EditorColorScheme = EditorColorScheme().also { s ->
    fun put(slot: Int, color: Color) = s.setColor(slot, color.toArgb())

    // The page and its furniture.
    put(EditorColorScheme.WHOLE_BACKGROUND, background)
    put(EditorColorScheme.LINE_NUMBER_BACKGROUND, background)
    put(EditorColorScheme.LINE_NUMBER_PANEL, accents.gutterActive)
    put(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, background)
    put(EditorColorScheme.LINE_NUMBER, accents.gutter)
    put(EditorColorScheme.LINE_NUMBER_CURRENT, accents.gutterActive)
    put(EditorColorScheme.LINE_DIVIDER, divider)
    put(EditorColorScheme.CURRENT_LINE, accents.caretLine)
    put(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection)
    put(EditorColorScheme.SELECTION_INSERT, handle)
    put(EditorColorScheme.SELECTION_HANDLE, handle)
    put(EditorColorScheme.SCROLL_BAR_THUMB, scrollBar)
    put(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, handle)
    put(EditorColorScheme.SCROLL_BAR_TRACK, background)
    put(EditorColorScheme.BLOCK_LINE, divider)
    put(EditorColorScheme.BLOCK_LINE_CURRENT, accents.gutter)

    // What the scanner paints with.
    put(EditorColorScheme.TEXT_NORMAL, accents.codeText)
    put(CodeSlot.KEYWORD, accents.keyword)
    put(CodeSlot.OPERATOR, accents.operator)
    put(CodeSlot.COMMENT, accents.comment)
    put(CodeSlot.STRING, accents.string)
    put(CodeSlot.FUNCTION, accents.function)
    put(CodeSlot.NUMBER, accents.number)
    put(CodeSlot.BUILTIN, accents.builtin)
    put(CodeSlot.DECORATOR, accents.decorator)
    put(CodeSlot.SELF_REF, accents.selfRef)
    put(CodeSlot.PUNCTUATION, accents.punctuation)
    put(CodeSlot.BRACE, accents.brace)
}
