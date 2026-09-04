package com.sibtainocn.alchemy.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileKind
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.LocalAccents

/**
 * The row leading glyph.
 *
 * Two shapes, chosen by what the file is rather than by one rule for everything.
 *
 * Source files keep the extension tag: in a folder of thirty `.py` files the extension is
 * the only part worth reading, and setting it in the language's own syntax colour makes a
 * mixed directory sortable by eye. Everything else gets a page with a turned corner and a
 * mark for its kind, because "png" spelled out in a grey square tells you less than a
 * picture frame does, and a folder of media then reads as media at a glance.
 *
 * All of it is drawn here rather than shipped as assets. Every measurement is a fraction
 * of the requested size, so the same glyph serves a 34dp picker row and a 42dp list row
 * without a second set of paths.
 */
@Composable
fun EntryGlyph(entry: Entry, size: Dp = 42.dp) {
    if (entry.isDir) {
        FolderGlyph(entry, size)
    } else if (entry.kind == FileKind.CODE) {
        CodeGlyph(entry, size)
    } else {
        PageGlyph(entry, size)
    }
}

/**
 * Colours for the kinds that have no syntax of their own.
 *
 * Kept apart from the Monokai set the editor uses: these sit beside code glyphs in the
 * same list, so a video has to be a colour no language has taken.
 */
private object KindInk {
    val text = Color(0xFFCBD1DA)
    val pdf = Color(0xFFE5484D)
    val archive = Color(0xFFF5A524)
    val image = Color(0xFF8B5CF6)
    val video = Color(0xFF3B82F6)
    val audio = Color(0xFF14B8A6)
    val app = Color(0xFF22C55E)
    val binary = Color(0xFF8A8F98)
}

private fun inkFor(kind: FileKind): Color = when (kind) {
    FileKind.TEXT -> KindInk.text
    FileKind.PDF -> KindInk.pdf
    FileKind.ARCHIVE -> KindInk.archive
    FileKind.IMAGE -> KindInk.image
    FileKind.VIDEO -> KindInk.video
    FileKind.AUDIO -> KindInk.audio
    FileKind.APP -> KindInk.app
    FileKind.BINARY, FileKind.CODE -> KindInk.binary
}

@Composable
private fun FolderGlyph(entry: Entry, size: Dp) {
    val a = LocalAccents.current
    val alpha = if (entry.isHidden) 0.45f else 1f
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = CornerRadius(w * 0.11f, w * 0.11f)

        // Back tab, offset up and inset from the right edge.
        drawRoundRect(
            color = a.folderShade.copy(alpha = alpha),
            topLeft = Offset(w * 0.06f, h * 0.16f),
            size = Size(w * 0.52f, h * 0.30f),
            cornerRadius = r,
        )
        // Body, lit from the top so it reads as a solid object rather than a flat swatch.
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    a.folder.copy(alpha = alpha),
                    a.folder.copy(alpha = 0.82f * alpha),
                ),
                startY = h * 0.28f,
                endY = h * 0.80f,
            ),
            topLeft = Offset(w * 0.06f, h * 0.28f),
            size = Size(w * 0.88f, h * 0.52f),
            cornerRadius = r,
        )
        // Highlight strip across the top of the body.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.30f * alpha),
            topLeft = Offset(w * 0.12f, h * 0.33f),
            size = Size(w * 0.30f, h * 0.05f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
        )
    }
}

/**
 * A source file, as its extension on a tinted plate.
 *
 * The plate is a gradient rather than a flat wash and its edge is a hairline rather than
 * a border: at 42dp that is the difference between a chip and a sticker.
 */
@Composable
private fun CodeGlyph(entry: Entry, size: Dp) {
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
    val tag = tagFor(entry.name)

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val inset = w * 0.08f
            val side = w * 0.84f
            val radius = CornerRadius(w * 0.28f, w * 0.28f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.09f)),
                    startY = inset,
                    endY = inset + side,
                ),
                topLeft = Offset(inset, inset),
                size = Size(side, side),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = tint.copy(alpha = 0.5f),
                topLeft = Offset(inset, inset),
                size = Size(side, side),
                cornerRadius = radius,
                style = Stroke(width = w * 0.03f),
            )
        }
        Text(
            text = tag,
            color = tint,
            fontFamily = CodeFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * (if (tag.length >= 4) 0.21f else 0.27f)).sp,
            lineHeight = (size.value * 0.30f).sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A page with its top corner turned back, for everything that is not source.
 *
 * The silhouette carries the "this is a document" half of the message and the mark inside
 * carries the rest, so the two never have to compete for the same few pixels. Kinds with
 * a natural picture get one; kinds that are really just a format get their name.
 */
