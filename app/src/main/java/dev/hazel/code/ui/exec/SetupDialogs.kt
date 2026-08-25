package dev.hazel.code.ui.exec

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
import dev.hazel.code.exec.InstallPlanner
import dev.hazel.code.exec.InstallState
import dev.hazel.code.exec.Readiness
import dev.hazel.code.exec.Runtime
import dev.hazel.code.exec.SetupStep
import dev.hazel.code.ui.common.Ico
import dev.hazel.code.ui.common.ShapeLoader
import dev.hazel.code.ui.common.rememberCopyToClipboard
import dev.hazel.code.ui.theme.CodeFont
import dev.hazel.code.ui.theme.Hairline
import dev.hazel.code.ui.theme.InkHigh
import dev.hazel.code.ui.theme.InkRaised
import dev.hazel.code.ui.theme.Radii
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextLow
import dev.hazel.code.ui.theme.TextMid

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
 * Step one: get the runner installed, permitted, and willing to take orders.
 *
 * Shows the rung the user is on, then the commands to paste. The commands stay visible at
 * every rung on purpose: someone who has already done them and is being told something is
 * still wrong needs to see what they ran, not a screen that hides it.
 */
@Composable
fun RunnerSetupDialog(
    vm: SetupViewModel,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val guide = vm.guide ?: return
    val context = LocalContext.current
    val copy = rememberCopyToClipboard()
    val readiness = vm.readiness

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refresh() }

    LaunchedEffect(Unit) {
        vm.markOffered()
        vm.refresh()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.lg),
        title = { Text("Set up ${guide.runnerName}", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                StatusLine(readiness, vm.checking, guide.runnerName)

                if (readiness is Readiness.RunnerMissing || readiness is Readiness.RunnerFromAppStore) {
                    guide.downloadNote?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid)
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionChip("Get ${guide.runnerName}") {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(guide.downloadUrl))
                            )
                        }
                    }
                }

                if (readiness is Readiness.PermissionMissing) {
                    Spacer(Modifier.height(12.dp))
                    vm.requiredPermission?.let { permission ->
                        ActionChip("Grant permission") { permissionRequest.launch(permission) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Run these once in ${guide.runnerName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextHigh,
                )
                Text(
                    "Copy each line, paste it into ${guide.runnerName}, press enter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow,
                )

                guide.steps.forEachIndexed { index, step ->
                    Spacer(Modifier.height(14.dp))
                    StepCard(index + 1, step) { copy(step.command) }
                }
            }
        },
        confirmButton = {
            if (readiness is Readiness.Ready) {
                TextButton(onClick = onContinue) {
                    Text("Choose languages", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = { vm.refresh() }, enabled = !vm.checking) {
                    Text("Check again", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            Row {
                vm.launchIntent()?.let { intent ->
                    TextButton(onClick = { runCatching { context.startActivity(intent) } }) {
                        Text("Open ${guide.runnerName}", color = TextMid)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Not now", color = TextMid) }
            }
        },
    )
}

/** Where the user is on the ladder, in one sentence they can act on. */
@Composable
private fun StatusLine(readiness: Readiness?, checking: Boolean, runner: String) {
    val (text, good) = when {
        checking || readiness == null -> "Checking..." to false
        readiness is Readiness.Ready -> "$runner is ready." to true
        readiness is Readiness.RunnerMissing -> "$runner is not installed yet." to false
        readiness is Readiness.RunnerFromAppStore ->
            "This $runner came from Google Play. That build cannot take commands from " +
                "other apps and cannot be updated into one that can, so it has to be " +
                "replaced." to false
        readiness is Readiness.RunnerTooOld ->
            "This $runner is too old for the interface this uses. Update it." to false
        readiness is Readiness.PermissionMissing ->
            "This app needs your permission to talk to $runner." to false
        readiness is Readiness.ExternalAppsDisabled ->
            "$runner is refusing commands from other apps. Run the first command below." to false
        readiness is Readiness.Unsupported -> "This build cannot run code." to false
        else -> "Something is still missing." to false
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (checking) {
            ShapeLoader(size = 14.dp)
            Spacer(Modifier.width(10.dp))
        } else if (good) {
            Icon(Ico.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (good) MaterialTheme.colorScheme.primary else TextMid,
        )
    }
}

@Composable
private fun StepCard(number: Int, step: SetupStep, onCopy: () -> Unit) {
    Column {
        Text(
            "$number. ${step.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextHigh,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(step.why, style = MaterialTheme.typography.bodySmall, color = TextLow)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.sm))
                .background(InkHigh)
                .clickable(onClick = onCopy)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                step.command,
                fontFamily = CodeFont,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = TextMid,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Icon(Ico.Copy, "Copy", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

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
) {
    var selection by remember { mutableStateOf(emptySet<Runtime>()) }
    val plan = vm.plan(selection)
    val states = vm.installStates

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

                if (plan.isNotEmpty() && !vm.installing) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "About ${InstallPlanner.totalMb(plan)} MB, smallest first.",
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
            TextButton(onClick = onDismiss, enabled = !vm.installing) {
                Text(if (states.isEmpty()) "Skip" else "Done", color = TextMid)
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
