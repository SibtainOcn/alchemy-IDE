package com.sibtainocn.alchemy.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.SortBy
import com.sibtainocn.alchemy.ui.common.EntryGlyph
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import java.io.File

/**
 * Pick a folder, for the operations that need a destination rather than a clipboard.
 *
 * Folders only: files cannot be moved into, so listing them would offer taps that do
 * nothing. The confirm button names the folder you are standing in rather than sitting
 * under a heading somewhere else, because the whole question the sheet asks is "here?"
 *
 * [blocked] is the thing being moved. Standing anywhere inside it disables the button,
 * since a folder cannot become its own child, and says so rather than letting the tap
 * fail later with a message about canonical paths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    start: File,
    title: String,
    confirmLabel: String,
    blocked: File?,
    onDismiss: () -> Unit,
    onPick: (File) -> Unit,
) {
    var dir by remember { mutableStateOf(if (start.isDirectory) start else FileStore.storageRoot) }
    var folders by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }
    var forbidden by remember { mutableStateOf(false) }

    // Listing and the self-containment check both touch the disk, so they happen together
    // off the main thread and land as one state change.
    LaunchedEffect(dir.absolutePath, blocked?.absolutePath) {
        loading = true
        val listed = runCatching {
            FileStore.list(dir, showHidden = true, sortBy = SortBy.NAME, descending = false)
        }
        folders = listed.getOrDefault(emptyList()).filter { it.isDir }
        denied = listed.isFailure
        forbidden = blocked != null &&
            withContext(Dispatchers.IO) { FileStore.isInside(dir, blocked) }
        loading = false
    }

    val atRoot = dir.absolutePath == FileStore.storageRoot.absolutePath || dir.parentFile == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxHeight(0.82f)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh)
                Spacer(Modifier.height(6.dp))
                Text(
                    dir.absolutePath,
                    fontFamily = CodeFont,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = TextLow,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
            }

            HairlineDivider()

            Box(Modifier.weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { ShapeLoader(size = 26.dp) }
                    denied -> Note("Android would not let Alchemy read this folder.")
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        if (!atRoot) {
                            item(key = "..") {
                                PickerRow(
                                    label = "..",
                                    detail = dir.parentFile?.name?.ifBlank { "Up one level" }
                                        ?: "Up one level",
                                    leading = {
                                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Ico.Back,
                                                null,
                                                Modifier.size(17.dp).rotate(90f),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                ) { dir.parentFile?.let { dir = it } }
                            }
                        }
                        items(folders, key = { it.file.absolutePath }) { entry ->
                            PickerRow(
                                label = entry.name,
                                detail = Fmt.subtitle(entry),
                                leading = { EntryGlyph(entry, 34.dp) },
                            ) { dir = entry.file }
                        }
                        if (folders.isEmpty()) {
                            item(key = "empty") {
                                Note("No folders inside this one.", Modifier.padding(top = 28.dp))
                            }
                        }
                    }
                }
            }

            HairlineDivider()

            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (forbidden) {
                    Text(
                        "A folder cannot be moved inside itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetButton(
                        label = "Cancel",
                        filled = false,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    SheetButton(
                        label = confirmLabel,
                        filled = true,
                        enabled = !forbidden && !denied && !loading,
                        modifier = Modifier.weight(1.4f),
                        onClick = { onPick(dir) },
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    detail: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLow, maxLines = 1)
        }
        Icon(Ico.ChevronRight, null, Modifier.size(15.dp), tint = TextLow)
    }
}

@Composable
private fun SheetButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        !filled -> InkHigh
        enabled -> MaterialTheme.colorScheme.primary
        else -> InkHigh
    }
    val content = when {
        !filled -> TextMid
        enabled -> MaterialTheme.colorScheme.onPrimary
        else -> TextLow
    }
    Box(
        modifier
            .clip(RoundedCornerShape(Radii.md))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
