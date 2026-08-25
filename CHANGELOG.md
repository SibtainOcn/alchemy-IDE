# Changelog

All notable changes to Hazel IDE are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **A terminal, as a sheet over the editor.** Two buttons in the editor bar, F-Droid
  build only: the terminal opens a console on the folder of the file you are editing and
  waits for commands; run sends the file itself through it, saving first because the
  runner reads from disk. Either one, tapped before the terminal app is set up, opens the
  step that clears the way rather than reporting an error.
- **Everything a program prints is shown, whole.** Standard output and standard error
  both, unedited, before any of our own commentary, and printed even when the run failed
  on our side, because a program that managed three lines wrote three useful lines. A
  traceback is the part of a failed run that explains it, and an editor that replaced it
  with an exit code would be taking away the only thing worth reading. The scrollback is
  selectable so an error can be copied, and when output was too large to pass back whole
  the terminal says how much is missing rather than letting a cut-off trace look complete.
- **The prompt reads `~ $`.** The working directory is announced once when it changes and
  then stays out of the way. `cd` is handled here, since each command is its own process
  and nothing carries between them, and it is checked against the filesystem rather than
  believed: walking into a directory that is not there would otherwise break every
  command after it for a reason the terminal already knew.
- **A setup flow for the terminal**, in the F-Droid build only. Two dialogs: the first
  gets the runner installed, permitted and willing to take orders, showing which rung of
  that ladder you are on and the two commands to paste, each with a copy button and the
  reason it exists; the second offers Python, C and Go to install, none preselected, with
  a size beside each, Skip, and the command to run by hand instead. Reachable from the
  editor menu, and it will open by itself when a run needs something that is not there.
- **A language installer that thinks before it downloads.** Anything already present is
  skipped rather than fetched again, the rest go smallest first so something works within
  a minute, packages wanted by two languages are asked for once, and one language failing
  leaves the others to finish. A package manager that reports success is not believed
  until the program it installed actually answers.
- **Two builds from one codebase.** The `fdroid` flavour, which is what GitHub releases
  ship and keeps the existing `dev.hazel.code` id, is the one that can hand a file to a
  separately installed Termux. The `playstore` flavour carries no execution code and no
  Termux permission at all: not disabled at runtime, not shrunk away, simply never
  compiled into it. `docs/DISTRIBUTION-SPLIT.md` explains where each kind of change
  belongs.
- **An `ExecutionProvider` seam.** Shared code asks where code can run and reacts to the
  answer, without naming Termux or knowing which build it is in. Nothing calls it yet.
- **A runtime catalogue** covering Python, C and Go, with the packages each needs, the
  command that proves it is installed, and the shell line that runs a file. C compiles
  into Termux's own temporary directory rather than beside the source, because Android
  mounts shared storage non-executable and the program would be refused where it sat.
- **Undo and redo in the editor bar**, beside save. Taking back a typo is the most
  repeated action in an editor and it cost two taps and a menu. They show only while text
  is being edited, so a preview or a read-only file does not spend bar width on them, and
  they stay in the menu as well.
- **Release signing and packaging.** `keystore.properties` (local) or environment
  variables (CI) drive a real `signingConfig`, so the release variant installs from
  Android Studio's Run button and from `./gradlew assembleRelease`.
- **Per-architecture APKs.** `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` and universal,
  named `HAZEL-IDE-<CHANNEL>-v<version>-<abi>.apk` with a `checksums.txt` beside them.
- **Tag-driven releases.** Pushing `v1.2.0` builds, signs, and publishes; a pre-release
  suffix publishes as a pre-release and names its APKs `BETA` rather than `STABLE`.
- **Key bar modifiers.** Ctrl, Shift, Caps and Tab, pinned so they never scroll out of
  reach. Ctrl and Shift latch for one key; Caps locks. Arming Ctrl swaps the scrolling
  group for a shortcut set (save, undo, redo, select all, copy, cut, paste).
- **Caret keys.** Arrows, home and end, with Shift to extend the selection — none of which
  a phone keyboard offers.
- **Draggable key bar.** Long-press any key and drag it into place. Saved per language; a
  saved order survives the key set changing between versions.
- **Markdown preview zoom.** The editor menu's stepper asks the question that fits what is
  on screen: text size while editing source, zoom while reading the rendered page. Zoom
  runs 60-250% in steps of ten, is remembered, and scales headings, code blocks, table
  columns and the space between them together rather than only the body text.
- **Markdown tables**, with alignment, horizontal scroll and fixed column widths. Also
  task lists, nested and ordered lists, setext headings and backslash escapes.
- **Undo history per file, for as long as the app is running.** Leaving a file to look at
  another one and coming back finds its history where you left it. Nothing is written to
  disk, so closing the app clears every history - there is no saved undo file to go stale
  against a file edited elsewhere between sessions. A history whose file has changed
  underneath it is dropped rather than reused, and the memory budget is shared across
  files rather than granted to each of them: past it, the least recently edited file loses
  its history whole so the file in front of you keeps all of its own.
