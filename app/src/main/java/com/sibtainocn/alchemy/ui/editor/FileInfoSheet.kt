package com.sibtainocn.alchemy.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import java.io.File

/**
 * Everything about the open file, reachable by tapping its name.
 *
 * A sheet rather than a dialog for one reason: the whole thing is selectable. A path is
 * the piece of information people most often need out of an editor and least often have
 * anywhere to read, and it is no use at all if it cannot be picked up and taken
 * somewhere. The name is truncated in the bar out of necessity; here it is not truncated
 * at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInfoSheet(
    file: File,
    language: String,
    lines: Int,
    characters: Int,
    onCopyPath: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHigh,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$language  ·  $lines lines",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )

                Spacer(Modifier.height(18.dp))
                Text("Path", style = MaterialTheme.typography.labelMedium, color = TextMid)
                Spacer(Modifier.height(6.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(InkHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        file.absolutePath,
                        fontFamily = CodeFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = TextMid,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    // The copy button stays, even though the text can be selected. One is
                    // for taking the whole path in a tap, the other for taking part of it.
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(onClick = onCopyPath),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Ico.Copy,
                            "Copy path",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))
                Spacer(Modifier.height(6.dp))

                InfoRow("Folder", file.parent ?: "-")
                InfoRow("Size", Fmt.size(file.length()))
                InfoRow("Modified", Fmt.date(file.lastModified()))
                InfoRow("Type", language)
                InfoRow("Lines", lines.toString())
                InfoRow("Characters", characters.toString())
                InfoRow("Writable", if (file.canWrite()) "Yes" else "No")

                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            modifier = Modifier.width(104.dp),
        )
        Text(
            value,
            fontFamily = CodeFont,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = TextHigh,
            modifier = Modifier.weight(1f),
        )
    }
}
