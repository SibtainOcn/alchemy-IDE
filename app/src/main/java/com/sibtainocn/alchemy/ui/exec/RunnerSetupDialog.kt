package com.sibtainocn.alchemy.ui.exec

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sibtainocn.alchemy.exec.SetupStep
import com.sibtainocn.alchemy.exec.StepAction
import com.sibtainocn.alchemy.exec.StepStatus
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.common.rememberCopyToClipboard
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.Danger
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.Ink
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import kotlinx.coroutines.delay

/**
 * Getting the runner installed, permitted, and willing to take orders.
 *
 * A checklist rather than a status line. Every row is verified on its own and shows its
 * own answer, because the single ready flag this replaced was measured by one handshake:
 * it went green the moment the runner answered, while the two instructions underneath it
 * were still undone, and then every run failed on a file that plainly existed.
 *
 * Rows are checked top to bottom and the run stops at the first failure, since the rows
 * are a chain. Everything below a failure is drawn as waiting rather than as broken, so
 * nobody is sent to fix a step that was never the problem.
 *
 * Nothing here names the runner. Every string comes from the provider's guide.
 */
@Composable
fun RunnerSetupDialog(
    vm: SetupViewModel,
    onDismiss: () -> Unit,
) {
    val guide = vm.guide ?: return
    val context = LocalContext.current

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.recheck() }

    LaunchedEffect(Unit) {
        vm.markOffered()
        vm.recheck()
    }

    val done = vm.allStepsDone

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(InkRaised)
                .border(1.dp, Hairline, RoundedCornerShape(Radii.lg)),
        ) {
            Header(
                runner = guide.runnerName,
                total = vm.steps.size,
                complete = vm.stepsDone,
                done = done,
            )

            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                vm.steps.forEachIndexed { index, step ->
                    StepRow(
                        number = index + 1,
                        step = step,
                        status = vm.stepStates[step.id] ?: StepStatus.Pending,
                        last = index == vm.steps.lastIndex,
                        runner = guide.runnerName,
                        note = if (step.action is StepAction.Download) guide.downloadNote else null,
                        onAction = {
                            when (step.action) {
                                StepAction.Download -> runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(guide.downloadUrl))
                                    )
                                }
                                StepAction.GrantPermission ->
                                    vm.requiredPermission?.let(permissionRequest::launch)
                                StepAction.OpenRunner -> vm.launchIntent()?.let {
                                    runCatching { context.startActivity(it) }
                                }
                                StepAction.None -> Unit
                            }
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Footer(
                runner = guide.runnerName,
                checking = vm.checking,
                done = done,
                canOpenRunner = vm.launchIntent() != null,
                onRecheck = { vm.recheck() },
                onOpenRunner = { vm.launchIntent()?.let { runCatching { context.startActivity(it) } } },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun Header(runner: String, total: Int, complete: Int, done: Boolean) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp)) {
        Text(
            if (done) "$runner is ready" else "Set up $runner",
            style = MaterialTheme.typography.headlineSmall,
            color = TextHigh,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (done) {
                "Everything checked out. You can run files now."
            } else {
                "Alchemy has no shell of its own, so it borrows one. $complete of $total done."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
        )
        Spacer(Modifier.height(14.dp))

        // One segment per step. A bar reads as elapsed time, which this is not: it is a
        // count of things that are true.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { index ->
                val filled = index < complete
                val colour by animateColorAsState(
                    if (filled) MaterialTheme.colorScheme.primary else Hairline,
                    Motion.standard(),
                    label = "segment",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colour)
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    step: SetupStep,
    status: StepStatus,
    last: Boolean,
    runner: String,
    note: String?,
    onAction: () -> Unit,
) {
    val done = status == StepStatus.Done
    val failed = status == StepStatus.Failed

    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
            StatusDot(number, status)
            if (!last) {
                Spacer(
                    Modifier
                        .width(1.dp)
                        .weight(1f)
                        .heightIn(min = 12.dp)
                        .background(Hairline)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f).padding(bottom = if (last) 6.dp else 20.dp)) {
            Text(
                step.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) TextMid else TextHigh,
                fontWeight = FontWeight.SemiBold,
            )

            // A finished row keeps its title and drops everything else. The instructions
            // are only worth space while they are still instructions.
            AnimatedVisibility(!done) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (failed) step.onFailure ?: step.why else step.why,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) Danger else TextMid,
                        lineHeight = 19.sp,
                    )
                    note?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextLow, lineHeight = 17.sp)
                    }

                    step.command?.let {
                        Spacer(Modifier.height(10.dp))
                        CommandBlock(it, runner)
                    }

                    if (step.action !is StepAction.None) {
                        Spacer(Modifier.height(10.dp))
                        ActionButton(
                            label = when (step.action) {
                                StepAction.Download -> "Get $runner"
                                StepAction.GrantPermission -> "Grant permission"
                                StepAction.OpenRunner -> "Open $runner"
                                StepAction.None -> ""
                            },
                            onClick = onAction,
                        )
                    }
                }
            }
        }
    }
}

