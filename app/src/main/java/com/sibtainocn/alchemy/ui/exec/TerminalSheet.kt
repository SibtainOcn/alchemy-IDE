package com.sibtainocn.alchemy.ui.exec

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.exec.ConsoleLine
import java.io.File
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.common.rememberCopyToClipboard
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TerminalFont
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.LocalAccents
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * The terminal, as a sheet over the editor.
 *
 * A console rather than a terminal emulator, and the difference is worth being honest
 * about: each command is its own process, so there is no session to be interactive with.
 * A program that asks a question will wait for an answer that cannot arrive. What this
 * does do is run a line, show everything that line printed, and remember where you are.
 *
 * The prompt reads `~ $` whatever directory is current. The full path is announced when it
 * changes and then stays out of the way, because forty characters of storage path in front
 * of every line is not a prompt, it is a margin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalSheet(vm: TerminalViewModel, onOpenFile: (File) -> Unit = {}) {
    if (!vm.open) return

    // Opens at half and drags to full. It went straight to full height because the
    // prompt was a bar pinned under the transcript, which a half sheet pushed below the
    // fold. The prompt is the last line of the transcript now, so it arrives with the
    // output, and a half sheet leaves the file underneath it in view.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardUp = WindowInsets.isImeVisible

    /**
     * Closes the keyboard, then the sheet.
     *
     * In that order and never together: a sheet dismissed out from under an open keyboard
     * leaves the keyboard standing over the editor with nothing to type into, and the next
     * tap goes wherever it lands underneath.
     */
    fun dismiss() {
        focusManager.clearFocus(force = true)
        vm.close()
    }

    // Back is one gesture at a time. With the keyboard up it puts the keyboard away and
    // leaves the transcript on screen, which is what the same press does in every other
    // app on the device; the press after that closes the sheet.
    BackHandler(enabled = keyboardUp) { focusManager.clearFocus(force = true) }

    // True while the end of the transcript is already in view. Somebody who has scrolled
    // up to read a traceback is not asking to be pulled back down by the next line.
    val following by remember { derivedStateOf { !listState.canScrollForward } }

    // Opening lands on the end of whatever is already there rather than at the top of it.
    LaunchedEffect(Unit) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) runCatching { listState.scrollToItem(last) }
    }

    // Follow the output down as it arrives. A jump rather than an animation, and anchored
    // to the line count rather than to the content: an animated scroll restarted by every
    // line of a burst spends the whole burst chasing itself, which is what made the sheet
    // appear to shiver while a command was printing.
    LaunchedEffect(vm.lines.size) {
        if (!following) return@LaunchedEffect
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) runCatching { listState.scrollToItem(last) }
    }

    ModalBottomSheet(
        // Whatever asked for the sheet to go - back, the scrim, a drag - the keyboard goes
        // first. This is the backstop for the back press as well, since a sheet lives in a
        // window of its own and cannot be relied on to hand the press to a BackHandler.
        onDismissRequest = { if (keyboardUp) focusManager.clearFocus(force = true) else vm.close() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        // A handle, because the sheet is resizable now and nothing else says so.
        dragHandle = { SheetGrip() },
        // The sheet keeps its own hands off the insets so the content can put the prompt
        // exactly on top of the keyboard rather than behind it.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .imePadding()
        ) {
            Header(
                vm = vm,
                onRecall = { recalled -> input = recalled },
                onOpenFile = onOpenFile,
                onClose = { dismiss() },
            )
            Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))

            // Kept with the indices of the unfiltered list, so a row's key survives a
            // timing being switched off and the scrollback being trimmed alike.
            val visible = remember(vm.lines, vm.showTimings) {
                vm.lines.withIndex().filter { vm.showTimings || it.value !is ConsoleLine.Timing }
            }

            LazyColumn(
                state = listState,
                // Only as tall as it needs to be. With a short transcript the prompt sits
                // directly under the last line, where a terminal's next line goes; with a
                // long one this fills what is left and the prompt rests on the keyboard.
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 10.dp,
                ),
            ) {
                // Selection is per row rather than around the whole list, because a
                // selection container cannot hold a text field and the prompt sits with
                // these rows. The first thing anyone does with an error they do not
                // understand is copy it somewhere that might, so the output rows keep it.
                items(visible.size, key = { vm.firstLineId + visible[it].index }) { index ->
                    SelectionContainer { ConsoleRow(visible[index].value, vm.fontSizeSp) }
                }
            }

            // The live line, directly under the transcript rather than inside it.
            //
            // It used to be the last item of the list, which meant the list could scroll
            // it out of existence: an item that leaves the viewport is disposed, and a
            // disposed text field takes the focus and the keyboard with it, then asked for
            // both back the moment it returned. That was the flicker. Out here it is
            // composed for as long as the sheet is open, so output lands behind a keyboard
            // that never went anywhere.
            Prompt(
                value = input,
                running = vm.running,
                sizeSp = vm.fontSizeSp,
                focusRequester = focus,
                onValue = { input = it },
                onSubmit = {
                    vm.submit(input)
                    input = ""
                },
                onStop = { vm.cancel() },
            )

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Header(
    vm: TerminalViewModel,
    onRecall: (String) -> Unit,
    onOpenFile: (File) -> Unit,
    onClose: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val copy = rememberCopyToClipboard()

    Row(
        Modifier
            .fillMaxWidth()
            .background(InkRaised)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ico.Terminal, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text("Terminal", style = MaterialTheme.typography.titleSmall, color = TextHigh)

        Spacer(Modifier.weight(1f))

        if (vm.running) {
            ShapeLoader(size = 16.dp)
            Spacer(Modifier.width(12.dp))
        }

        // Writes the command into the prompt rather than running it, so it can be edited
        // first. Tapping again walks further back, the way a shell's up arrow does.
        if (vm.hasHistory) {
            SmallAction(Ico.HistoryUp, "Previous command") {
                vm.recallPrevious()?.let(onRecall)
            }
        }

        SmallAction(Ico.Trash, "Clear") { vm.clear() }

        Box {
            SmallAction(Ico.More, "Options") { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Radii.md),
                modifier = Modifier.width(258.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Text size",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextHigh,
                        modifier = Modifier.weight(1f),
                    )
                    SmallAction(Ico.Minus, "Smaller") { vm.setFontSize(vm.fontSizeSp - 1) }
                    Text(
                        "${vm.fontSizeSp}",
                        fontFamily = TerminalFont,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(28.dp),
                    )
                    SmallAction(Ico.Plus, "Bigger") { vm.setFontSize(vm.fontSizeSp + 1) }
                }

                DropdownMenuItem(
                    onClick = { vm.toggleTimings() },
                    text = {
                        Text(
                            "Show timings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHigh,
                        )
                    },
                    trailingIcon = {
                        Text(
                            if (vm.showTimings) "On" else "Off",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (vm.showTimings) MaterialTheme.colorScheme.primary
                            else TextLow,
                        )
                    },
                )

                DropdownMenuItem(
                    onClick = { menuOpen = false; copy(vm.transcript()) },
                    text = {
                        Text(
                            "Copy everything",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHigh,
                        )
                    },
                )

                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        val history = vm.historyFile()
                        onClose()
                        onOpenFile(history)
                    },
                    text = {
                        Text(
                            "Command history",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHigh,
                        )
                    },
                )

                DropdownMenuItem(
                    onClick = { menuOpen = false; vm.clearHistory() },
                    text = {
                        Text(
                            "Clear history",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHigh,
                        )
                    },
                )
            }
        }

        SmallAction(Ico.Close, "Close", onClick = onClose)
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(17.dp), tint = TextMid)
    }
}

