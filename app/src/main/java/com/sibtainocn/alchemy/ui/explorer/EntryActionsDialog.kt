package com.sibtainocn.alchemy.ui.explorer

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.ui.common.EntryGlyph
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * The long-press menu, as a centred dialog rather than a bottom sheet.
 *
 * On a tall phone a sheet puts destructive actions under the thumb that just pressed the
 * row; a dialog lands the menu next to what you are looking at and makes Delete a
 * deliberate reach.
 *
 * The actions are grouped by what they do to the file rather than listed flat: opening
 * it, moving it about, naming it, destroying it. Cut and Copy arm the paste control and
 * leave the folder alone; Move asks where to go and does it in one step, which is the
 * shorter road when the destination is somewhere you are not currently standing.
 */
@Composable
fun EntryActionsDialog(
    entry: Entry,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    /** Hands the file to another app. Null for folders, which have nowhere to go. */
    onOpenWith: (() -> Unit)? = null,
    /** The system share sheet. Null for folders: Android cannot share a directory. */
    onShare: (() -> Unit)? = null,
    /** Enters multi-select mode with this entry pre-selected. Null when selection is not available. */
    onSelect: (() -> Unit)? = null,
    onTogglePin: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
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
            // Seven actions plus a header is taller than a small screen in landscape, and
            // a menu that cannot reach its own Delete row is worse than a long one.
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 18.dp)) {
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
                if (onOpenWith != null) {
                    DialogAction(Ico.Eye, "Open with another app", onClick = onOpenWith)
                }
                if (onShare != null) {
                    DialogAction(Ico.Share, "Share", onClick = onShare)
                }

                if (onSelect != null) {
                    DialogAction(Ico.Check, "Select", onClick = onSelect)
                }

                DialogAction(
                    Ico.Pin,
                    if (pinned) "Unpin" else "Pin to top",
                    accent = pinned,
                    onClick = onTogglePin,
                )

                Separator()

                DialogAction(Ico.Cut, "Cut", onClick = onCut)
                DialogAction(Ico.Copy, "Copy", onClick = onCopy)
                DialogAction(Ico.Move, "Move to", onClick = onMove)

                Separator()

                DialogAction(Ico.Pencil, "Rename", onClick = onRename)
                DialogAction(Ico.Doc, "Copy path", onClick = onCopyPath)
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
private fun Separator() {
    HairlineDivider(Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
}

@Composable
private fun DialogAction(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    /** For a row describing a state the entry is already in, rather than an action on it. */
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        danger -> MaterialTheme.colorScheme.error
        accent -> MaterialTheme.colorScheme.primary
        else -> TextHigh
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = if (danger || accent) tint else TextMid)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
