package com.sibtainocn.alchemy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * What the app says about the crash it came back from.
 *
 * Shown once, on the launch after a failure, and then forgotten. The trace is here rather
 * than hidden behind a "details" link because the only person who can act on it is the
 * one who can also copy it into an issue, and a report nobody can reach is a report
 * nobody sends.
 *
 * It does not apologise twice or offer to "try again": the thing that failed already
 * happened, and the file it happened on is still on disk.
 */
@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val copy = rememberCopyToClipboard()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.lg),
        title = {
            Text(
                "Alchemy closed unexpectedly",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    "Nothing on disk was changed by this. If you were editing something " +
                        "unsaved, that edit is gone - the rest of the file is as it was.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
                Text(
                    report,
                    fontFamily = CodeFont,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = TextHigh,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(InkHigh)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        },
        dismissButton = {
            TextButton(onClick = { copy(report) }) { Text("Copy report") }
        },
    )
}
