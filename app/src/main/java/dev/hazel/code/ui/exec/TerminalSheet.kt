package dev.hazel.code.ui.exec

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hazel.code.exec.ConsoleLine
import dev.hazel.code.ui.common.Ico
import dev.hazel.code.ui.common.ShapeLoader
import dev.hazel.code.ui.theme.CodeFont
import dev.hazel.code.ui.theme.Hairline
import dev.hazel.code.ui.theme.InkRaised
import dev.hazel.code.ui.theme.LocalAccents
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextLow
import dev.hazel.code.ui.theme.TextMid

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSheet(vm: TerminalViewModel) {
    if (!vm.open) return

    // Straight to full height. A half sheet put the prompt below the fold, which is the
    // one part of a terminal that has to be reachable the moment it opens.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val accents = LocalAccents.current

    // Follow the output down. Anchored to the count rather than to the content so a long
    // burst scrolls once instead of per line.
    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.lastIndex)
    }

    ModalBottomSheet(
        onDismissRequest = { vm.close() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        // The sheet keeps its own hands off the insets so the content can put the prompt
        // exactly on top of the keyboard rather than behind it.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.94f)
                .imePadding()
        ) {
            Header(vm)
            Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))

            // Selectable, because the first thing anyone does with an error they do not
            // understand is copy it somewhere that might.
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp,
                    ),
                ) {
                    items(vm.lines.size) { index -> ConsoleRow(vm.lines[index]) }
                }
            }

            Box(Modifier.fillMaxWidth().height(0.7.dp).background(Hairline))
            Prompt(
                value = input,
                running = vm.running,
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
private fun Header(vm: TerminalViewModel) {
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

        SmallAction(Ico.Trash, "Clear") { vm.clear() }
        SmallAction(Ico.Close, "Close") { vm.close() }
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
private fun ConsoleRow(line: ConsoleLine) {
    val accents = LocalAccents.current
    when (line) {
        is ConsoleLine.Typed -> Row(Modifier.padding(top = 6.dp)) {
            Text(
                PROMPT,
                fontFamily = CodeFont,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                line.command,
                fontFamily = CodeFont,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = TextHigh,
            )
        }

        is ConsoleLine.Output -> Text(
            line.text,
            fontFamily = CodeFont,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = TextMid,
        )

        is ConsoleLine.Error -> Text(
            line.text,
            fontFamily = CodeFont,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.error,
        )

        is ConsoleLine.Note -> Text(
            line.text,
            fontFamily = CodeFont,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = accents.comment,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/** The live line: a prompt, a caret, and whatever is being typed. */
@Composable
private fun Prompt(
    value: String,
    running: Boolean,
    onValue: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(InkRaised)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            PROMPT,
            fontFamily = CodeFont,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
        )

        BasicTextField(
            value = value,
            onValueChange = onValue,
            enabled = !running,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = CodeFont,
                fontSize = 13.sp,
                color = if (running) TextLow else TextHigh,
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
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                if (value.isEmpty()) {
                    Text(
                        if (running) "running..." else "run a command",
                        fontFamily = CodeFont,
                        fontSize = 13.sp,
                        color = TextLow.copy(alpha = 0.6f),
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
private const val PROMPT = "~ $ "