- **Test tooling.** `:app:testSummary` reports real counts from the JUnit XML;
  `:app:verifyTestFloor` fails if the suite shrinks below the recorded floor.
- **CI** on every pull request: Gradle script syntax, Kotlin compile, Android Lint and
  tests. Packaging (debug + release through R8) runs on `main`.

### Fixed
- **The starting window drew the old, filled mark.** The launcher icon was corrected but
  the splash drawable was not, so the app opened on the blob for a moment and then swapped
  to the outlined bolt once its own screens took over. Both are stroked now.
- **The launcher icon was a blob rather than a bolt.** The mark is an open curve meant to
  be stroked; it was being filled as well, which closes it through the shortest line
  between its ends and fattened the shape until it read as a smudge at icon size. It is
  now stroked only, at the size the other Hazel app uses, which also brings it inside the
  safe circle no launcher mask can clip.
- **A key could only be dragged one place per long-press.** Every crossing was written
  straight back to the saved order, which rebuilt the row from a new list mid-gesture,
  restarted the pointer input under the finger and ended the drag. A drag now runs against
  a local order and is saved on release, so a key goes wherever it is dropped; holding it
  against either edge scrolls the row underneath it, which is what makes the far end of
  the bar reachable in one gesture.
- **Undo could run the app out of memory.** Each snapshot is a whole copy of the buffer,
  and the limit was a count: 120 snapshots of a 900 KB file is 208 MB, measured. The limit
  is now total characters held, with a minimum depth kept regardless, so a large file can
  still be undone.
- **Undo now sizes itself to the device.** The budget is taken from the app heap Android
  actually grants - commonly 128-256 MB even on a 6 GB phone - so a generous device keeps
  a deep history and a constrained one still keeps a usable one. It is a tenth of that
  heap, capped at 24 MB, because history is not the only copy of the file in memory: the
  buffer, the layout Compose builds from it and the highlighter's styled spans are all
  live at the same time.
- **Line numbers flickered while editing Markdown.** The gutter read the field's current
  text against the previous frame's layout, and those disagree for a frame after every
  keystroke - so the count jumped between N and N+1. It now reads the layout's own text.
  Python files were affected too; Markdown just re-lays-out often enough to make it
  visible.
- **The current line was not findable.** The row band was `#0D0D0D` on true black -
  present in the buffer, absent to the eye. It is now visible and continues across the
  gutter, so the active row reads as one line rather than two halves, with its number
  brightened.
- **Cyan leaked onto ordinary Markdown text.** Bold prose in the editor and inline code in
  the preview were both tinted with the accent, which made half a table look like links
  and left nothing distinct for actual links. Bold is now weight rather than colour, and
  inline code is monospace on a tinted chip; the accent means "link".
- **Markdown preview was unreadable on real READMEs.** Every non-blank line was folded
  into one paragraph, so a table arrived as a wall of pipes. Block starts now interrupt
  the paragraph before them. Empty and ragged table cells are kept rather than dropped, so
  columns stay aligned.
- **A dead strip at the top of every screen.** `Scaffold` applied the status-bar inset and
  the bars applied it again.
- **Line numbers were invisible.** Inactive numbers were `#3A3A3F` on true black; only the
  current line could be read.
- **`print(x)` rendered cyan.** The builtin list was consulted before the call check;
  Monokai paints a call green whether or not the name is a builtin.
- **The text-size row in the editor menu stacked vertically.** The dropdown had no fixed
  width, so the stepper was squeezed until it wrapped.
- **`gradlew` was committed without its executable bit**, so every CI step failed before
  Gradle started.

### Changed
- Release titles carry the app name - `Hazel-IDE 1.2.0` rather than `1.2.0`. A releases
  list, a notification and a shared link all show the title on its own.
- Release notes reference pull requests by number rather than by full URL. The link is the
  same; the line is readable at the width a phone shows it.
- **Undo stores edits, not copies of the file.** A snapshot history cost the size of the
  document per step, which made depth a function of file size: capping the memory left a
  2 MB file about seven steps. A step now costs the size of the change - a keystroke is a
  keystroke whether the file around it is 4 KB or 2 MB - so the depth is a flat 500 steps
  on every file, and a thousand keystrokes in a 1 MB file hold under 2 KB of history
  rather than the hundreds of megabytes the same session cost before. This is how Vim,
  Emacs, VS Code and Compose's own text field all store history.
- **A line break is its own undo step.** Nothing merges into it and it merges into
  nothing, so undo hands back the words on a line before it hands back the line.
- Undo restores the caret and selection from either side of the edit, rather than leaving
  it wherever the change ended.
- A history that no longer matches the buffer is discarded rather than applied. A snapshot
  could not be wrong about the text it replaced; an edit can be, and applying one to a
  buffer it does not fit would corrupt the file.
- The history budget is a flat figure rather than a share of the device heap. It was read
  from the heap when a step cost as much as the whole file; it no longer does.
- **The key bar is one row and every key can be dragged**, Ctrl and Tab included. Pinning
  four keys cost their width on every screen and stopped exactly the keys people most want
  to move from being moved.