/** The number, the spinner, the tick or the cross, in the one place the eye looks. */
@Composable
private fun StatusDot(number: Int, status: StepStatus) {
    val ring = when (status) {
        StepStatus.Done -> MaterialTheme.colorScheme.primary
        StepStatus.Failed -> Danger
        else -> Hairline
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (status == StepStatus.Done) MaterialTheme.colorScheme.primary else Ink)
            .border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            StepStatus.Checking -> ShapeLoader(size = 12.dp)
            StepStatus.Done -> Icon(
                Ico.Check,
                null,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            StepStatus.Failed -> Text(
                "!",
                style = MaterialTheme.typography.labelLarge,
                color = Danger,
                fontWeight = FontWeight.Bold,
            )
            else -> Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = TextLow,
            )
        }
    }
}

/**
 * A command, at a size it can be read at, with its own copy button.
 *
 * Scrolls sideways rather than wrapping. These lines are long and a wrapped shell command
 * is hard to check against what was actually pasted, which is the one thing somebody
 * looking at this screen is trying to do.
 */
@Composable
private fun CommandBlock(command: String, runner: String) {
    val copy = rememberCopyToClipboard()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .background(Ink)
            .border(1.dp, Hairline, RoundedCornerShape(Radii.sm)),
    ) {
        Text(
            command,
            fontFamily = CodeFont,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = TextHigh,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Paste into $runner",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            CopyPill(copied) {
                copy(command)
                copied = true
            }
        }
    }
}

@Composable
private fun CopyPill(copied: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (copied) MaterialTheme.colorScheme.primary else TextMid,
        Motion.snappy(),
        label = "copy",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(InkRaised)
            .border(1.dp, Hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (copied) Ico.Check else Ico.Copy, null, Modifier.size(14.dp), tint = tint)
        Spacer(Modifier.width(7.dp))
        Text(
            if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The way out, which is a different way out depending on what the checklist found.
 *
 * When everything passes there is one button and it says Done. Offering "Check again",
 * "Open runner" and "Not now" to somebody who has just been told they are ready is three
 * ways to ask a question that has been answered.
 */
@Composable
private fun Footer(
    runner: String,
    checking: Boolean,
    done: Boolean,
    canOpenRunner: Boolean,
    onRecheck: () -> Unit,
    onOpenRunner: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (done) {
                ActionButton("Done", onClick = onDismiss)
                return@Row
            }

            FooterText("Not now", onClick = onDismiss)
            if (canOpenRunner) {
                Spacer(Modifier.width(4.dp))
                FooterText("Open $runner", onClick = onOpenRunner)
            }
            Spacer(Modifier.width(8.dp))

            val alpha by animateFloatAsState(if (checking) 0.6f else 1f, label = "recheck")
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    .clickable(enabled = !checking, onClick = onRecheck)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checking) {
                    ShapeLoader(size = 13.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (checking) "Checking" else "Check again",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FooterText(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextMid)
    }
}
