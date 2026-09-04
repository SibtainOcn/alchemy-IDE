package com.sibtainocn.alchemy.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.common.ConfirmDialog
import com.sibtainocn.alchemy.ui.common.EmptyState
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.common.rememberCopyToClipboard
import com.sibtainocn.alchemy.ui.common.rememberPasteFromClipboard
import com.sibtainocn.alchemy.exec.Readiness
import com.sibtainocn.alchemy.exec.Runtime
import com.sibtainocn.alchemy.ui.exec.RunnerSetupDialog
import com.sibtainocn.alchemy.ui.exec.RuntimePickerDialog
import com.sibtainocn.alchemy.ui.exec.SetupViewModel
import com.sibtainocn.alchemy.ui.exec.TerminalSheet
import com.sibtainocn.alchemy.ui.exec.TerminalViewModel
import com.sibtainocn.alchemy.ui.preview.MarkdownView
import com.sibtainocn.alchemy.ui.theme.CodeFont
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.LocalAccents
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid
import kotlinx.coroutines.launch
import java.io.File

/**
 * How wide each control in the editor bar is.
 *
 * Down from the 44 a lone icon would take. The bar carries seven of them beside a
 * filename now, and at 44 apiece the name was down to a few characters before its
 * ellipsis.
 */
private val BAR_ICON = 40.dp

