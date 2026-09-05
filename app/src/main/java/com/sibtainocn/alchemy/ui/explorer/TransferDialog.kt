package com.sibtainocn.alchemy.ui.explorer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * What a running transfer is doing, in front of the folder rather than above it.
 *
 * It used to be two lines in the strip at the top of the screen, which is where a
 * clipboard belongs and not where a job in flight does: the one number a person watching a
 * copy actually wants is how much longer it will take, and a two-pixel bar under a
 * filename cannot answer that. This says what is being written, how far along it is, and
 * how fast, which between them is the answer.
 *
 * Two ways out, and they are not the same thing. Cancel stops the work; Hide leaves it
 * running and gets out of the way, because a ten-minute copy that owns the screen for ten
 * minutes is a copy nobody starts twice. The corner button keeps turning while it runs and
 * brings this back.
 */
@Composable
fun TransferDialog(job: TransferJob, onCancel: () -> Unit, onHide: () -> Unit) {
    if (job.hidden) return

    Dialog(
        // Neither the scrim nor back dismisses it: both of those would have to mean either
        // Cancel or Hide, and guessing which one on someone's behalf is how work gets
        // thrown away by accident. Hide is one tap and says what it does.
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(start = 22.dp, end = 14.dp, top = 20.dp, bottom = 10.dp)) {
                Text(
                    job.verb + if (job.count > 1) " ${job.count} items" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHigh,
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        // Two lines of a long filename, then an ellipsis: enough to
                        // recognise what is stuck without the dialog changing height as
                        // the names go by.
                        job.name ?: "Working out the size",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            percentOf(job),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextHigh,
                        )
                        // Nothing rather than a zero while the average settles: a speed
                        // that reads 0B/s for the first second of every copy is worse than
                        // no speed at all.
                        job.speed?.takeIf { it > 0 }?.let { rate ->
                            Text(
                                Fmt.size(rate) + "/s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                ProgressTrack(job.fraction)
                Spacer(Modifier.height(4.dp))

                Text(
                    detailOf(job),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onHide) {
                        Text("Hide", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * The bar, determinate when there is something to be determinate about.
 *
 * A measured total is not always there: a move that renames carries no bytes, and the
 * first moments of a batch are spent walking the tree. An indeterminate sweep says that
 * honestly, where a bar sitting at zero would read as stuck.
 */
@Composable
private fun ProgressTrack(fraction: Float?) {
    val track = Hairline
    val fill = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track)
    ) {
        if (fraction == null) {
            // Half width, parked at the start: the shimmer a real indeterminate bar needs
            // would be a second animation running through a file copy for no information.
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fill.copy(alpha = 0.45f))
            )
        } else {
            val width by animateFloatAsState(fraction, Motion.standard(), label = "transfer")
            Box(
                Modifier
                    .fillMaxWidth(width.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fill)
            )
        }
    }
}

private fun percentOf(job: TransferJob): String {
    val fraction = job.fraction ?: return "--"
    return "${(fraction * 100).toInt()}%"
}

/** The line under the bar: bytes for a transfer, items for a delete. */
private fun detailOf(job: TransferJob): String = when {
    job.kind == TransferJob.Kind.DELETE -> "${job.done} of ${job.total}"
    job.total <= 0L -> "Measuring"
    else -> Fmt.size(job.done) + " of " + Fmt.size(job.total)
}
