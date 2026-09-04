package com.sibtainocn.alchemy.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.SortBy
import com.sibtainocn.alchemy.ui.common.EntryGlyph
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import kotlinx.coroutines.launch
import java.io.File

/** One line of the flattened tree: what it is, and how deep it sits. */
private data class TreeRow(val entry: Entry, val depth: Int)

/**
 * The folder around the open file, as a tree you can walk without leaving the editor.
 *
 * Reached by tapping the filename, because the filename is the thing on screen that most
 * looks like it should answer "what else is in here". Folders open in place rather than
 * pushing a new screen: the point of a tree is seeing a file's neighbours and its
 * parent's neighbours at once, which a stack of folder screens never shows. Tapping a
 * file swaps what the editor is holding and closes the sheet.
 *
 * Dotfiles are listed. A `.gitignore` is exactly the kind of file you open from here, and
 * a code tree that hid half of itself would send you back to the file browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeSheet(
    current: File,
    onOpenFile: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var root by remember {
        mutableStateOf(current.parentFile ?: FileStore.storageRoot)
    }
    // Loaded once per folder and kept: collapsing and reopening a branch should not go
    // back to the disk for something that has not changed under it.
    val children = remember { mutableStateMapOf<String, List<Entry>>() }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun read(dir: File): List<Entry> = runCatching {
        FileStore.list(dir, showHidden = true, sortBy = SortBy.NAME, descending = false)
    }.getOrDefault(emptyList())

    LaunchedEffect(root.absolutePath) {
        loading = true
        val listed = runCatching {
            FileStore.list(root, showHidden = true, sortBy = SortBy.NAME, descending = false)
        }
        denied = listed.isFailure
        children[root.absolutePath] = listed.getOrDefault(emptyList())
        loading = false
    }

    // Flattened on every pass rather than cached: it is a walk over what is already in
    // memory, and the alternative is a second structure that can disagree with the first.
    val rows = buildList {
        fun walk(dir: File, depth: Int) {
            children[dir.absolutePath]?.forEach { entry ->
                add(TreeRow(entry, depth))
                if (entry.isDir && entry.file.absolutePath in expanded) walk(entry.file, depth + 1)
            }
        }
        walk(root, 0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opens half way and can be dragged the rest of the way up. A tree is something
        // you glance at to find one file, and a sheet that takes the whole screen to do
        // that hides the file you came from while you look for the next one.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        // Taller than the half it opens at, so there is somewhere for the drag to go.
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        root.name.ifBlank { "Internal storage" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHigh,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        root.absolutePath,
                        fontFamily = CodeFont,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = TextLow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // One step out, for when the file you want is a sibling of the folder
                // rather than of the file.
                root.parentFile?.let { parent ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radii.sm))
                            .clickable {
                                expanded = expanded + root.absolutePath
                                root = parent
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Ico.Back,
                            "Up one level",
                            Modifier.size(18.dp).rotate(90f),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HairlineDivider()

            Box(Modifier.weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { ShapeLoader(size = 26.dp) }
                    denied -> Message("Android would not let Alchemy read this folder.")
                    rows.isEmpty() -> Message("This folder is empty.")
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(rows, key = { it.entry.file.absolutePath }) { row ->
                            TreeRowView(
                                row = row,
                                open = row.entry.file.absolutePath in expanded,
                                active = row.entry.file.absolutePath == current.absolutePath,
                            ) {
                                val path = row.entry.file.absolutePath
                                if (!row.entry.isDir) {
                                    onOpenFile(row.entry.file)
                                    onDismiss()
                                    return@TreeRowView
                                }
                                expanded = if (path in expanded) {
                                    expanded - path
                                } else {
                                    // Read on first open only; the map is what makes a
                                    // reopened branch instant.
                                    if (!children.containsKey(path)) {
                                        scope.launch { children[path] = read(row.entry.file) }
                                    }
                                    expanded + path
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun TreeRowView(
    row: TreeRow,
    open: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 0f else -90f, Motion.snappy(), label = "twist")

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            // Indent is the only thing saying where in the tree a row sits, so it has to
            // be big enough to count and small enough that six levels still fit.
            .padding(start = 12.dp + (row.depth * 15).dp, end = 16.dp)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (row.entry.isDir) {
                Icon(
                    Ico.ChevronDown,
                    null,
                    Modifier.size(13.dp).rotate(turn),
                    tint = TextMid,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        EntryGlyph(row.entry, 28.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary else TextHigh,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            Fmt.subtitle(row.entry),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.5.sp,
            color = TextLow,
            maxLines = 1,
        )
    }
}
