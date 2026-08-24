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
import androidx.compose.foundation.layout.width
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
import dev.hazel.code.ui.theme.Hairline
import dev.hazel.code.ui.theme.InkRaised
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextMid

/**
 * The strip above the keyboard: the keys a phone IME does not have.
 *
 * Layout is two groups. Ctrl, Shift, Caps and Tab are pinned on the left and never scroll
 * away, because a modifier you have to go looking for is not a modifier. Everything else
 * scrolls, and can be long-pressed and dragged into whatever order suits the language you
 * actually write.
 *
 * Arming Ctrl swaps the scrolling group for the shortcut set rather than overlaying
 * anything, so there is never a question about what a key will do when you tap it.
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
    Row(
        modifier
            .fillMaxWidth()
            .background(InkRaised)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pinned modifier group.
        Row(
            Modifier.padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyBarModel.modifierKeys.forEach { key ->
                val active = when (key.id) {
                    KeyBarModel.SHIFT -> mods.shift
                    KeyBarModel.CTRL -> mods.ctrl
                    KeyBarModel.CAPS -> mods.caps
                    else -> false
                }
                KeyCap(
                    label = KeyBarModel.labelFor(key, mods),
                    active = active,
                    dimmed = true,
                    onClick = {
                        val (outcome, next) = KeyBarModel.press(key, mods, language)
                        onMods(next)
                        if (outcome !is KeyOutcome.None) onOutcome(outcome)
                    },
                )
            }
        }

        Box(
            Modifier
                .padding(horizontal = 7.dp)
                .width(1.dp)
                .height(24.dp)
                .background(Hairline)
        )

        AnimatedContent(
            targetState = mods.ctrl,
            transitionSpec = { fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy()) },
            label = "keyset",
            modifier = Modifier.weight(1f),
        ) { ctrlArmed ->
            if (ctrlArmed) {
                ShortcutRow(language, mods, onMods, onOutcome)
            } else {
                ReorderableRow(language, mods, order, onMods, onOrderChange, onOutcome)
            }
        }
    }
}

/** The Ctrl set. Fixed order — these are commands, not characters you arrange to taste. */
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
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
 * The scrolling group, with long-press-and-drag reordering.
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
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            val dragging = draggingId == key.id

            KeyCap(
                label = KeyBarModel.labelFor(key, mods),
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

    val lit = active || pressed || dragging
    val background = when {
        active -> MaterialTheme.colorScheme.primary
        pressed || dragging -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        active -> MaterialTheme.colorScheme.onPrimary
        lit -> MaterialTheme.colorScheme.primary
        accented -> MaterialTheme.colorScheme.primary
        dimmed -> TextMid
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
