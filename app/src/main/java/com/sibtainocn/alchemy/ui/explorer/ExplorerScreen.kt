package com.sibtainocn.alchemy.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sibtainocn.alchemy.data.Entry
import com.sibtainocn.alchemy.data.FileStore
import com.sibtainocn.alchemy.data.SortBy
import com.sibtainocn.alchemy.data.Transfer
import com.sibtainocn.alchemy.ui.common.ConfirmDialog
import com.sibtainocn.alchemy.ui.common.EmptyState
import com.sibtainocn.alchemy.ui.common.EntryGlyph
import com.sibtainocn.alchemy.ui.common.Fmt
import com.sibtainocn.alchemy.ui.common.HairlineDivider
import com.sibtainocn.alchemy.ui.common.Ico
import com.sibtainocn.alchemy.ui.common.Motion
import com.sibtainocn.alchemy.ui.common.NameDialog
import com.sibtainocn.alchemy.ui.common.ShapeLoader
import com.sibtainocn.alchemy.ui.common.rememberCopyToClipboard
import com.sibtainocn.alchemy.ui.common.SheetAction
import com.sibtainocn.alchemy.ui.exec.RunnerSetupDialog
import com.sibtainocn.alchemy.ui.exec.RuntimePickerDialog
import com.sibtainocn.alchemy.ui.exec.SetupViewModel
import com.sibtainocn.alchemy.ui.exec.TerminalSheet
import com.sibtainocn.alchemy.ui.exec.TerminalViewModel
import com.sibtainocn.alchemy.ui.theme.Hairline
import com.sibtainocn.alchemy.ui.theme.InkRaised
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.sibtainocn.alchemy.data.ExternalOpen
import com.sibtainocn.alchemy.ui.theme.TextMid
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    vm: ExplorerViewModel,
    onOpenFile: (File) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val copyToClipboard = rememberCopyToClipboard()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * Opens a file wherever it belongs.
     *
     * Text and source go to the editor. A picture, a video, an archive or an installer
     * goes to whatever the device already opens it with, which is a better answer than
     * loading it and reporting that it is not text. The decision is made on the name so
     * that a tap does not have to wait on a read.
     */
    fun openEntry(file: File) {
        if (!ExternalOpen.isForAnotherApp(file.name)) {
            onOpenFile(file)
            return
        }
        if (!ExternalOpen.open(context, file)) {
            scope.launch { snackbar.showSnackbar("No app on this device opens ${file.name}") }
        }
    }

    var sheetFor by remember { mutableStateOf<Entry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newFile by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var moving by remember { mutableStateOf<Entry?>(null) }
    var sortSheet by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var runtimesOpen by remember { mutableStateOf(false) }

    // Setting the terminal up has nothing to do with any one file, so it is reachable
    // from here rather than only from inside the editor.
    val setup: SetupViewModel = viewModel()
    val terminal: TerminalViewModel = viewModel()

    // One scroll position per folder, so backing out of a directory lands you where you
    // left rather than at the top - the thing every file manager gets judged on. Bounded
    // and least-recently-used, since a long browsing session would otherwise keep a state
    // object for every folder ever opened.
    val scrollStates = remember {
        object : LinkedHashMap<String, androidx.compose.foundation.lazy.LazyListState>(32, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, androidx.compose.foundation.lazy.LazyListState>,
            ): Boolean = size > 64
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

    // Swallowing back at the root would trap the user in the app.
    BackHandler(enabled = !state.atRoot || state.searching) { vm.up() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The bars apply the status-bar inset themselves. Letting Scaffold apply it too
        // was the empty strip above the header on every screen.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // One button, two jobs. Cut or copy arms it and it becomes Paste for as long
            // as something is waiting; empty the clipboard and it is the + again. The
            // corner is where a thumb already is, and a second control down there would
            // only ever be the right one half the time.
            val armed = state.staged != null
            val busy = state.working != null
            FloatingActionButton(
                onClick = {
                    if (busy) return@FloatingActionButton
                    if (armed) vm.paste() else creating = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radii.md),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                AnimatedContent(
                    targetState = if (busy) 2 else if (armed) 1 else 0,
                    transitionSpec = {
                        (scaleIn(Motion.expressive(), initialScale = 0.7f) + fadeIn(Motion.standard()))
                            .togetherWith(
                                scaleOut(Motion.snappy(), targetScale = 0.7f) + fadeOut(Motion.snappy())
                            )
                    },
                    label = "fab",
                ) { mode ->
                    when (mode) {
                        2 -> ShapeLoader(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
                        1 -> Icon(Ico.Paste, "Paste here")
                        else -> Icon(Ico.Plus, "New")
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            ExplorerBar(
                state = state,
                onUp = { vm.up() },
                onSearchToggle = { vm.setSearching(it) },
                onQuery = vm::setQuery,
                onSort = { sortSheet = true },
                onHome = { vm.jumpTo(FileStore.storageRoot) },
                onCopyPath = { copyToClipboard(state.dir.absolutePath) },
                onSetup = if (setup.supported) ({ setupOpen = true }) else null,
                onRuntimes = if (setup.supported) ({ runtimesOpen = true }) else null,
            )

            Crumbs(dir = state.dir, onJump = vm::jumpTo)

            // The clipboard strip, in the one place it cannot be missed and cannot be
            // mistaken for part of the folder.
            AnimatedVisibility(
                visible = state.staged != null,
                enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
                exit = shrinkVertically(Motion.snappy()) + fadeOut(Motion.snappy()),
            ) {
                state.staged?.let { staged ->
                    ClipboardBar(
                        staged = staged,
                        working = state.working,
                        progress = state.progress,
                        onCancel = { vm.clearStaged() },
                    )
                }
            }

            HairlineDivider()

            // Directory changes slide: descending pushes in from the right, going up
            // pulls back from the left. It keeps the hierarchy legible without a map.
            AnimatedContent(
                targetState = state.dir.absolutePath to state.loading,
                transitionSpec = {
                    val forward = targetState.first.length >= initialState.first.length
                    val dx = if (forward) 1 else -1
                    (slideInHorizontally(Motion.offset()) { (it * 0.12f * dx).toInt() } + fadeIn(Motion.standard()))
                        .togetherWith(fadeOut(Motion.snappy()))
                },
                label = "dir",
                modifier = Modifier.fillMaxSize(),
            ) { (_, loading) ->
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { ShapeLoader(size = 30.dp) }
                    state.denied -> DeniedPane(state.dir.name)
                    state.visible.isEmpty() && state.query.isNotBlank() ->
                        EmptyState("No matches", "Nothing here is called \"${state.query}\".", Ico.Search)
                    state.visible.isEmpty() ->
                        EmptyState("Empty folder", "Use + to add a file or a folder.", Ico.Folder)
                    else -> EntryList(
                        entries = state.visible,
                        pinned = state.pinned,
                        showUpRow = !state.atRoot && state.query.isBlank(),
                        parentName = state.dir.parentFile?.name.orEmpty(),
                        listState = scrollStates.getOrPut(state.dir.absolutePath) {
                            androidx.compose.foundation.lazy.LazyListState()
                        },
                        onUp = { vm.up() },
                        onOpen = { e -> if (e.isDir) vm.open(e.file) else openEntry(e.file) },
                        onHold = { sheetFor = it },
                    )
                }
            }
        }
    }

    // ---- Sheets and dialogs ----

    if (creating) {
        ModalBottomSheet(
            onDismissRequest = { creating = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { SheetHandle() },
        ) {
            SheetTitle("Create in ${state.dir.name}")
            SheetAction(Ico.Doc, "New file", detail = "Opens straight in the editor") {
                creating = false; newFile = true
            }
            SheetAction(Ico.Folder, "New folder") { creating = false; newFolder = true }
            Spacer(Modifier.height(12.dp).windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    sheetFor?.let { entry ->
        EntryActionsDialog(
            entry = entry,
            pinned = entry.file.absolutePath in state.pinned,
            onDismiss = { sheetFor = null },
            onTogglePin = { vm.togglePin(entry); sheetFor = null },
            onOpen = {
                // Always the editor: this row is the way to edit a file that a tap sends
                // elsewhere, an .html being the case it exists for.
                if (entry.isDir) vm.open(entry.file) else onOpenFile(entry.file)
                sheetFor = null
            },
            onOpenWith = if (entry.isDir) null else {
                {
                    sheetFor = null
                    if (!ExternalOpen.open(context, entry.file)) {
                        scope.launch {
                            snackbar.showSnackbar("No app on this device opens ${entry.name}")
                        }
                    }
                }
            },
            onCut = { vm.stage(entry, Transfer.MOVE); sheetFor = null },
            onCopy = { vm.stage(entry, Transfer.COPY); sheetFor = null },
            onMove = { moving = entry; sheetFor = null },
            onRename = { renaming = entry; sheetFor = null },
            onCopyPath = {
                copyToClipboard(entry.file.absolutePath)
                sheetFor = null
            },
            onDelete = { deleting = entry; sheetFor = null },
        )
    }

    if (sortSheet) {
        ModalBottomSheet(
            onDismissRequest = { sortSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { SheetHandle() },
        ) {
            SheetTitle("Sort by")
            SortBy.entries.forEach { option ->
                val active = state.sortBy == option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Tapping the active option flips direction instead of doing nothing.
                            vm.setSort(option, if (active) !state.sortDescending else true)
                        }
                        .padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else TextHigh,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        Text(
                            if (state.sortDescending) "newest first" else "oldest first",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            HairlineDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            SheetAction(
                if (state.showHidden) Ico.Hidden else Ico.Eye,
                if (state.showHidden) "Hide dotfiles" else "Show dotfiles",
            ) { vm.toggleHidden() }
            Spacer(Modifier.height(12.dp).windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    if (newFile) {
        NameDialog("New file", hint = "script.py", onDismiss = { newFile = false }) { name ->
            newFile = false
            vm.createFile(name) { created -> created?.let(onOpenFile) }
        }
    }
    if (newFolder) {
        NameDialog("New folder", hint = "src", onDismiss = { newFolder = false }) { name ->
            newFolder = false
            vm.createFolder(name)
        }
    }
    renaming?.let { entry ->
        NameDialog("Rename", initial = entry.name, confirmLabel = "Rename", onDismiss = { renaming = null }) { name ->
            vm.rename(entry, name)
            renaming = null
        }
    }
    deleting?.let { entry ->
        ConfirmDialog(
            title = "Delete ${entry.name}?",
            body = if (entry.isDir) "The folder and everything inside it will be removed. This cannot be undone."
            else "This file will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            danger = true,
            onDismiss = { deleting = null },
        ) {
            vm.delete(entry)
            deleting = null
        }
    }

    moving?.let { entry ->
        FolderPickerSheet(
            // Starting where you already are, not at the root: the folder next door is a
            // far more common destination than one on the other side of the volume.
            start = state.dir,
            title = "Move " + entry.name,
            confirmLabel = "Move here",
            blocked = entry.file.takeIf { entry.isDir },
            onDismiss = { moving = null },
            onPick = { destination ->
                moving = null
                vm.moveTo(entry, destination)
            },
        )
    }

    // Raised from inside a running transfer, which stays parked until it is answered.
    state.ask?.let { ConflictDialog(it) }
}

/**
 * What cut or copy is holding, and the way out of it.
 *
 * Modelled on the strip an archive manager puts above the list while a clipboard is
 * armed: it says what is being carried, it shows how far a transfer has got, and the X
 * abandons it without touching anything on disk.
 */
@Composable
private fun ClipboardBar(
    staged: Staged,
    working: String?,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val moving = staged.transfer == Transfer.MOVE

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (moving) Ico.Cut else Ico.Copy, null, Modifier.size(17.dp), tint = onContainer)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    working ?: if (moving) "Ready to move" else "Ready to copy",
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    staged.entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (working == null) {
                BarButton(Ico.Close, "Cancel", tint = onContainer, onClick = onCancel)
            } else {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    ShapeLoader(size = 18.dp, color = onContainer)
                }
            }
        }

        // A determinate line while the size is known, and nothing at all when it is not:
        // a bar that cannot say how far along it is should not pretend to.
        if (working != null && progress != null) {
            val width by animateFloatAsState(progress, Motion.standard(), label = "transfer")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(onContainer.copy(alpha = 0.2f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(width.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(onContainer)
                )
            }
        }
    }
}

@Composable
private fun ExplorerBar(
    state: ExplorerState,
    onUp: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onSort: () -> Unit,
    onHome: () -> Unit,
    onCopyPath: () -> Unit,
    /** Both null in a build that cannot run code. */
    onSetup: (() -> Unit)?,
    onRuntimes: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Ico.Back, "Up", enabled = !state.atRoot || state.searching, onClick = onUp)

            AnimatedContent(
                targetState = state.searching,
                transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy()) },
                label = "bar",
                modifier = Modifier.weight(1f),
            ) { searching ->
                if (searching) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    TextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        placeholder = { Text("Filter in this folder", color = TextLow) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                } else {
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(
                            text = if (state.atRoot) "Internal storage" else state.dir.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHigh,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Fmt.folderMeta(state.visible),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLow,
                        )
                    }
                }
            }

            if (state.searching) {
                BarButton(Ico.Close, "Close search") { onSearchToggle(false) }
            } else {
                BarButton(Ico.Search, "Search") { onSearchToggle(true) }
                BarButton(Ico.Sort, "Sort", onClick = onSort)
                BarButton(Ico.Home, "Storage root", onClick = onHome)

                Box {
                    BarButton(Ico.More, "More") { menuOpen = true }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.width(240.dp),
                    ) {
                        BarMenuRow(Ico.Copy, "Copy path") { menuOpen = false; onCopyPath() }
                        onSetup?.let {
                            BarMenuRow(Ico.Wrench, "Set up terminal") { menuOpen = false; it() }
                        }
                        onRuntimes?.let {
                            BarMenuRow(Ico.Terminal, "Install languages") { menuOpen = false; it() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = TextMid) },
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = TextHigh) },
    )
}

/** Breadcrumb strip - the path in the reference, but each segment is a jump target. */
@Composable
private fun Crumbs(dir: File, onJump: (File) -> Unit) {
    val root = FileStore.storageRoot.absolutePath
    val chain = remember(dir.absolutePath) {
        val out = mutableListOf<File>()
        var cur: File? = dir
        while (cur != null) {
            out.add(0, cur)
            if (cur.absolutePath == root) break
            cur = cur.parentFile
        }
        out
    }
    val scroll = rememberScrollState()
    LaunchedEffect(dir.absolutePath) { scroll.animateScrollTo(scroll.maxValue) }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chain.forEachIndexed { i, f ->
            if (i > 0) {
                Icon(Ico.ChevronRight, null, Modifier.size(13.dp).padding(horizontal = 1.dp), tint = TextLow)
            }
            val last = i == chain.lastIndex
            Text(
                text = if (f.absolutePath == root) "storage" else f.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (last) MaterialTheme.colorScheme.primary else TextMid,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.xs))
                    .clickable(enabled = !last) { onJump(f) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryList(
    entries: List<Entry>,
    pinned: Set<String>,
    showUpRow: Boolean,
    parentName: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onUp: () -> Unit,
    onOpen: (Entry) -> Unit,
    onHold: (Entry) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 108.dp),
    ) {
        if (showUpRow) {
            item(key = "..") { UpRow(parentName, onUp) }
        }
        items(entries, key = { it.file.absolutePath }) { entry ->
            EntryRow(
                entry = entry,
                pinned = entry.file.absolutePath in pinned,
                onClick = { onOpen(entry) },
                onLongClick = { onHold(entry) },
                modifier = Modifier.animateItem(
                    fadeInSpec = Motion.standard(),
                    placementSpec = Motion.offset(),
                    fadeOutSpec = Motion.snappy(),
                ),
            )
        }
    }
}

/** The ".." row, exactly where a file manager puts it: first, always reachable. */
@Composable
private fun UpRow(parentName: String, onUp: () -> Unit) {
    val a = com.sibtainocn.alchemy.ui.theme.LocalAccents.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onUp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(Ico.Folder, null, Modifier.size(26.dp), tint = a.folder.copy(alpha = 0.55f))
            Icon(
                Ico.Back,
                null,
                Modifier.size(15.dp).rotate(90f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("..", style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            Text(
                if (parentName.isBlank()) "Up one level" else "Up to $parentName",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: Entry,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A small settle on press: enough to feel physical, not enough to read as a bounce.
    val scale by animateFloatAsState(if (pressed) 0.976f else 1f, Motion.snappy(), label = "press")

    Row(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (entry.isHidden) 0.55f else 1f)) {
            EntryGlyph(entry)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Fmt.subtitle(entry),
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                maxLines = 1,
            )
        }
        // The mark that explains why this row is where it is.
        if (pinned) {
            Icon(
                Ico.Pin,
                "Pinned",
                Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(7.dp))
        }
        Spacer(Modifier.width(10.dp))
        // Smaller than the two lines beside it. The date is what you check once you have
        // found the row, so it should not be competing to be read first.
        Text(
            Fmt.date(entry.modified),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.5.sp,
            color = TextLow,
        )
    }
}

/**
 * Android refused the listing. Rather than an empty list that looks like an empty folder,
 * say what happened and offer the one action that fixes it.
 */
@Composable
private fun DeniedPane(folderName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radii.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ico.Info, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Access denied",
            style = MaterialTheme.typography.titleMedium,
            color = TextHigh,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Android would not let Alchemy read \"" + folderName + "\". Some system folders " +
                "stay off-limits even with All files access; others need the permission " +
                "turned on.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        com.sibtainocn.alchemy.ui.common.PrimaryButton("Check permission") {
            runCatching {
                context.startActivity(com.sibtainocn.alchemy.ui.common.Storage.allFilesAccessIntent(context))
            }.onFailure {
                context.startActivity(com.sibtainocn.alchemy.ui.common.Storage.appSettingsIntent(context))
            }
        }
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = TextHigh,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.snappy(), label = "tap")
    Box(
        Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 22.dp),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            Modifier.size(21.dp),
            tint = if (enabled) tint else TextLow.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(Hairline, CircleShape)
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextLow,
        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
    )
}
