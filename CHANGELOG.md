# Changelog

All notable changes to Hazel IDE are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
- **Markdown tables**, with alignment, horizontal scroll and fixed column widths. Also
  task lists, nested and ordered lists, setext headings and backslash escapes.
- **Test tooling.** `:app:testSummary` reports real counts from the JUnit XML;
  `:app:verifyTestFloor` fails if the suite shrinks below the recorded floor.
- **CI** on every pull request: Gradle script syntax, Kotlin compile, Android Lint and
  tests. Packaging (debug + release through R8) runs on `main`.

### Fixed
- **Undo could run the app out of memory.** Each snapshot is a whole copy of the buffer,
  and the limit was a count: 120 snapshots of a 900 KB file is 208 MB, measured. The limit
  is now total characters held, with a minimum depth kept regardless, so a large file can
  still be undone.
- **Undo now sizes itself to the device.** The budget is taken from the app heap Android
  actually grants - commonly 128-256 MB even on a 6 GB phone - so a generous device keeps
  a deep history and a constrained one still keeps a usable one.
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
