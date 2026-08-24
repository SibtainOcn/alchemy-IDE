# Changelog

All notable changes to Hazel IDE are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