@Composable
private fun PageGlyph(entry: Entry, size: Dp) {
    val kind = entry.kind
    val ink = inkFor(kind)
    val dimmed = if (entry.isHidden) 0.5f else 1f
    // A pale page needs dark marks on it; a saturated one needs light.
    val mark = if (kind == FileKind.TEXT) Color(0xFF3B4048) else Color.White
    val label = when (kind) {
        FileKind.PDF -> "PDF"
        FileKind.APP -> "APK"
        FileKind.BINARY -> tagFor(entry.name)
        else -> null
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val left = w * 0.15f
            val right = w * 0.85f
            val top = w * 0.07f
            val bottom = w * 0.93f
            val fold = w * 0.26f

            val page = Path().apply {
                moveTo(left, top)
                lineTo(right - fold, top)
                lineTo(right, top + fold)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(
                path = page,
                brush = Brush.verticalGradient(
                    listOf(ink.copy(alpha = dimmed), ink.copy(alpha = 0.84f * dimmed)),
                    startY = top,
                    endY = bottom,
                ),
            )
            // The turned corner: the same sheet seen from behind, so it is the page
            // colour lifted rather than a second colour laid on top.
            drawPath(
                path = Path().apply {
                    moveTo(right - fold, top)
                    lineTo(right, top + fold)
                    lineTo(right - fold, top + fold)
                    close()
                },
                color = Color.White.copy(alpha = 0.42f * dimmed),
            )

            if (label == null) {
                drawMark(kind, mark.copy(alpha = 0.92f * dimmed), left, right, top, bottom)
            }
        }
        if (label != null) {
            Text(
                text = label,
                color = mark.copy(alpha = 0.95f * dimmed),
                fontFamily = CodeFont,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * (if (label.length >= 4) 0.18f else 0.22f)).sp,
                lineHeight = (size.value * 0.26f).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The picture inside the page, for the kinds that have one. */
private fun DrawScope.drawMark(
    kind: FileKind,
    ink: Color,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
) {
    val w = right - left
    val h = bottom - top
    val cx = (left + right) / 2f
    val cy = top + h * 0.58f
    val line = w * 0.09f

    when (kind) {
        // Ruled lines, shorter at the foot the way a paragraph ends.
        FileKind.TEXT -> {
            val widths = listOf(0.62f, 0.62f, 0.62f, 0.40f)
            widths.forEachIndexed { i, fraction ->
                val y = top + h * (0.40f + i * 0.135f)
                drawLine(
                    color = ink,
                    start = Offset(left + w * 0.19f, y),
                    end = Offset(left + w * 0.19f + w * fraction, y),
                    strokeWidth = h * 0.045f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // A frame with a horizon and a sun in it.
        FileKind.IMAGE -> {
            drawCircle(ink, radius = w * 0.075f, center = Offset(left + w * 0.34f, cy - h * 0.16f))
            drawPath(
                path = Path().apply {
                    moveTo(left + w * 0.18f, cy + h * 0.16f)
                    lineTo(left + w * 0.42f, cy - h * 0.06f)
                    lineTo(left + w * 0.58f, cy + h * 0.09f)
                    lineTo(left + w * 0.70f, cy - h * 0.01f)
                    lineTo(left + w * 0.82f, cy + h * 0.16f)
                    close()
                },
                color = ink,
            )
        }

        // A play triangle, sitting in the optical centre rather than the true one.
        FileKind.VIDEO -> {
            drawPath(
                path = Path().apply {
                    moveTo(cx - w * 0.16f, cy - h * 0.19f)
                    lineTo(cx + w * 0.24f, cy)
                    lineTo(cx - w * 0.16f, cy + h * 0.19f)
                    close()
                },
                color = ink,
            )
        }

        // A quaver: stem, flag, and the head hanging off the bottom of it.
        FileKind.AUDIO -> {
            drawLine(
                color = ink,
                start = Offset(cx - w * 0.02f, cy - h * 0.22f),
                end = Offset(cx - w * 0.02f, cy + h * 0.12f),
                strokeWidth = line * 0.62f,
                cap = StrokeCap.Round,
            )
            drawPath(
                path = Path().apply {
                    moveTo(cx - w * 0.02f, cy - h * 0.22f)
                    lineTo(cx + w * 0.22f, cy - h * 0.14f)
                    lineTo(cx + w * 0.22f, cy - h * 0.02f)
                    lineTo(cx - w * 0.02f, cy - h * 0.10f)
                    close()
                },
                color = ink,
            )
            drawCircle(ink, radius = w * 0.10f, center = Offset(cx - w * 0.12f, cy + h * 0.12f))
        }

        // A zip pull, with the teeth running up to it.
        FileKind.ARCHIVE -> {
            drawLine(
                color = ink,
                start = Offset(cx, top + h * 0.20f),
                end = Offset(cx, cy - h * 0.04f),
                strokeWidth = line * 0.7f,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(h * 0.055f, h * 0.045f),
                    0f,
                ),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(cx - w * 0.10f, cy - h * 0.02f),
                size = Size(w * 0.20f, h * 0.22f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            )
        }

        else -> Unit
    }
}

/** The extension, trimmed to something that fits inside a glyph. */
private fun tagFor(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext.isEmpty() -> "txt"
        ext.length > 4 -> ext.take(4)
        else -> ext
    }
}
