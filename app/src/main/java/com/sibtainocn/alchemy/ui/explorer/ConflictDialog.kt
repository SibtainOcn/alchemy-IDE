package com.sibtainocn.alchemy.ui.explorer

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * The name is already taken. What should happen to it?
 *
 * Both sides are shown with their size and date rather than the name alone, because the
 * question is never really "replace?" but "which of these two is the one I want", and
 * that is not answerable from a filename. Two folders of the same name combine instead
 * of displacing each other, so the strong action says Merge and the wording changes with
 * it.
 *
 * Dismissing stops the whole transfer, which is the only reading of a dismissed prompt
 * that cannot lose anything.
 */
@Composable
fun ConflictDialog(prompt: ConflictPrompt) {
    val conflict = prompt.conflict
    val merging = conflict.isMerge
    var applyToAll by remember { mutableStateOf(false) }

    fun answer(resolution: FileStore.Resolution) = prompt.reply(resolution, applyToAll)

    Dialog(
        onDismissRequest = { prompt.reply(FileStore.Resolution.CANCEL) },
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 14.dp)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        if (merging) "Folder already exists" else "File already exists",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHigh,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        conflict.existing.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(14.dp))
                    Side("Already there", conflict.existing, merging)
                    Spacer(Modifier.height(8.dp))
                    Side("Coming in", conflict.source, merging)
                }

                Spacer(Modifier.height(14.dp))

                if (prompt.repeatable) {
                    ApplyToAll(applyToAll) { applyToAll = !applyToAll }
                }

                HairlineDivider()
                Spacer(Modifier.height(4.dp))

                Choice(
                    if (merging) Ico.Move else Ico.Save,
                    if (merging) "Merge" else "Replace",
                    if (merging) "Keep the folder and settle each file inside it"
                    else "The one already there is overwritten",
                ) { answer(FileStore.Resolution.OVERWRITE) }

                Choice(Ico.Copy, "Keep both", "Lands beside it as a numbered copy") {
                    answer(FileStore.Resolution.KEEP_BOTH)
                }

                Choice(Ico.Close, "Skip", "Leave what is there and carry on") {
                    answer(FileStore.Resolution.SKIP)
                }

                Spacer(Modifier.height(4.dp))
                HairlineDivider()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { prompt.reply(FileStore.Resolution.CANCEL) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = TextMid)
                }
            }
        }
    }
}

/** One half of the comparison: which file, how big, how old. */
@Composable
private fun Side(label: String, file: java.io.File, folders: Boolean) {
    val detail = if (folders && file.isDirectory) {
        val count = file.list()?.size ?: 0
        if (count == 1) "1 item" else count.toString() + " items"
    } else {
        Fmt.size(file.length())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .background(InkHigh)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
            modifier = Modifier.width(92.dp),
        )
        Text(
            detail + "  ·  " + Fmt.date(file.lastModified()),
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
            maxLines = 1,
        )
    }
}

@Composable
private fun ApplyToAll(checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else InkHigh
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Ico.Check,
                    null,
                    Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            "Do this for everything else",
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) TextHigh else TextMid,
        )
    }
}

@Composable
private fun Choice(icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = TextMid)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
    }
}