@Composable
fun EditorScreen(
    vm: EditorViewModel,
    file: File,
    onClose: () -> Unit,
    /** Switches the editor to another file, for the terminal's own history. */
    onOpenFile: (File) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val copyToClipboard = rememberCopyToClipboard()
    val pasteFromClipboard = rememberPasteFromClipboard()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var treeOpen by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var runtimesOpen by remember { mutableStateOf(false) }

    // Shared with the rest of the app rather than owned by this screen: an install is a
    // long download that must not be abandoned because a dialog closed.
    val setup: SetupViewModel = viewModel()
    val terminal: TerminalViewModel = viewModel()
    val scope = rememberCoroutineScope()

    /** What this file would be run with, or null when nothing here runs it. */
    val runtime = remember(file.name) { Runtime.forFile(file.name) }

    /**
     * Opens the terminal, or the thing standing in its way.
     *
     * The same gate serves both buttons: there is no point opening a console onto a
     * runner that is not answering, and no point running a file with a language that is
     * not installed. Each obstacle leads to the dialog that clears it rather than to an
     * error.
     */
    fun withRunner(needs: Runtime?, then: () -> Unit) {
        scope.launch {
            val dir = file.parent ?: file.absolutePath
            if (terminal.preflight(dir) !is Readiness.Ready) {
                setupOpen = true
                return@launch
            }
            if (needs != null && !terminal.isInstalled(needs)) {
                runtimesOpen = true
                return@launch
            }
            then()
        }
    }

    LaunchedEffect(file.absolutePath) { vm.load(file) }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    fun leave() {
        if (vm.dirty) confirmExit = true else onClose()
    }

    // Text edits go straight to the view model as one undo step; the rest are things only
    // this screen can reach - the clipboard, the save pipeline, the undo history.
    fun handleKey(outcome: KeyOutcome) {
        when (outcome) {
            is KeyOutcome.Edit -> vm.apply(outcome.op)
            is KeyOutcome.Command -> when (outcome.command) {
                EditorCommand.SAVE -> vm.save()
                EditorCommand.UNDO -> vm.undo()
                EditorCommand.REDO -> vm.redo()
                EditorCommand.COPY -> copyToClipboard(SmartEdit.selectedTextOrLine(vm.value))
                EditorCommand.CUT -> {
                    copyToClipboard(SmartEdit.selectedTextOrLine(vm.value))
                    vm.apply { v ->
                        if (v.selection.collapsed) SmartEdit.deleteLine(v)
                        else SmartEdit.deleteSelection(v)
                    }
                }
                EditorCommand.PASTE -> pasteFromClipboard { text ->
                    vm.apply { SmartEdit.insert(it, text) }
                }
            }
            KeyOutcome.None -> Unit
        }
    }

    BackHandler { leave() }

    val isMarkdown = vm.language == Language.MARKDOWN
    val editing = vm.mode == ViewMode.EDIT

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The editor bar carries the status-bar inset; Scaffold adding it as well left a
        // dead strip at the top of the screen.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
        ) {
            EditorBar(
                name = file.name,
                subtitle = buildString {
                    append(vm.language.label)
                    append("  ·  ")
                    append(vm.value.text.count { it == '\n' } + 1)
                    append(" lines")
                    if (vm.readOnly) append("  ·  read-only")
                },
                dirty = vm.dirty,
                saving = vm.saving,
                canSave = vm.dirty && !vm.readOnly,
                showPreviewToggle = isMarkdown,
                previewing = !editing,
                showHistory = editing && !vm.readOnly,
                showTerminal = terminal.supported,
                showRun = terminal.supported && runtime != null,
                running = terminal.running,
                canUndo = vm.canUndo,
                canRedo = vm.canRedo,
                onBack = { leave() },
                onTitle = { treeOpen = true },
                onTogglePreview = { vm.switchMode(if (editing) ViewMode.PREVIEW else ViewMode.EDIT) },
                onUndo = { vm.undo() },
                onRedo = { vm.redo() },
                onTerminal = {
                    withRunner(needs = null) {
                        terminal.openAt(file.parent ?: file.absolutePath)
                    }
                },
                onRun = {
                    val language = runtime ?: return@EditorBar
                    withRunner(needs = language) {
                        // Saved first: the runner reads the file from disk and knows
                        // nothing about a buffer that has not been written yet.
                        if (vm.dirty) vm.save { saved -> if (saved) terminal.runFile(file, language) }
                        else terminal.runFile(file, language)
                    }
                },
                onSave = { vm.save() },
                onMenu = { menuOpen = true },
                menu = {
                    EditorMenu(
                        expanded = menuOpen,
                        vm = vm,
                        onDismiss = { menuOpen = false },
                        onCopyAll = {
                            copyToClipboard(vm.value.text)
                            menuOpen = false
                        },
                        onInfo = { infoOpen = true; menuOpen = false },
                        onSetup = if (setup.supported) {
                            { setupOpen = true; menuOpen = false }
                        } else {
                            null
                        },
                        onRuntimes = if (setup.supported) {
                            { runtimesOpen = true; menuOpen = false }
                        } else {
                            null
                        },
                    )
                },
            )

            HairlineDivider()

            AnimatedContent(
                targetState = Triple(vm.loading, vm.mode, vm.readOnly && vm.value.text.isEmpty()),
                transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy()) },
                label = "editor-body",
                modifier = Modifier.weight(1f),
            ) { (loading, mode, blocked) ->
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { ShapeLoader(size = 30.dp) }
                    blocked -> EmptyState(
                        "Cannot open this file",
                        vm.message ?: "It is not text, or it is not readable.",
                        Ico.Info,
                    )
                    mode == ViewMode.PREVIEW ->
                        MarkdownView(vm.value.text, Modifier.fillMaxSize(), vm.previewZoomPct / 100f)
                    else -> CodeField(
                        value = vm.value,
                        onValueChange = vm::onValueChange,
                        language = vm.language,
                        fontSizeSp = vm.fontSizeSp,
                        wordWrap = vm.wordWrap,
                        showLineNumbers = vm.lineNumbers,
                        readOnly = vm.readOnly,
                    )
                }
            }

            // The key bar only earns its space while you are actually editing text.
            AnimatedVisibility(
                visible = editing && !vm.readOnly && !vm.loading,
                enter = slideInVertically(Motion.offset()) { it } + fadeIn(),
                exit = slideOutVertically(Motion.offset()) { it } + fadeOut(),
            ) {
                Column {
                    HairlineDivider()
                    KeyBar(
                        language = vm.language,
                        mods = vm.modifiers,
                        order = vm.keyOrder,
                        onMods = vm::updateModifiers,
                        onOrderChange = vm::updateKeyOrder,
                        onOutcome = ::handleKey,
                    )
                    CaretStatus(vm)
                }
            }

            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "Discard changes?",
            body = "${file.name} has unsaved edits.",
            confirmLabel = "Discard",
            danger = true,
            onDismiss = { confirmExit = false },
        ) {
            confirmExit = false
            onClose()
        }
    }

    TerminalSheet(terminal, onOpenFile = onOpenFile)

    if (setupOpen) {
        RunnerSetupDialog(vm = setup, onDismiss = { setupOpen = false })
    }

    if (runtimesOpen) {
        RuntimePickerDialog(
            vm = setup,
            onDismiss = { runtimesOpen = false },
            onShowLogs = { runtimesOpen = false; terminal.showLog(setup.log) },
        )
    }

    if (treeOpen) {
        FileTreeSheet(
            current = file,
            onOpenFile = onOpenFile,
            onDismiss = { treeOpen = false },
        )
    }

    if (infoOpen) {
        FileInfoSheet(
            file = file,
            language = vm.language.label,
            lines = vm.value.text.count { it == '\n' } + 1,
            characters = vm.value.text.length,
            onCopyPath = { copyToClipboard(file.absolutePath) },
            onDismiss = { infoOpen = false },
        )
    }
}

