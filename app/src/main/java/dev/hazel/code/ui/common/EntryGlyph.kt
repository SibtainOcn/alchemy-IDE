package dev.hazel.code.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hazel.code.data.Entry
import dev.hazel.code.data.Language
import dev.hazel.code.ui.theme.CodeFont
import dev.hazel.code.ui.theme.LocalAccents

/**
 * The row leading glyph.
 *
 * Folders are drawn rather than iconified - a solid body with a lighter tab, matching the
 * shape in the reference. Files skip the interchangeable page-with-a-corner icon and show
 * their extension in the language's own syntax colour, so a directory of sources is
 * scannable by type at a glance.
 */
@Composable
fun EntryGlyph(entry: Entry, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val a = LocalAccents.current
    if (entry.isDir) {
        FolderGlyph(size, a.folder, a.folderShade, dimmed = entry.isHidden)
    } else {
        FileGlyph(entry, size)
    }
}

@Composable
private fun FolderGlyph(size: androidx.compose.ui.unit.Dp, body: Color, tab: Color, dimmed: Boolean) {
    val alpha = if (dimmed) 0.45f else 1f
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = CornerRadius(w * 0.10f, w * 0.10f)

        // Back tab, offset up and inset from the right edge.
        drawRoundRect(
            color = tab.copy(alpha = alpha),
            topLeft = Offset(w * 0.06f, h * 0.16f),
            size = Size(w * 0.52f, h * 0.30f),
            cornerRadius = r,
        )
        // Body.
        drawRoundRect(
            color = body.copy(alpha = alpha),
            topLeft = Offset(w * 0.06f, h * 0.28f),
            size = Size(w * 0.88f, h * 0.52f),
            cornerRadius = r,
        )
        // Highlight strip across the top of the body, as in the reference rows.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.30f * alpha),
            topLeft = Offset(w * 0.12f, h * 0.33f),
            size = Size(w * 0.30f, h * 0.05f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
        )
    }
}

@Composable
private fun FileGlyph(entry: Entry, size: androidx.compose.ui.unit.Dp) {
    val a = LocalAccents.current
    val tint = when (entry.language) {
        Language.PYTHON -> a.function       // Monokai green
        Language.MARKDOWN -> a.builtin      // cyan
        Language.JSON, Language.CONFIG -> a.string
        Language.XML -> a.keyword
        Language.KOTLIN, Language.JAVA -> a.selfRef
        Language.JS -> a.decorator
        Language.C_LIKE -> a.number
        Language.SHELL -> a.comment
        Language.PLAIN -> a.punctuation.copy(alpha = 0.55f)
    }
    val tag = entry.name.substringAfterLast('.', "").lowercase()
        .let { if (it.isEmpty() || it.length > 4) "txt" else it }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            drawRoundRect(
                color = tint.copy(alpha = 0.13f),
                topLeft = Offset(w * 0.08f, w * 0.08f),
                size = Size(w * 0.84f, w * 0.84f),
                cornerRadius = CornerRadius(w * 0.26f, w * 0.26f),
            )
            drawRoundRect(
                color = tint.copy(alpha = 0.45f),
                topLeft = Offset(w * 0.08f, w * 0.08f),
                size = Size(w * 0.84f, w * 0.84f),
                cornerRadius = CornerRadius(w * 0.26f, w * 0.26f),
                style = Stroke(width = w * 0.035f),
            )
        }
        Text(
            text = tag,
            color = tint,
            fontFamily = CodeFont,
            fontSize = if (tag.length >= 4) 9.sp else 11.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
