package com.sibtainocn.alchemy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import com.sibtainocn.alchemy.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Alchemy runs one palette only: true black. There is no light mode to fall back to, so the
 * scheme is declared once and [isSystemInDarkTheme] is deliberately ignored.
 */

val Ink = Color(0xFF000000)          // page
val InkRaised = Color(0xFF0A0A0A)    // sheets, bars, cards
val InkHigh = Color(0xFF121212)      // pressed / hovered fills
val Hairline = Color(0xFF1C1C1E)     // 1px separations, outlines
val Cyan = Color(0xFF0FCBE8)      // the interactive accent, lifted from the user's VS Code chrome
val CyanDim = Color(0xFF06323A)
val CyanSoft = Color(0xFF66F2F4)
val Amber = Color(0xFFFFB300)     // reserved for folder glyphs in the explorer
val AmberDim = Color(0xFF3D2B00)
val TextHigh = Color(0xFFF2F2F3)
val TextMid = Color(0xFF9A9A9F)
val TextLow = Color(0xFF5E5E63)
val Danger = Color(0xFFFF5449)

private val AlchemyScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00181D),
    primaryContainer = CyanDim,
    onPrimaryContainer = CyanSoft,
    secondary = TextMid,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextHigh,
    surface = Ink,
    onSurface = TextHigh,
    surfaceVariant = InkRaised,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkHigh,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Danger,
    onError = Ink,
    scrim = Color(0xCC000000),
)

/**
 * The face used by the editor, the gutter and every code span in the previewer.
 *
 * JetBrains Mono rather than the platform's monospace: the system fallback varies by
 * vendor, and the characters a reader has to tell apart in code are exactly the ones it
 * draws alike. This one separates 0 from O and 1 from l, and its taller x-height keeps a
 * line legible at the sizes a phone editor actually runs at.
 *
 * SIL Open Font License 1.1, which is compatible with the GPL this app ships under.
 */
val CodeFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * The face the terminal draws in.
 *
 * Hack rather than the editor's face, because the two are read differently. Editor text is
 * scanned in structured blocks with syntax colour carrying much of the meaning; terminal
 * text is a wall of one colour where every character has to stand alone, often at a
 * smaller size, and often a character nobody chose to type.
 *
 * Hack descends from Bitstream Vera by way of DejaVu, which is what the desktop terminals
 * this is imitating have used for twenty years. Wide, even, and unambiguous at small
 * sizes: dotted zero, slashed-through nothing, a distinct 1, l and I.
 *
 * SIL Open Font License 1.1, plus the Bitstream Vera licence it inherits. Both compatible
 * with the GPL.
 */
val TerminalFont = FontFamily(
    Font(R.font.hack_regular, FontWeight.Normal),
    Font(R.font.hack_bold, FontWeight.Bold),
)

private val AlchemyType = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.W300, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.W400, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W500, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W500, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.W400, fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W500, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W500, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

/**
 * Syntax + chrome colors that are not part of the M3 role set.
 *
 * The code palette is Monokai, kept at its canonical hues so Python reads the way it
 * does in an editor people already know - pink keywords and operators, green
 * definitions, cyan italic types, purple literals - but dropped onto true black
 * instead of Monokai's grey-brown ground.
 */
data class AlchemyAccents(
    val codeText: Color = Color(0xFFF8F8F2),   // Monokai default foreground
    val keyword: Color = Color(0xFFF92672),    // def, class, return, import, global
    val operator: Color = Color(0xFFF92672),   // = += not in - pink, same as keywords
    val function: Color = Color(0xFFA6E22E),   // definition + call names
    val builtin: Color = Color(0xFF66D9EF),    // types and builtins, italic
    val string: Color = Color(0xFFE6DB74),
    val number: Color = Color(0xFFAE81FF),
    val comment: Color = Color(0xFF75715E),
    val decorator: Color = Color(0xFFA6E22E),
    val selfRef: Color = Color(0xFFFD971F),    // self, cls, params - orange italic
    val punctuation: Color = Color(0xFFF8F8F2),
    val brace: Color = Color(0xFFF8F8F2),
    val folder: Color = Color(0xFFFFB300),
    val folderShade: Color = Color(0xFFE08A00),
    // Inactive line numbers still have to be readable on #000000; the previous value
    // was dark enough that only the current line showed.
    val gutter: Color = Color(0xFF6B6B78),
    val gutterActive: Color = Color(0xFF0FCBE8),
    // The current-row band. #0D0D0D on true black was present in the buffer and absent
    // to the eye; this is still quiet but actually locates the caret.
    val caretLine: Color = Color(0xFF17171C),
)

val LocalAccents = compositionLocalOf { AlchemyAccents() }

/** Corner radius scale - M3 shape tokens, tightened one step for a denser tool feel. */
object Radii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 18.dp
    val lg = 26.dp
    val xl = 34.dp
}

@Composable
fun AlchemyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AlchemyScheme, typography = AlchemyType, content = content)
}
