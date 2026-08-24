package dev.hazel.code.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hazel draws its own icons.
 *
 * The stock Material icon library is ~2 MB of paths for the dozen glyphs an editor
 * actually needs, and it carries a look every other app already has. These are stroked on
 * a 24-unit grid at a constant 1.7 weight, which keeps them optically consistent with the
 * monospace type and with each other.
 */
private const val WEIGHT = 1.7f

private fun stroked(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = WEIGHT,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun filled(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White))
        }
    }.build()

object Ico {
    val Back = stroked("back", "M14.5 5.5 L8 12 l6.5 6.5")
    val ChevronRight = stroked("chevron", "M9.5 5.5 L16 12 l-6.5 6.5")
    val ChevronDown = stroked("chevronDown", "M5.5 9.5 L12 16 l6.5 -6.5")
    val Search = stroked("search", "M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0 -13", "M15.8 15.8 L20 20")
    val Close = stroked("close", "M6 6 L18 18", "M18 6 L6 18")
    val Check = stroked("check", "M5 12.5 L10 17.5 L19 6.5")
    val Plus = stroked("plus", "M12 5 L12 19", "M5 12 L19 12")
    val Minus = stroked("minus", "M5 12 L19 12")
    val More = filled(
        "more",
        "M12 4.6a1.7 1.7 0 1 1 0 3.4 1.7 1.7 0 0 1 0 -3.4",
        "M12 10.3a1.7 1.7 0 1 1 0 3.4 1.7 1.7 0 0 1 0 -3.4",
        "M12 16a1.7 1.7 0 1 1 0 3.4 1.7 1.7 0 0 1 0 -3.4",
    )
    val Sort = stroked("sort", "M4 6.5 L15 6.5", "M4 12 L12 12", "M4 17.5 L9 17.5", "M17.5 8 L17.5 18", "M14.5 15 L17.5 18 L20.5 15")
    val Home = stroked("home", "M4 10.5 L12 4 l8 6.5", "M6.2 12 L6.2 19.5 L17.8 19.5 L17.8 12")
    val Save = stroked("save", "M5 5.8C5 5.4 5.4 5 5.8 5H16l3 3v10.2c0 .4-.4.8-.8.8H5.8c-.4 0-.8-.4-.8-.8Z", "M8 5 L8 10 L15 10 L15 5", "M8 19 L8 14 L16 14 L16 19")
    val Undo = stroked("undo", "M8.5 8 L4.5 12 L8.5 16", "M4.5 12 H14a5.5 5.5 0 0 1 0 11h-2.5")
    val Redo = stroked("redo", "M15.5 8 L19.5 12 L15.5 16", "M19.5 12 H10a5.5 5.5 0 0 0 0 11h2.5")
    val Wrap = stroked("wrap", "M4 6.5 L20 6.5", "M4 12 H16a3 3 0 0 1 0 6h-3", "M15 15 L12.5 18 L15 21", "M4 17.5 L9 17.5")
    val Indent = stroked("indent", "M4 6 L20 6", "M10 10.5 L20 10.5", "M10 15 L20 15", "M4 19.5 L20 19.5", "M4 10.5 L6.5 12.75 L4 15")
    val Dedent = stroked("dedent", "M4 6 L20 6", "M10 10.5 L20 10.5", "M10 15 L20 15", "M4 19.5 L20 19.5", "M6.5 10.5 L4 12.75 L6.5 15")
    val Hash = stroked("hash", "M9.5 4 L7.5 20", "M16.5 4 L14.5 20", "M4.5 9 L20 9", "M4 15 L19.5 15")
    val Code = stroked("code", "M8.5 8 L4 12 L8.5 16", "M15.5 8 L20 12 L15.5 16", "M13.5 5 L10.5 19")
    val Eye = stroked("eye", "M2.8 12S6.4 5.8 12 5.8 21.2 12 21.2 12 17.6 18.2 12 18.2 2.8 12 2.8 12Z", "M12 9.2a2.8 2.8 0 1 1 0 5.6 2.8 2.8 0 0 1 0 -5.6")
    val Trash = stroked("trash", "M4.5 7 L19.5 7", "M9.5 7 V4.8h5V7", "M6.5 7 L7.5 19.6c0 .5.4.9.9.9h7.2c.5 0 .9-.4.9-.9L17.5 7", "M10 11 L10 17", "M14 11 L14 17")
    val Pencil = stroked("pencil", "M4.5 19.5 L5.2 15.6 L16.1 4.7a1.6 1.6 0 0 1 2.3 0l1.7 1.7a1.6 1.6 0 0 1 0 2.3L9.2 19.6 Z", "M14.5 6.5 L17.9 9.9")
    val Copy = stroked("copy", "M9 9.2c0-.7.5-1.2 1.2-1.2h8.1c.7 0 1.2.5 1.2 1.2v9.1c0 .7-.5 1.2-1.2 1.2h-8.1c-.7 0-1.2-.5-1.2-1.2Z", "M15 8V5.7c0-.7-.5-1.2-1.2-1.2H5.7c-.7 0-1.2.5-1.2 1.2v8.1c0 .7.5 1.2 1.2 1.2H8")
    val Info = stroked("info", "M12 3.8a8.2 8.2 0 1 1 0 16.4 8.2 8.2 0 0 1 0 -16.4", "M12 11 L12 16", "M12 8 L12 8.6")
    val Hidden = stroked("hidden", "M4 4 L20 20", "M9.6 6.3A9.6 9.6 0 0 1 12 6c5.6 0 9.2 6 9.2 6a17 17 0 0 1 -3 3.6", "M6.4 8.6A17 17 0 0 0 2.8 12S6.4 18 12 18a9.4 9.4 0 0 0 3.2-.55")
    val Doc = stroked("doc", "M6 4.8c0-.4.4-.8.8-.8H14l4 4v11.2c0 .4-.4.8-.8.8H6.8c-.4 0-.8-.4-.8-.8Z", "M14 4 L14 8.2 L18 8.2")
    val Folder = stroked("folder", "M3.5 7c0-.8.7-1.5 1.5-1.5h3.6l2 2.2H19c.8 0 1.5.7 1.5 1.5v8.3c0 .8-.7 1.5-1.5 1.5H5c-.8 0-1.5-.7-1.5-1.5Z")
    val Font = stroked("font", "M5 19 L11 5 L13 5 L19 19", "M7.6 14.2 L16.4 14.2")
    val Numbers = stroked("numbers", "M5 6 L5 12", "M4 6.8 L5.6 5.5", "M3.8 16.2c0-1 .8-1.6 1.7-1.6.8 0 1.5.6 1.5 1.4 0 1.6-3.2 1.9-3.2 3.7h3.4", "M11 8 L20 8", "M11 16 L20 16")
    val Wrench = stroked("wrench", "M15.6 4.6a5 5 0 0 0 -6 6.4L4.6 16a2 2 0 0 0 2.8 2.8l5-5a5 5 0 0 0 6.4-6l-3 3-2.2-2.2Z")
}

/** Convenience so call sites read as one line. */
@Composable
fun rememberIcon(v: ImageVector): ImageVector = v
