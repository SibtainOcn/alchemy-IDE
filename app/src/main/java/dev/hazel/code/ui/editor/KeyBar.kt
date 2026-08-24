package dev.hazel.code.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.hazel.code.data.Language
import dev.hazel.code.ui.common.Motion
import dev.hazel.code.ui.theme.CodeFont
import dev.hazel.code.ui.theme.InkRaised
import dev.hazel.code.ui.theme.TextHigh

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
 * having all their widths measured is what makes the swap threshold exact instead of
 * estimated.
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
    val keys = remember(language, order) {
        KeyBarModel.applyOrder(KeyBarModel.defaultKeys(language), order)
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gapPx = with(density) { KEY_GAP_DP.dp.toPx() }

    val widths = remember { mutableStateMapOf<String, Float>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableStateOf(0f) }

    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            val dragging = draggingId == key.id

            KeyCap(
                label = key.label,
                dimmed = key.kind != KeyKind.INSERT,
                dragging = dragging,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationX = if (dragging) dragDx else 0f }
                    .onGloballyPositioned { widths[key.id] = it.size.width.toFloat() }
                    .pointerInput(key.id, keys) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = key.id
                                dragDx = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                draggingId = null
                                dragDx = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                dragDx = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDx += amount.x
                                val index = keys.indexOfFirst { it.id == key.id }
                                if (index < 0) return@detectDragGesturesAfterLongPress

                                // Swap once the key has travelled past the midpoint of its
                                // neighbour, then carry the remainder so a long drag keeps
                                // moving instead of stalling after one place.
                                if (dragDx > 0) {
                                    val next = keys.getOrNull(index + 1) ?: return@detectDragGesturesAfterLongPress
                                    val step = (widths[next.id] ?: 0f) + gapPx
                                    if (step > 0 && dragDx > step / 2f) {
                                        onOrderChange(
                                            KeyBarModel.reorder(keys, index, index + 1).map { it.id }
                                        )
                                        dragDx -= step
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                } else {
                                    val prev = keys.getOrNull(index - 1) ?: return@detectDragGesturesAfterLongPress
                                    val step = (widths[prev.id] ?: 0f) + gapPx
                                    if (step > 0 && dragDx < -step / 2f) {
                                        onOrderChange(
                                            KeyBarModel.reorder(keys, index, index - 1).map { it.id }
                                        )
                                        dragDx += step
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
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
