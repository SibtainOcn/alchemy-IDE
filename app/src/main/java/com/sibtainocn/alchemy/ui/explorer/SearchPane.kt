package com.sibtainocn.alchemy.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.SearchKind
import com.sibtainocn.alchemy.ui.common.EmptyState
import com.sibtainocn.alchemy.ui.common.EntryGlyph
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * The one-tap searches, as a row of chips under the field.
 *
 * A search box on a phone asks for typing, and half the time what somebody wants is not a
 * name at all but a kind: every PDF, every recording, every Python file under here. A chip
 * is that search without the typing, and it composes with the field rather than replacing
 * it, so "py" plus the code chip is a narrower question than either on its own.
 */
@Composable
fun SearchChips(selected: SearchKind?, onPick: (SearchKind) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(InkRaised),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SearchKind.entries.toList(), key = { it.name }) { kind ->
            Chip(kind = kind, active = kind == selected, onClick = { onPick(kind) })
        }
    }
}

@Composable
private fun Chip(kind: SearchKind, active: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) primary.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (active) Modifier else Modifier.border(
                    width = 0.7.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(50),
                )
            )
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            iconFor(kind),
            null,
            Modifier.size(15.dp),
            tint = if (active) primary else TextMid,
        )
        Text(
            kind.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) primary else TextMid,
        )
    }
}

private fun iconFor(kind: SearchKind) = when (kind) {
    SearchKind.FOLDERS -> Ico.Folder
    SearchKind.CODE, SearchKind.PYTHON -> Ico.Code
    SearchKind.MARKDOWN -> Ico.Book
    SearchKind.PDF, SearchKind.DOCUMENTS -> Ico.Doc
    SearchKind.IMAGES -> Ico.Eye
    SearchKind.AUDIO, SearchKind.VIDEO -> Ico.Play
    SearchKind.ARCHIVES -> Ico.Paste
    SearchKind.APPS -> Ico.Plus
}

/**
 * What the walk has found, filling in as it goes.
 *
 * Every row carries where it came from, because a result out of a recursive search is
 * meaningless without it: three files called `notes.md` are the same row three times until
 * the folder each one lives in is on screen.
 */
@Composable
fun SearchResults(
    root: java.io.File,
    query: String,
    kind: SearchKind?,
    results: List<Entry>,
    running: Boolean,
    truncated: Boolean,
    onOpen: (Entry) -> Unit,
    onHold: (Entry) -> Unit,
) {
    val asked = query.isNotBlank() || kind != null

    when {
        !asked -> EmptyState(
            "Search " + (root.name.ifBlank { "storage" }),
            "Type part of a name, or tap a kind above to find every one of them under here.",
            Ico.Search,
        )

        results.isEmpty() && running -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            ShapeLoader(size = 30.dp)
        }

        results.isEmpty() -> EmptyState(
            "No matches",
            "Nothing under " + root.name.ifBlank { "storage" } + " answers to that.",
            Ico.Search,
        )

        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 108.dp),
        ) {
            item(key = "count") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        // Said while it is still running, because a count that only
                        // appears at the end leaves the list looking finished when it is
                        // not.
                        if (running) "${results.size} so far" else Fmt.matches(results.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLow,
                    )
                }
            }

            items(results, key = { it.file.absolutePath }) { entry ->
                ResultRow(entry, onClick = { onOpen(entry) }, onLongClick = { onHold(entry) })
            }

            if (truncated) {
                item(key = "capped") {
                    Text(
                        "Showing the first ${results.size}. Narrow the search to see the rest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(entry: Entry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryGlyph(entry)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                where(entry),
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!entry.isDir) {
            Spacer(Modifier.width(10.dp))
            Text(
                Fmt.size(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = TextMid,
            )
        }
    }
}

/**
 * The folder a result came out of, with the part everything shares taken off.
 *
 * `/storage/emulated/0/` in front of every row is forty characters that never vary, which
 * is the same reason the terminal prompt does not carry the working directory.
 */
private fun where(entry: Entry): String {
    val parent = entry.file.parent ?: return ""
    val root = FileStore.storageRoot.absolutePath
    return when {
        parent == root -> "Internal storage"
        parent.startsWith("$root/") -> parent.removePrefix("$root/")
        else -> parent
    }
}