/** One entry of scrollback. Everything is monospace; only the colour separates the kinds. */
@Composable
private fun ConsoleRow(line: ConsoleLine, sizeSp: Int) {
    val accents = LocalAccents.current
    val body = sizeSp.sp
    // Terminal output is read line by line rather than in blocks, so it wants more air
    // between rows than code does.
    val bodyLine = (sizeSp * 1.55f).sp
    // Notes and timings are the app talking rather than the program, so they stay a step
    // smaller than the output whatever size the output is set to.
    val aside = (sizeSp - 1).coerceAtLeast(8).sp

    when (line) {
        is ConsoleLine.Typed -> Row(Modifier.padding(top = 6.dp)) {
            Text(
                PROMPT,
                fontFamily = TerminalFont,
                fontSize = body,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                line.command,
                fontFamily = TerminalFont,
                fontSize = body,
                lineHeight = bodyLine,
                color = TextHigh,
            )
        }

        // What the program printed, which is the reason the terminal is open. It was a
        // step down from the command that produced it, so the output of every successful
        // run read as less important than the echo of what was typed.
        is ConsoleLine.Output -> Text(
            line.text,
            fontFamily = TerminalFont,
            fontSize = body,
            lineHeight = bodyLine,
            color = TextHigh,
        )

        is ConsoleLine.Error -> Text(
            line.text,
            fontFamily = TerminalFont,
            fontSize = body,
            lineHeight = bodyLine,
            color = MaterialTheme.colorScheme.error,
        )

        is ConsoleLine.Note -> Text(
            line.text,
            fontFamily = TerminalFont,
            fontSize = aside,
            lineHeight = bodyLine,
            color = accents.comment,
            modifier = Modifier.padding(bottom = 2.dp),
        )

        is ConsoleLine.Timing -> Text(
            line.text,
            fontFamily = TerminalFont,
            fontSize = aside,
            lineHeight = bodyLine,
            color = accents.comment,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/**
 * The live line: a prompt, a caret, and whatever is being typed.
 *
 * One more row of the console, at the console's own size, with no background of its own.
 * It sits where the next line of output will go, which is where a terminal's input belongs
 * and where the eye is already looking.
 */
@Composable
private fun Prompt(
    value: String,
    running: Boolean,
    sizeSp: Int,
    focusRequester: FocusRequester,
    onValue: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    val body = sizeSp.sp

    // Typing is the point of the sheet, so the caret starts here - once, when the sheet
    // opens. Asking again on every recomposition is what turns a keyboard into a strobe.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            PROMPT,
            fontFamily = TerminalFont,
            fontSize = body,
            color = MaterialTheme.colorScheme.primary,
        )

        BasicTextField(
            value = value,
            onValueChange = onValue,
            // Never disabled while a command runs. Taking `enabled` away from a field that
            // has focus takes the focus with it, which closes the keyboard, and giving it
            // back a second later opens the keyboard again: the terminal was borrowing the
            // keyboard for exactly as long as each command took. Typing the next line
            // while one is still running is what a terminal is for anyway, and the view
            // model holds that line until the runner is free.
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TerminalFont,
                fontSize = body,
                color = TextHigh,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                // A shell is case sensitive and full of punctuation; autocorrect here is
                // an active hindrance.
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { field ->
                if (value.isEmpty()) {
                    Text(
                        if (running) "running..." else "run a command",
                        fontFamily = TerminalFont,
                        fontSize = body,
                        color = TextLow.copy(alpha = 0.55f),
                    )
                }
                field()
            },
        )

        when {
            // Stopping only stops the waiting; the command carries on inside the runner,
            // which the note it leaves behind says out loud.
            running -> SmallAction(Ico.Close, "Stop waiting", onClick = onStop)
            value.isNotBlank() -> SmallAction(Ico.Play, "Run", onClick = onSubmit)
        }
    }
}

/**
 * Deliberately not the working directory.
 *
 * The path is said once when it changes, in the scrollback, and the prompt stays short.
 */
/** A short bar saying the sheet can be dragged, drawn on the sheet's own background. */
@Composable
private fun SheetGrip() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(34.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Hairline)
        )
    }
}

private const val PROMPT = "~ $ "
