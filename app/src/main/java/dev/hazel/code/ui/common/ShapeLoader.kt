package dev.hazel.code.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import dev.hazel.code.ui.theme.TextMid

/**
 * The indeterminate loader used everywhere in Hazel: a single small glyph that morphs
 * around a loop of rounded polygons while it spins — the Material 3 expressive
 * "shape loader" behaviour seen in Play Store.
 *
 * It is drawn from [RoundedPolygon] geometry rather than a spritesheet, so one instance
 * covers every size without extra assets.
 */
private const val MORPH_STEP_MS = 650
private const val SPIN_MS = 4200

private fun loaderShapes(): List<RoundedPolygon> = listOf(
    RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.82f,
        rounding = CornerRounding(0.45f),
        innerRounding = CornerRounding(0.45f),
    ),
    RoundedPolygon(
        numVertices = 4,
        rounding = CornerRounding(0.32f),
    ),
    RoundedPolygon.star(
        numVerticesPerRadius = 4,
        innerRadius = 0.5f,
        rounding = CornerRounding(0.4f),
        innerRounding = CornerRounding(0.32f),
    ),
    RoundedPolygon.pill(width = 1f, height = 0.62f),
    RoundedPolygon.star(
        numVerticesPerRadius = 6,
        innerRadius = 0.75f,
        rounding = CornerRounding(0.5f),
        innerRounding = CornerRounding(0.5f),
    ),
    RoundedPolygon.circle(numVertices = 12),
)

@Composable
fun ShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val morphs = remember {
        val shapes = loaderShapes().map { it.normalized() }
        shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) }
    }

    val transition = rememberInfiniteTransition(label = "shape-loader")

    // One continuous ramp across the whole shape loop; the integer part selects the
    // morph, the fraction drives it. Keeping it as a single animation means the
    // hand-off between shapes never stutters.
    val cursor by transition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(MORPH_STEP_MS * morphs.size, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "morph-cursor",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SPIN_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    Canvas(modifier = modifier.size(size)) {
        val index = cursor.toInt().coerceIn(0, morphs.lastIndex)
        val raw = cursor - index
        // Ease each hop so the shape settles before the next one starts.
        val progress = raw * raw * (3f - 2f * raw)

        val side = kotlin.math.min(this.size.width, this.size.height)
        val path = morphs[index].toPath(progress).apply {
            val m = android.graphics.Matrix()
            m.setScale(side, side)
            transform(m)
        }.asComposePath()

        val dx = (this.size.width - side) / 2f
        val dy = (this.size.height - side) / 2f
        rotate(spin) {
            translate(dx, dy) {
                drawPath(path, color)
            }
        }
    }
}

/** Centred loader for whole-screen waits, with an optional one-word status. */
@Composable
fun LoadingPane(label: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShapeLoader(size = 34.dp)
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = TextMid)
            }
        }
    }
}