/** Line/column readout - small, but it is the thing you look for when a stack trace names a line. */
@Composable
private fun CaretStatus(vm: EditorViewModel) {
    val accents = LocalAccents.current
    val text = vm.value.text
    val caret = vm.value.selection.start
    val line = remember(text, caret) { SmartEdit.lineNumberAt(text, caret) }
    val col = remember(text, caret) { SmartEdit.columnAt(text, caret) }
    val selected = vm.value.selection.length

    Row(
        Modifier
            .fillMaxWidth()
            .background(InkRaised)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Ln $line, Col $col" + if (selected > 0) "   ($selected selected)" else "",
            fontFamily = CodeFont,
            fontSize = 11.sp,
            color = accents.comment,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (vm.autoPair) "auto-pair on" else "auto-pair off",
            fontFamily = CodeFont,
            fontSize = 11.sp,
            color = if (vm.autoPair) accents.gutterActive.copy(alpha = 0.7f) else accents.comment,
        )
    }
}

@Composable
private fun EditorBar(
    name: String,
    subtitle: String,
    dirty: Boolean,
    saving: Boolean,
    canSave: Boolean,
    showPreviewToggle: Boolean,
    previewing: Boolean,
    showHistory: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showTerminal: Boolean,
    showRun: Boolean,
    running: Boolean,
    onBack: () -> Unit,
    onTitle: () -> Unit,
    onTogglePreview: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTerminal: () -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onMenu: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(Ico.Back, "Back", onClick = onBack)

        // The name is the handle for where the file sits, so it opens the folder around
        // it. Moving between two files in a project is the most repeated thing anyone
        // does in an editor, and routing it back through the file browser costs a screen
        // each way. What the file itself is stays one row down, in the menu.
        // A filled plate rather than bare text. The name is the way into the folder
        // around the file, and nothing about a title says "press me" on its own.
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radii.sm))
                .background(InkHigh)
                .clickable(onClick = onTitle)
                .padding(start = 11.dp, end = 9.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Unsaved state is a dot, not a word: it never pushes the filename around.
                AnimatedVisibility(dirty, enter = scaleIn(Motion.expressive()), exit = scaleOut()) {
                    Box(
                        Modifier
                            .padding(start = 7.dp)
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Icon(
                    Ico.ChevronDown,
                    null,
                    Modifier.padding(start = 6.dp).size(13.dp),
                    tint = TextLow,
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLow, maxLines = 1)
        }

        if (showPreviewToggle) {
            BarIcon(
                if (previewing) Ico.Code else Ico.Book,
                if (previewing) "Edit source" else "Preview",
                tint = if (previewing) MaterialTheme.colorScheme.primary else TextHigh,
                onClick = onTogglePreview,
            )
        }

        // The terminal opens a console on this file's folder; run sends the file itself
        // through it. Both are absent in a build that cannot run code, and run is absent
        // for a language nothing here knows how to start.
        if (showTerminal) {
            BarIcon(Ico.Terminal, "Terminal", onClick = onTerminal)
        }
        if (showRun) {
            BarIcon(
                Ico.Play,
                "Run",
                enabled = !running,
                tint = if (running) TextLow.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.primary,
                onClick = onRun,
            )
        }

        // Undo and redo are in the menu as well, but taking back a typo is the most
        // repeated action in an editor and it should not cost two taps and a menu. They
        // appear only while there is text being edited, so a preview or a read-only file
        // does not pay for them in bar width.
        if (showHistory) {
            BarIcon(
                Ico.Undo,
                "Undo",
                enabled = canUndo,
                tint = if (canUndo) TextHigh else TextLow.copy(alpha = 0.45f),
                onClick = onUndo,
            )
            BarIcon(
                Ico.Redo,
                "Redo",
                enabled = canRedo,
                tint = if (canRedo) TextHigh else TextLow.copy(alpha = 0.45f),
                onClick = onRedo,
            )
        }

        Box(Modifier.size(BAR_ICON), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = saving,
                transitionSpec = { fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy()) },
                label = "save",
            ) { busy ->
                if (busy) {
                    ShapeLoader(size = 20.dp)
                } else {
                    BarIcon(
                        Ico.Save,
                        "Save",
                        enabled = canSave,
                        tint = if (canSave) MaterialTheme.colorScheme.primary else TextLow.copy(alpha = 0.45f),
                        onClick = onSave,
                    )
                }
            }
        }

        Box {
            BarIcon(Ico.More, "More", onClick = onMenu)
            menu()
        }
    }
}

