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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.InkHigh
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.LocalAccents
import com.sibtainocn.alchemy.ui.editor.sora.EditorPalette
import com.sibtainocn.alchemy.ui.editor.sora.SoraCodeField
import io.github.rosemoe.sora.widget.CodeEditor
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
    var closingTab by remember { mutableStateOf<File?>(null) }
    var setupOpen by remember { mutableStateOf(false) }
    var runtimesOpen by remember { mutableStateOf(false) }

    // The view itself, for the commands the key bar and the bar produce. The buffer lives
    // on the view model; this is only the thing that knows where the caret is in it.
    var editor by remember { mutableStateOf<CodeEditor?>(null) }

    // Rendering Markdown means reading the buffer out as one string, which is the one
    // thing here that costs what the file is long. Held against the revision it was taken
    // at, so switching to the preview without having typed copies nothing.
    val preview = remember { PreviewText() }

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

    /** Drops a tab and goes wherever the view model says is left. */
    fun dropTab(target: File) {
        val next = vm.closeTab(target)
        when {
            next == null -> onClose()
            next.absolutePath != file.absolutePath -> onOpenFile(next)
        }
    }

    // Text edits go straight to the view model as one undo step; the rest are things only
    // this screen can reach - the clipboard, the save pipeline, the undo history.
    fun handleKey(outcome: KeyOutcome) {
        when (outcome) {
            is KeyOutcome.Edit -> editor?.let(outcome.op)
            is KeyOutcome.Command -> {
                val target = editor
                when (outcome.command) {
                    EditorCommand.SAVE -> vm.save()
                    // Through the view rather than the buffer, so the caret follows the
                    // change back to where it was made.
                    EditorCommand.UNDO -> target?.undo() ?: vm.undo()
                    EditorCommand.REDO -> target?.redo() ?: vm.redo()
                    // The editor's own clipboard: with nothing selected it takes the
                    // whole line, which is what these keys have always done.
                    EditorCommand.COPY -> target?.copyText(true)
                    EditorCommand.CUT -> target?.cutText()
                    EditorCommand.PASTE -> target?.pasteText()
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
                    append(vm.content?.lineCount ?: 1)
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
                            copyToClipboard(vm.content?.toString().orEmpty())
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

            // One row of what this session has open. It earns its height only once there
            // is somewhere else to go: with a single file the strip would be the name
            // from the bar, repeated directly under the bar.
            if (vm.tabs.size > 1) {
                TabStrip(
                    tabs = vm.tabs,
                    current = file,
                    unsaved = vm::hasUnsavedWork,
                    onSelect = { if (it.absolutePath != file.absolutePath) onOpenFile(it) },
                    onClose = { target ->
                        // Closing throws the tab's buffer away, so anything unwritten is
                        // asked about first, whether or not it is the file on screen.
                        if (vm.hasUnsavedWork(target)) closingTab = target else dropTab(target)
                    },
                )
                HairlineDivider()
            }

            AnimatedContent(
                targetState = Triple(vm.loading, vm.mode, vm.readOnly && (vm.content?.length ?: 0) == 0),
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
                    mode == ViewMode.PREVIEW -> MarkdownView(
                        preview.of(vm.content, vm.revision),
                        Modifier.fillMaxSize(),
                        vm.previewZoomPct / 100f,
                    )
                    else -> vm.content?.let { buffer ->
                        SoraCodeField(
                            content = buffer,
                            language = vm.language,
                            palette = EditorPalette(),
                            fontSizeSp = vm.fontSizeSp,
                            wordWrap = vm.wordWrap,
                            autoPair = vm.autoPair,
                            lineNumbers = vm.lineNumbers,
                            readOnly = vm.readOnly,
                            onChanged = vm::onContentChanged,
                            onCaret = vm::onCaret,
                            onReady = { editor = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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

    closingTab?.let { target ->
        ConfirmDialog(
            title = "Close without saving?",
            body = target.name + " has edits that have not been written.",
            confirmLabel = "Close",
            danger = true,
            onDismiss = { closingTab = null },
        ) {
            closingTab = null
            dropTab(target)
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
            lines = vm.content?.lineCount ?: 1,
            characters = vm.content?.length ?: 0,
            onCopyPath = { copyToClipboard(file.absolutePath) },
            onDismiss = { infoOpen = false },
        )
    }
}

/**
 * The open files, as a strip of names under the bar.
 *
 * Reduced to the two things a tab is for on a phone: getting back to a file, and getting
 * rid of one. No glyph, no path, no close-others menu. A file with unwritten edits shows
 * a dot where its cross would be, which is the one piece of state a tab has to carry and
 * the one place there is room to put it.
 */
@Composable
private fun TabStrip(
    tabs: List<File>,
    current: File,
    unsaved: (File) -> Boolean,
    onSelect: (File) -> Unit,
    onClose: (File) -> Unit,
) {
    val scroll = rememberLazyListState()

    // The open file changes from the tree and from closing a tab as well as from a tap in
    // here, and in those cases it can easily be off the end of the strip.
    LaunchedEffect(current.absolutePath, tabs.size) {
        val index = tabs.indexOfFirst { it.absolutePath == current.absolutePath }
        if (index >= 0) runCatching { scroll.animateScrollToItem(index) }
    }

    LazyRow(
        state = scroll,
        modifier = Modifier.fillMaxWidth().background(InkRaised),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(tabs, key = { it.absolutePath }) { tab ->
            Tab(
                file = tab,
                active = tab.absolutePath == current.absolutePath,
                unsaved = unsaved(tab),
                onSelect = { onSelect(tab) },
                onClose = { onClose(tab) },
            )
        }
    }
}

@Composable
private fun Tab(
    file: File,
    active: Boolean,
    unsaved: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.xs))
            .background(if (active) InkHigh else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 11.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) TextHigh else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Long enough for a real filename, short enough that three tabs still fit on
            // a phone before anything has to be scrolled to.
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Box(
            Modifier
                .padding(start = 3.dp)
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            if (unsaved) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            } else {
                Icon(Ico.Close, "Close " + file.name, Modifier.size(12.dp), tint = TextLow)
            }
        }
    }
}

/** Line/column readout - small, but it is the thing you look for when a stack trace names a line. */
@Composable
private fun CaretStatus(vm: EditorViewModel) {
    val accents = LocalAccents.current
    // Reported by the editor as the caret moves, rather than worked out from the buffer.
    val caret = vm.caret
    val line = caret.line
    val col = caret.column
    val selected = caret.selected

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

        MenuToggleRow(Ico.Wrap, "Word wrap", vm.wordWrap) { vm.toggleWrap() }
        MenuToggleRow(Ico.Numbers, "Line numbers", vm.lineNumbers) { vm.toggleLineNumbers() }
        MenuToggleRow(Ico.Code, "Auto-pair", vm.autoPair) { vm.toggleAutoPair() }

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

/**
 * A menu row for something that is either on or off.
 *
 * The state used to be the words "On" and "Off" in the trailing slot, which reads as a
 * label rather than as a control: it says what the setting is without saying that tapping
 * would change it. A switch says both.
 */
@Composable
private fun MenuToggleRow(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onToggle,
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = TextMid) },
        text = {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextHigh)
        },
        trailingIcon = {
            Switch(
                checked = on,
                // The whole row is the target, not just the switch. A control that is only
                // half of what you can press is a control people miss the other half of.
                onCheckedChange = null,
                // Material's switch is sized for a settings screen; a menu row is tighter.
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedThumbColor = TextLow,
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = Hairline,
                ),
            )
        },
    )
}

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

/**
 * The last string the preview was rendered from, and the revision it came out of.
 *
 * Reading a [Content] out as a string copies the whole document, so it is done when the
 * buffer has actually moved and not when the screen merely recomposed or the preview was
 * switched back to.
 */
private class PreviewText {
    private var revision = -1
    private var text = ""

    fun of(content: io.github.rosemoe.sora.text.Content?, at: Int): String {
        if (at != revision) {
            text = content?.toString().orEmpty()
            revision = at
        }
        return text
    }
}
