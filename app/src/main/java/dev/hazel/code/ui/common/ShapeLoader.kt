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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
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
private const val SPIN_MS = 2600

/**
 * Four shapes, not two and not six.
 *
 * A two-shape morph reverses back through the in-between states it just came from, which
 * reads as a wobble rather than progress. A loop of four never retraces, so the motion has
 * a direction. More than four and each hop gets too short to register at this size.
 */
private fun loaderShapes(): List<RoundedPolygon> = listOf(
    RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.35f)),
    RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.7f, rounding = CornerRounding(0.28f)),
    RoundedPolygon(numVertices = 7, rounding = CornerRounding(0.4f)),
    RoundedPolygon.star(numVerticesPerRadius = 5, innerRadius = 0.62f, rounding = CornerRounding(0.3f)),
)

@Composable
fun ShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    // Built once. Allocating a Morph inside the draw would churn on every frame of an
    // animation that never stops.
    val morphs = remember {
        val shapes = loaderShapes()
        shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) }
    }

    val transition = rememberInfiniteTransition(label = "shape-loader")

    // One continuous ramp across the whole loop: the integer part selects the morph, the
    // fraction drives it. A single animation means the hand-off never stutters.
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
        drawMorph(morphs[index], cursor - index, spin, color)
    }
}

/**
 * The polygons are described in a space centred on the origin with radius 1, so placing
 * one is just: move to the middle, turn, scale to fit.
 */
private fun DrawScope.drawMorph(morph: Morph, fraction: Float, degrees: Float, color: Color) {
    val path = morph.toPath(fraction).asComposePath()
    val radius = minOf(size.width, size.height) / 2f

    translate(size.width / 2f, size.height / 2f) {
        rotate(degrees, pivot = Offset.Zero) {
            scale(radius, radius, pivot = Offset.Zero) {
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
