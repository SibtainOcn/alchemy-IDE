package com.sibtainocn.alchemy.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * One motion vocabulary for the whole app.
 *
 * M3 expressive motion is spring-based, not curve-based: things overshoot slightly and
 * settle, and nothing uses a fixed duration except the loader. Three springs cover
 * everything here - anything that needs a fourth is probably doing too much.
 */
object Motion {

    /** Default for size, offset and alpha on interactive elements. */
    fun <T> standard() = spring<T>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** For emphasised entrances - sheets, FAB expansion, screen swaps. */
    fun <T> expressive() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = 380f,
    )

    /** Quick, no overshoot - press states and toggles. */
    fun <T> snappy() = spring<T>(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMedium,
    )

    fun offset() = spring<IntOffset>(
        dampingRatio = 0.82f,
        stiffness = 400f,
        visibilityThreshold = IntOffset(1, 1),
    )
}
