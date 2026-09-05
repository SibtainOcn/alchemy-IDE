package com.sibtainocn.alchemy.ui.exec

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.exec.InstallPlanner
import com.sibtainocn.alchemy.exec.InstallState
import com.sibtainocn.alchemy.exec.Readiness
import com.sibtainocn.alchemy.exec.Runtime
import com.sibtainocn.alchemy.exec.SetupStep
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.common.rememberCopyToClipboard
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * The two dialogs that stand between installing the app and running a file.
 *
 * Neither of them names Termux. Everything specific to the runner is asked of the
 * provider, so a build with a different one, or with none, shows the right thing without
 * this file knowing anything about it.
 *
 * The setup is deliberately two dialogs rather than one long list. The first is mandatory
 * and cannot be skipped, because without it this app cannot say anything to the runner at
 * all. The second is a choice about which languages to download, and every part of it can
 * be declined.
 */

/**
 * Step two: which languages to download.
 *
 * Every part of this is optional. Nothing is preselected, the whole dialog can be skipped,
 * and a failure on one language leaves the others alone and says what went wrong.
 */
@Composable
fun RuntimePickerDialog(
    vm: SetupViewModel,
    onDismiss: () -> Unit,
    onShowLogs: () -> Unit,
) {
    var selection by remember { mutableStateOf(emptySet<Runtime>()) }
    val plan = vm.plan(selection)
    val states = vm.installStates

    // Ask what is already there every time this opens. Something installed by hand in the
    // meantime should show as installed, not be offered as a quarter-gigabyte download.
    LaunchedEffect(Unit) { vm.refresh() }

    AlertDialog(
        onDismissRequest = { if (!vm.installing) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.lg),
        title = { Text("Install languages", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Pick what you want to run. Each one is downloaded inside the " +
                        "terminal app, so it costs data once and stays there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                )
                Spacer(Modifier.height(14.dp))

                Runtime.entries.forEach { runtime ->
                    RuntimeRow(
                        runtime = runtime,
                        state = states[runtime],
                        checked = runtime in selection,
                        enabled = !vm.installing && runtime !in vm.present,
                    ) { checked ->
                        selection = if (checked) selection + runtime else selection - runtime
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(10.dp))
                when {
                    vm.checking -> ProgressLine("Checking what is already installed...")

                    vm.installing -> ProgressLine(
                        buildString {
                            append("Installing ${vm.finished + 1} of ${vm.queue.size}")
                            vm.current?.let { append(": ${it.label}, ${it.downloadSize}") }
                        }
                    )

                    plan.isNotEmpty() -> Text(
                        "About ${InstallPlanner.totalMb(plan)} MB, smallest first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                    )
                }

                if (vm.installing) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This keeps going if you close the dialog. Come back here to see " +
                            "how it went.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))
                Spacer(Modifier.height(10.dp))
                Text(
                    "You can install these later from the editor menu, or by hand with " +
                        "the command shown beside each one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.install(selection) },
                enabled = plan.isNotEmpty() && !vm.installing,
            ) {
                Text(
                    if (vm.installing) "Installing..." else "Install",
                    color = if (plan.isEmpty() || vm.installing) TextLow
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            // Whatever the package manager printed, in the terminal, unedited. An install
            // that fails says why somewhere in a few hundred lines of apt, and the dialog
            // has room for one of them.
            if (vm.log.isNotEmpty()) {
                TextButton(onClick = onShowLogs) { Text("Logs", color = TextMid) }
            }
            // Always live, including mid-install. The download belongs to the view model
            // rather than to this dialog, so closing it is leaving the room, not pulling
            // the plug.
            TextButton(onClick = onDismiss) {
                Text(
                    when {
                        vm.installing -> "Close"
                        states.isEmpty() -> "Skip"
                        else -> "Done"
                    },
                    color = TextMid,
                )
            }
        },
    )
}

@Composable
private fun RuntimeRow(
    runtime: Runtime,
    state: InstallState?,
    checked: Boolean,
    enabled: Boolean,
    onCheck: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .background(InkRaised)
            .clickable(enabled = enabled) { onCheck(!checked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            InstallState.Installing, InstallState.Waiting -> Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) { ShapeLoader(size = 16.dp) }

            InstallState.Installed, InstallState.AlreadyInstalled -> Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Ico.Check,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            else -> Checkbox(
                checked = checked,
                onCheckedChange = onCheck,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = TextLow,
                ),
            )
        }

        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(runtime.label, style = MaterialTheme.typography.bodyMedium, color = TextHigh)
                Spacer(Modifier.width(8.dp))
                Text(
                    runtime.downloadSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextLow,
                )
            }
            Text(
                detailFor(runtime, state),
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    is InstallState.Failed, InstallState.InstalledButMissing ->
                        MaterialTheme.colorScheme.error
                    InstallState.Installed, InstallState.AlreadyInstalled ->
                        MaterialTheme.colorScheme.primary
                    else -> TextLow
                },
                fontFamily = if (state == null) CodeFont else null,
                fontSize = if (state == null) 11.sp else 12.sp,
            )
        }
    }
}

/**
 * The second line of a row: the install command while nothing is happening, and what
 * happened once something has.
 */
private fun detailFor(runtime: Runtime, state: InstallState?): String = when (state) {
    null, InstallState.Waiting -> runtime.installCommand
    InstallState.Installing -> "Downloading and installing..."
    InstallState.Installed -> "Installed"
    InstallState.AlreadyInstalled -> "Already installed"
    InstallState.InstalledButMissing ->
        "Installed, but ${runtime.probe} is still not found. Try running the command by hand."
    is InstallState.Failed -> state.reason
}

/** One line of live progress, with something moving beside it. */
@Composable
private fun ProgressLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapeLoader(size = 13.dp)
        Spacer(Modifier.width(9.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextMid)
    }
}

/** A small filled button, for the one action a dialog section is about. */
@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
