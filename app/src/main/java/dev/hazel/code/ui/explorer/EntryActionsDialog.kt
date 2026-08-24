package dev.hazel.code.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.hazel.code.data.Entry
import dev.hazel.code.ui.common.EntryGlyph
import dev.hazel.code.ui.common.Fmt
import dev.hazel.code.ui.common.HairlineDivider
import dev.hazel.code.ui.common.Ico
import dev.hazel.code.ui.theme.Radii
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextLow
import dev.hazel.code.ui.theme.TextMid

/**
 * The long-press menu, as a centred dialog rather than a bottom sheet.
 *
 * On a tall phone a sheet puts destructive actions under the thumb that just pressed the
 * row; a dialog lands the menu next to what you are looking at and makes Delete a
 * deliberate reach.
 */
@Composable
fun EntryActionsDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(vertical = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EntryGlyph(entry, 38.dp)
                    Column {
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHigh,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Fmt.subtitle(entry) + "  ·  " + Fmt.date(entry.modified),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLow,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HairlineDivider()
                Spacer(Modifier.height(6.dp))

                DialogAction(
                    if (entry.isDir) Ico.Folder else Ico.Code,
                    if (entry.isDir) "Open folder" else "Open in editor",
                    onClick = onOpen,
                )
                DialogAction(Ico.Pencil, "Rename", onClick = onRename)
                DialogAction(Ico.Copy, "Copy path", onClick = onCopyPath)
                DialogAction(Ico.Trash, "Delete", danger = true, onClick = onDelete)

                Spacer(Modifier.height(6.dp))
                HairlineDivider()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = TextMid)
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else TextHigh
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = if (danger) tint else TextMid)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
