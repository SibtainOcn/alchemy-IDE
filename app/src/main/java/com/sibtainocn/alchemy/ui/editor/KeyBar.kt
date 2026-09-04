package com.sibtainocn.alchemy.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.TextHigh

/**
 * The strip above the keyboard: the keys a phone IME does not have.
 *
 * One row, everything in it draggable. There is no pinned group any more - pinning Ctrl,
 * Shift, Caps and Tab cost the width of four keys on every screen and stopped exactly the
 * keys people most want to move from being moved.
 *
 * Arming Ctrl swaps the row for the shortcut set rather than overlaying anything, so there
 * is never a question about what a key will do when you tap it.
 */
private const val KEY_GAP_DP = 6

/** How near an edge a dragged key must come before the row starts scrolling under it. */
private const val DRAG_EDGE_DP = 46

/** Pixels per frame the row scrolls while a key is held against an edge. */
private const val DRAG_SCROLL_PX = 13f

@Composable
fun KeyBar(
    language: Language,
    mods: Modifiers,
    order: List<String>,
    onMods: (Modifiers) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onOutcome: (KeyOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = mods.ctrl,
        transitionSpec = { fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy()) },
        label = "keyset",
        modifier = modifier
            .fillMaxWidth()
            .background(InkRaised),
    ) { ctrlArmed ->
        if (ctrlArmed) {
            ShortcutRow(language, mods, onMods, onOutcome)
        } else {
            ReorderableRow(language, mods, order, onMods, onOrderChange, onOutcome)
        }
    }
}

/** The Ctrl set. Fixed order - these are commands, not characters you arrange to taste. */
@Composable
private fun ShortcutRow(
    language: Language,
    mods: Modifiers,
    onMods: (Modifiers) -> Unit,
    onOutcome: (KeyOutcome) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A way back out without running anything.
        KeyCap(label = "ctrl", active = true, onClick = { onMods(Modifiers(ctrl = false)) })

        KeyBarModel.ctrlKeys.forEach { key ->
            KeyCap(
                label = key.label,
                accented = true,
                onClick = {
                    val (outcome, next) = KeyBarModel.press(key, mods, language)
                    onMods(next)
                    if (outcome !is KeyOutcome.None) onOutcome(outcome)
                },
            )
        }
    }
}

/**
 * The scrolling row, with long-press-and-drag reordering.
 *
 * Every key is composed rather than lazily windowed: there are a few dozen of them, and
 * having all their widths measured is what makes the drop position exact instead of
 * estimated.
 *
 * A drag runs against a local copy of the order and is written back on release. Writing
 * each crossing back instead rebuilt the row from a new list mid-gesture, which restarted
 * the pointer input and ended the drag - so a key could only ever be moved one place per
 * long-press. Holding a key against either edge scrolls the row underneath it, so a key
 * can be taken the length of the bar without letting go.
 */
@Composable
private fun ReorderableRow(
    language: Language,
    mods: Modifiers,
    order: List<String>,
    onMods: (Modifiers) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onOutcome: (KeyOutcome) -> Unit,
) {
    val saved = remember(language, order) {
        KeyBarModel.applyOrder(KeyBarModel.defaultKeys(language), order)
    }
    var keys by remember(saved) { mutableStateOf(saved) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gapPx = with(density) { KEY_GAP_DP.dp.toPx() }
    val edgePx = with(density) { DRAG_EDGE_DP.dp.toPx() }

    val scroll = rememberScrollState()
    val widths = remember { mutableStateMapOf<String, Float>() }
    val lefts = remember { mutableStateMapOf<String, Float>() }
    var rowLeft by remember { mutableStateOf(0f) }
    var rowWidth by remember { mutableStateOf(0f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableStateOf(0f) }

    /** Moves the dragged key past every neighbour the drag has covered. */
    fun settle() {
        val id = draggingId ?: return
        val from = keys.indexOfFirst { it.id == id }
        if (from < 0) return
        val drop = KeyBarModel.dropTarget(keys.map { widths[it.id] ?: 0f }, from, dragDx, gapPx)
        if (drop.index != from) {
            keys = KeyBarModel.reorder(keys, from, drop.index)
            dragDx = drop.residual
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Ends the gesture, keeping wherever the key was let go of.
     *
     * The order is written back every time rather than only when it differs from the one
     * loaded: this runs from a gesture that outlives several recompositions, and the
     * "loaded" order it could compare against is the one captured when the gesture began.
     */
    fun release() {
        draggingId = null
        dragDx = 0f
        onOrderChange(keys.map { it.id })
    }

    val dragged = draggingId
    if (dragged != null) {
        LaunchedEffect(dragged) {
            while (true) {
                withFrameNanos { }
                val left = (lefts[dragged] ?: 0f) - rowLeft + dragDx
                val right = left + (widths[dragged] ?: 0f)
                val push = when {
                    right > rowWidth - edgePx -> DRAG_SCROLL_PX
                    left < edgePx -> -DRAG_SCROLL_PX
                    else -> 0f
                }
                if (push != 0f) {
                    // The finger has not moved, the row has - so the key owes that
                    // distance to stay under it, and that is what carries it onward.
                    val moved = scroll.scrollBy(push)
                    if (moved != 0f) {
                        dragDx += moved
                        settle()
                    }
                }
            }
        }
    }

    Row(
        Modifier
            .onGloballyPositioned {
                rowLeft = it.positionInRoot().x
                rowWidth = it.size.width.toFloat()
            }
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            // Keyed so a reorder moves the cap's composition rather than re-creating it.
            // Without this the drag would be cancelled by its own first crossing.
            key(key.id) {
                val dragging = draggingId == key.id

                KeyCap(
                    label = key.label,
                    dimmed = key.kind != KeyKind.INSERT,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        // Measured outside the drag offset, so the reading is the slot the
                        // key belongs to rather than where the finger has taken it.
                        .onGloballyPositioned {
                            widths[key.id] = it.size.width.toFloat()
                            lefts[key.id] = it.positionInRoot().x
                        }
                        .graphicsLayer { translationX = if (dragging) dragDx else 0f }
                        .pointerInput(key.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = key.id
                                    dragDx = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { release() },
                                // A cancelled drag keeps the arrangement on screen too:
                                // losing the pointer is not a reason to snap the row back
                                // to where it was several keys ago.
                                onDragCancel = { release() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDx += amount.x
                                    settle()
                                },
                            )
                        },
                    onClick = {
                        val (outcome, next) = KeyBarModel.press(key, mods, language)
                        onMods(next)
                        if (outcome !is KeyOutcome.None) onOutcome(outcome)
                    },
                )
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accented: Boolean = false,
    dimmed: Boolean = false,
    dragging: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            dragging -> 1.12f
            pressed -> 0.9f
            else -> 1f
        },
        Motion.snappy(),
        label = "key",
    )

    val background = when {
        active -> MaterialTheme.colorScheme.primary
        pressed || dragging -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        active -> MaterialTheme.colorScheme.onPrimary
        pressed || dragging || accented -> MaterialTheme.colorScheme.primary
        dimmed -> TextHigh.copy(alpha = 0.72f)
        else -> TextHigh
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(38.dp)
            .defaultMinSize(minWidth = 42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = CodeFont,
            fontSize = if (label.length > 3) 12.sp else 14.sp,
            color = content,
        )
    }
}
