package com.sibtainocn.alchemy.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.theme.Hairline
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
 * rid of one. No glyph, no path, no close-others menu. A file with unwritten edits carries
 * a dot in front of its name, which is the one piece of state a tab has to hold.
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
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
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
            .background(if (active) InkHigh else InkRaised)
            // A tab that is only a background tint is hard to pick out of a strip of
            // them on a dark theme; the current one is outlined as well.
            .border(
                width = if (active) 1.dp else 0.7.dp,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else Hairline,
                shape = RoundedCornerShape(Radii.xs),
            )
            .clickable(onClick = onSelect)
            .padding(start = 13.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A dot in front rather than in place of the cross, so the state and the way out
        // of it are two different targets: closing a tab used to mean pressing the mark
        // that says it has unsaved work, which is the last thing anyone wants to poke.
        if (unsaved) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Text(
            file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) TextHigh else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Long enough for a real filename, short enough that two tabs and the start of
            // a third still fit on a phone.
            modifier = Modifier.widthIn(max = 168.dp),
        )
        Box(
            Modifier
                .padding(start = 4.dp)
                // Was 26dp around a 12dp cross, which is under the platform's own floor
                // for something meant to be tapped and was being missed.
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Ico.Close,
                "Close " + file.name,
                Modifier.size(15.dp),
                tint = if (active) TextMid else TextLow,
            )
        }
    }
}
