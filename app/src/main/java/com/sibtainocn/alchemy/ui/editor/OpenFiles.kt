package com.sibtainocn.alchemy.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import java.io.File

/**
 * The open files, as a strip of names.
 *
 * Shown under the editor's bar and again above the explorer's list, because what is open
 * is a property of the session rather than of the editor: somebody browsing for the next
 * file to work on is exactly the person who wants one tap back to the last one. The
 * explorer passes no [current], and then no tab is drawn as selected.
 *
 * Reduced to the two things a tab is for on a phone: getting back to a file, and getting
 * rid of one. No glyph, no path, no close-others menu. A file with unwritten edits shows
 * a dot where its cross would be, which is the one piece of state a tab has to carry and
 * the one place there is room to put it.
 */
@Composable
fun OpenFilesStrip(
    tabs: List<File>,
    current: File?,
    unsaved: (File) -> Boolean,
    onSelect: (File) -> Unit,
    onClose: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberLazyListState()
    val currentPath = current?.absolutePath

    // The open file changes from the tree, from the explorer and from closing a tab as
    // well as from a tap in here, and in those cases it can easily be off the end of the
    // strip.
    LaunchedEffect(currentPath, tabs.size) {
        val index = tabs.indexOfFirst { it.absolutePath == currentPath }
        if (index >= 0) runCatching { scroll.animateScrollToItem(index) }
    }

    LazyRow(
        state = scroll,
        modifier = modifier.fillMaxWidth().background(InkRaised),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(tabs, key = { it.absolutePath }) { tab ->
            Tab(
                file = tab,
                active = tab.absolutePath == currentPath,
                unsaved = unsaved(tab),
                onSelect = { onSelect(tab) },
                onClose = { onClose(tab) },
            )
        }
    }
}

@Composable
private fun Tab(
    file: File,
    active: Boolean,
    unsaved: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.xs))
            .background(if (active) InkHigh else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 11.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) TextHigh else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Long enough for a real filename, short enough that three tabs still fit on
            // a phone before anything has to be scrolled to.
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Box(
            Modifier
                .padding(start = 3.dp)
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            if (unsaved) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            } else {
                Icon(Ico.Close, "Close " + file.name, Modifier.size(12.dp), tint = TextLow)
            }
        }
    }
}
