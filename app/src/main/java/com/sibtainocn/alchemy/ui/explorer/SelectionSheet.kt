package com.sibtainocn.alchemy.ui.explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.SheetAction
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow

/**
 * What can be done to everything that is ticked.
 *
 * A sheet rather than the centred dialog one entry gets, and for the opposite reason: that
 * menu is anchored to a row you just pressed and wants to sit near it, while this one is
 * about a set with no single position on screen. It also arrives from the bottom of a
 * screen where the selection controls already are.
 *
 * The actions are the ones that mean something for a set. Rename and Open are missing
 * because they are answers to a question about one file, and offering them here would
 * either do nothing or do something surprising to forty things at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionSheet(
    selection: List<Entry>,
    /** Bytes across the ticked files. Folders are not walked, so this is a floor. */
    bytes: Long,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val files = selection.count { !it.isDir }
    val folders = selection.size - files

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(Modifier.padding(top = 18.dp)) {
            Text(
                "Selected items",
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                // What was picked, in the terms it was picked in. The size counts files
                // only, which is said rather than quietly rounded away.
                buildString {
                    if (folders > 0) append(if (folders == 1) "1 folder" else "$folders folders")
                    if (folders > 0 && files > 0) append(", ")
                    if (files > 0) {
                        append(if (files == 1) "1 file" else "$files files")
                        append("  ·  ")
                        append(Fmt.size(bytes))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )

            Spacer(Modifier.height(12.dp))
            HairlineDivider()
            Spacer(Modifier.height(4.dp))

            SheetAction(Ico.Copy, "Copy", detail = "Paste with the button in the corner", onClick = onCopy)
            SheetAction(Ico.Cut, "Cut", onClick = onCut)
            SheetAction(Ico.Move, "Move to", detail = "Pick the folder and go", onClick = onMove)
            SheetAction(Ico.Trash, "Delete", danger = true, onClick = onDelete)

            Spacer(Modifier.height(12.dp).windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}