- **Shift and Caps are gone.** The IME already has both, and a second competing idea of
  "shifted" earned nothing. Outdent keeps its own key, so nothing became unreachable.
- The Markdown preview toggle is an open book rather than an eye. The question in a
  Markdown file is which of two forms you are reading, not whether something is hidden.
- Toolbar edits, highlighting, Markdown parsing and clipboard reads are wrapped so a
  failure in any of them leaves the text untouched instead of taking the screen down.
- The no-wrap width is measured from the longest line's length rather than its content.
  The editor font is monospace, so width follows character count, and typing inside a line
  that is not the longest now re-measures nothing.
- The gutter's number cache and the explorer's per-folder scroll positions are both
  bounded and least-recently-used; each previously grew for the life of the process.
- Undo's memory total is carried as a running count rather than summed on demand. It is
  read on every push, and summing walked every snapshot in the history to answer what two
  additions can.
- The syntax highlighting ceiling is 250 KB (~6,000 lines), down from 300 KB. Above it a
  file stays fully editable and loses only colour.
- The loader is rebuilt on four shapes rather than six, so the morph never reverses back
  through states it just came from, with a 2.6s turn.
- The folder header reads `1.5 KB · 19 items` rather than the count alone.

## [1.0.0] — 2026-08-24

First release. A file browser and a code editor, and deliberately nothing else: no
plugins, no build tools, no code execution, no network permission.

### Added

**Explorer**
- Single-list browser over internal storage — folders and files together, tap a folder to
  descend, tap a file to open it.
- `..` row for going up, plus a breadcrumb strip where every segment is a jump target.
- Scroll position remembered per folder, so backing out lands where you left.
- Sort by date, name, size or type; tapping the active option flips the direction.
  Dotfiles hidden by default, toggleable.
- Filter-in-folder search.
- Long-press a file or folder for a dialog with Open, Rename, Copy path and Delete.
- Create files and folders; a new file opens straight in the editor.
- Folder glyphs are drawn, not iconified; files show their extension in the language's own
  syntax colour, so a source directory is scannable by type.

**Editor**
- Syntax highlighting via a single-pass character scanner — correct around strings inside
  comments, comments inside strings, and Python's triple-quoted blocks.
- Languages: Python, Kotlin, Java, JavaScript/TypeScript, C/C++/Rust/Go/Swift, shell,
  JSON, XML/HTML, Markdown, and config formats.
- Monokai colours: pink keywords and operators, green definitions, cyan italic types,
  purple literals — on a true-black ground.
- C and C++ carry per-token overrides: white numerics, orange strings, red library calls,
  cyan braces.
- Smart typing: bracket and quote pairing, wrapping a selection, stepping over a closer,
  removing both halves on backspace, indent carried across newlines, Python block opening
  on a trailing colon, and closers pushed to their own line.
- Key bar above the keyboard with the characters and block operations code needs, keyed to
  the current language.
- Gutter line numbers that stay aligned across wrapped lines, current-line band, and a
  live line/column readout.
- Word wrap, line numbers, auto-pair and text size all toggleable and remembered.
- Undo/redo with coalesced steps — a run of typing is one step, not one per character.
- Atomic saves: written beside the target and swapped, so a failed write cannot destroy
  the original.

**Preview**
- Markdown renders headings, ordered and unordered lists, quotes, rules, and the inline
  run of bold, italic, strikethrough, code and links.
- Fenced code blocks are highlighted with the same engine as the editor.
- Toggle between rendered and raw source; `.md` files open rendered. Tables are not
  parsed and fall through as plain text.

**Shell and design**
- True black throughout (`#000000` page, `#0A0A0A` raised), cyan interactive accent, amber
  reserved for folder glyphs.
- Material 3 expressive motion: spring-based transitions, directional folder navigation,
  press states, and a shape-morphing loader built from `RoundedPolygon` geometry — the
  Play Store loading behaviour, at whatever size the context needs.
- Hand-authored icon set on a 24-unit grid; the Material icon library is not a dependency.
- Splash screen and adaptive launcher icon from the Hazel mark.
- Opens files sent by other apps through VIEW/EDIT intents, including storage-provider
  document URIs.

### Security and privacy
- No `INTERNET` permission is declared. Nothing the app reads can leave the device.
- Storage access is requested once, explained plainly, and re-checked on resume.
- Refusals from Android are surfaced as a typed failure with a route to the relevant
  Settings page, never as a silently empty folder.

### Known limitations
- Files over 2 MB open read-only; over 16 MB they do not open. Syntax highlighting is
  skipped past 300,000 characters.
- Binary files are detected and declined rather than shown as mojibake.
- Content URIs from providers other than the system storage provider are not resolved to a
  path and will not open.
- There is no search across folders, no multi-select, and no copy/move between folders.

### Build
- Kotlin, Jetpack Compose, Material 3. AGP 9.3.1 / Gradle 9.7.1, `compileSdk` 37,
  `minSdk` 24.
- Release APK is ~1.5 MB with R8 enabled.
- The typing engine is covered by unit tests (`SmartEditTest`, 14 cases).
