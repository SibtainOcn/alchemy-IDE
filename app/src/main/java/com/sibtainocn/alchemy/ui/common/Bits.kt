package com.sibtainocn.alchemy.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import kotlinx.coroutines.delay

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 0.7.dp, color = Hairline)
}

/** One row in a bottom sheet: icon, label, optional destructive tint. */
@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = if (danger) tint else TextMid)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLow)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    detail: String,
    icon: ImageVector? = null,
    /** Optional way out of the state, shown under the detail when both are given. */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radii.md)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(26.dp), tint = TextLow)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * Text-entry dialog used for new files, new folders and renames. It selects the basename
 * and leaves the extension alone, which is what you want ~every time you rename a source
 * file.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "Create",
    hint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    var value by remember {
        val stem = initial.substringBeforeLast('.', initial).length
        mutableStateOf(TextFieldValue(initial, TextRange(0, stem)))
    }
    val valid = value.text.isNotBlank() && !value.text.contains('/')

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // The dialog window needs a beat to take focus before the field can claim it.
        delay(80)
        runCatching { focus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.lg),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    shape = RoundedCornerShape(Radii.sm),
                    placeholder = { Text(hint ?: "name", color = TextLow) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (valid) onConfirm(value.text.trim())
                    }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(value.text.trim()) }, enabled = valid) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.lg),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMid) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) } },
    )
}

/**
 * The question asked when work is about to be thrown away: write it, drop it, or stay.
 *
 * Three answers rather than two, because the two-answer version - discard or cancel - has
 * no button for the thing most people came to do. Save leads, cancel is the quiet one, and
 * discard is the only one drawn in the error colour, since it is the only one that
 * destroys anything.
 */
@Composable
fun SaveOrDiscardDialog(
    title: String,
    body: String,
    saveLabel: String = "Save",
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.lg),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMid) },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(saveLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) }
                TextButton(onClick = onDiscard) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

/**
 * The activity this context belongs to, through however many wrappers sit between.
 *
 * A composition's context is not reliably the activity: themed wrappers, view-inflation
 * wrappers and the ones dialogs and popups add all present themselves as a Context. Walking
 * the base chain is the answer that holds on every API level, where a cast holds only on
 * the ones that happen not to wrap.
 */
fun Context.activity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** A one-pixel-tall spacer that reads as a separator without the weight of a divider. */
@Composable
fun Gap(height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.height(height).fillMaxWidth().background(Color.Transparent))
}