@Composable
private fun EditorMenu(
    expanded: Boolean,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onInfo: () -> Unit,
    /** Both null in a build that cannot run code, which is how the rows stay out of it. */
    onSetup: (() -> Unit)?,
    onRuntimes: (() -> Unit)?,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radii.md),
        // A dropdown sizes itself to its widest child. Without a fixed width the text-size
        // row's stepper was being squeezed until the label and the buttons stacked, which
        // is what made that row look broken.
        modifier = Modifier.width(272.dp),
    ) {
        MenuRow(Ico.Undo, "Undo", enabled = vm.canUndo) { vm.undo() }
        MenuRow(Ico.Redo, "Redo", enabled = vm.canRedo) { vm.redo() }
        HairlineDivider(Modifier.padding(vertical = 4.dp))

        MenuRow(Ico.Wrap, "Word wrap", trailing = onOff(vm.wordWrap)) { vm.toggleWrap() }
        MenuRow(Ico.Numbers, "Line numbers", trailing = onOff(vm.lineNumbers)) { vm.toggleLineNumbers() }
        MenuRow(Ico.Code, "Auto-pair", trailing = onOff(vm.autoPair)) { vm.toggleAutoPair() }

        HairlineDivider(Modifier.padding(vertical = 4.dp))
        // Reading a rendered page and editing its source are sized by different questions,
        // and only one of them is on screen at a time - so the row asks whichever applies.
        if (vm.mode == ViewMode.PREVIEW) {
            StepperRow(
                label = "Zoom",
                value = "${vm.previewZoomPct}%",
                onLess = { vm.setPreviewZoom(vm.previewZoomPct - EditorViewModel.PREVIEW_ZOOM_STEP) },
                onMore = { vm.setPreviewZoom(vm.previewZoomPct + EditorViewModel.PREVIEW_ZOOM_STEP) },
            )
        } else {
            StepperRow(
                label = "Text size",
                value = "${vm.fontSizeSp}",
                onLess = { vm.setFontSize(vm.fontSizeSp - 1) },
                onMore = { vm.setFontSize(vm.fontSizeSp + 1) },
            )
        }

        HairlineDivider(Modifier.padding(vertical = 4.dp))
        MenuRow(Ico.Copy, "Copy all", onClick = onCopyAll)
        MenuRow(Ico.Info, "File info", onClick = onInfo)
        onSetup?.let { MenuRow(Ico.Wrench, "Set up terminal", onClick = it) }
        onRuntimes?.let { MenuRow(Ico.Terminal, "Install languages", onClick = it) }
    }
}

/** The menu's one adjustable value: a label, a reading, and a step either side of it. */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ico.Font, null, Modifier.size(18.dp), tint = TextMid)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextHigh,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        StepButton(Ico.Minus, onClick = onLess)
        Text(
            value,
            fontFamily = CodeFont,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(46.dp),
        )
        StepButton(Ico.Plus, onClick = onMore)
    }
}

private fun onOff(on: Boolean) = if (on) "On" else "Off"

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        leadingIcon = {
            Icon(
                icon, null, Modifier.size(18.dp),
                tint = if (enabled) TextMid else TextLow.copy(alpha = 0.4f),
            )
        },
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) TextHigh else TextLow.copy(alpha = 0.5f),
            )
        },
        trailingIcon = trailing?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it == "On") MaterialTheme.colorScheme.primary else TextLow,
                )
            }
        },
    )
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = TextHigh)
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = TextHigh,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.snappy(), label = "tap")
    Box(
        Modifier
            .size(BAR_ICON)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 20.dp),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(19.dp), tint = tint)
    }
}
