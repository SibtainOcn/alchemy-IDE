package com.sibtainocn.alchemy.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * What the editor shows while a file is being decoded.
 *
 * The loader is [ShapeLoader] in both cases. Below [FileStore.PROGRESS_FLOOR_BYTES] the
 * read finishes within a frame or two, so it appears alone. At or above it the read is
 * long enough to be worth quantifying, and the name, size and percentage are added under
 * it. The percentage comes from bytes consumed off the stream, not from elapsed time.
 *
 * The value is animated between updates because progress arrives in whole-percent steps
 * at a non-uniform rate, which without smoothing reads as stalling and jumping.
 */
@Composable
fun LoadingFile(name: String, sizeBytes: Long, progress: Float, modifier: Modifier = Modifier) {
    val measured = sizeBytes >= FileStore.PROGRESS_FLOOR_BYTES

    Box(modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShapeLoader(size = 30.dp)

            if (!measured) return@Column

            val shown by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = Motion.standard(),
                label = "read-progress",
            )

            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(220.dp),
            )

            Text(
                "${formatSize(sizeBytes)}   ${(shown * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = TextMid,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
