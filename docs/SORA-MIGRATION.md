# Moving the editor onto sora-editor

Alchemy's code surface is being replaced. This is the record of why, what was measured to
justify it, how the outside code gets into this repository, and what breaks on the way.

Read this before changing anything in `app/src/main/java/com/sibtainocn/alchemy/ui/editor/`.

## The problem, as the device reported it

Opening a Python file, backing out to the browser, walking to another folder and opening
another file made the app stop responding for seconds at a time. Captured on a physical
RMX3151 running Android 13, from the platform's own frame log rather than guessed at:

```
6114ms  frame (365 frames dropped)
4723ms  frame (281 dropped)
3178ms, 2887ms, 1187ms ...

main RUNNABLE
  StaticLayout.generate                    <- building a text layout
  TextMeasurer.measure
  CodeField.kt:182                         <- the gutter's line numbers
  DrawBackgroundModifier.draw              <- during the DRAW phase
```

Two separate faults sat on top of each other.

The immediate one was the gutter. It walked `0 until lineCount` — every line in the
document, not the forty on screen — and on any file past 512 lines its number cache
overflowed, so it built a `StaticLayout` inside the draw phase. Draw must never allocate a
text layout. That was fixed in 1.1.3 and the worst frame in the same run fell to 505ms.

The one underneath it is architectural and is what this document is about.

## Why 1.1.3 was not enough

Alchemy puts the whole document into one `BasicTextField`, which becomes one
`MultiParagraph`, which becomes one `StaticLayout`. Every cost is therefore a function of
document size rather than of what is visible:

| | cost |
|---|---|
| Opening a file | lay out the entire document |
| One keystroke | lay it out again |
| Highlighting a 92KB file | 13.5ms on a desktop JVM, producing 13,000 spans |

Measured on the JVM, the highlighter alone:

```
chars=461     lines=24     spans=65      highlight=0.30ms
chars=9220    lines=480    spans=1300    highlight=2.65ms
chars=92200   lines=4800   spans=13000   highlight=13.48ms
```

A phone is several times slower than that, and the layout it feeds is slower still. After
1.1.3 the app no longer freezes, but opening a long file still costs 200–500ms, and that
number cannot be tuned away. `EDIT_LIMIT_BYTES` — the 2MB point past which a file opens
read-only — exists only because of this ceiling.

sora-editor is `O(viewport)`. It paints to a Canvas, only the lines on screen, over a
`Content` structure that is an array of lines rather than one `String`, with incremental
lexing. That is a different machine, not a faster version of the same one.

## The decision: adopt it, translate nothing

sora-editor's `editor` module as measured:

```
217 Java files   (41,876 lines)
 76 Kotlin files ( 7,638 lines)
deps: androidx.annotation, androidx.collection, kotlin-stdlib   <- all of them
minSdk 23    (Alchemy is 24)
```

A rewrite of this into Kotlin was considered and rejected. It is worth recording why,
because the idea is tempting and the reasons against it are not obvious. **Nothing is
translated.** The only Kotlin written is new bridge code in `app/`.

1. **Silent semantic drift.** Java `char` arithmetic, `int` overflow, integer division,
   `==` on boxed types. A conversion gets these *mostly* right, and "mostly" inside
   `Content.java` or `CachedIndexer.java` means corrupted documents at offset boundaries.
   That surfaces as data loss in someone's file, not as a crash in a test.
2. **Nullability.** Java carries no non-null guarantees, so conversion yields platform
   types needing thousands of annotations, and every wrong `!!` is a crash in the render
   loop.
3. **It forfeits upstream.** sora fixes real bugs — Samsung IME behaviour, RTL, emoji
   grapheme clusters. Taken as a dependency, a fix is a version bump. Translated, every
   fix is a manual re-derivation, forever.
4. **`EditorRenderer.java` is 2,698 lines of hot loop.** Hand-translation routinely
   introduces boxing where Java had primitives, which would recreate the performance
   problem the migration exists to escape.

### Licensing

sora-editor is LGPL-2.1. Alchemy is GPL-3. LGPL-2.1 §3 explicitly permits converting the
code to the ordinary GPL, so incorporating it is legitimate. The obligations are: keep the
copyright headers, state what was changed, ship under GPL-3, provide the source. All of
which this repository does anyway.

## How the code gets in

As a dependency, resolved from Maven Central:

```kotlin
// gradle/libs.versions.toml
soraEditor = "0.24.6"
sora-editor = { group = "io.github.rosemoe", name = "editor", version.ref = "soraEditor" }

// app/build.gradle.kts
implementation(libs.sora.editor)
```

That is the whole integration. Note the coordinate: `io.github.rosemoe:editor` is the
current one and is where releases land. The older `io.github.Rosemoe.sora-editor:editor`
is stalled at 0.23.6 and should not be used.

Copying the source into this repository was considered and rejected. It buys exactly one
thing — the ability to patch sora's internals without waiting for a release — and charges
for it daily: tens of thousands of unused lines in the tree, the whole library recompiled
on every clean build, and a merge process to run on every upgrade. A version bump is a
one-line change; a merge is not.

The decision is also cheap to revisit, which is the real argument for starting here. Every
difficult part of this migration lives in `app/` and is identical either way, so if a wall
ever appears that genuinely requires patching sora's internals, vendoring the source then
is a contained change to one line of `app/build.gradle.kts`. Paying for that insurance
before the wall exists is not worth it.

## Which files may become Kotlin

None of sora's own files are modified, so this only concerns bridge code. Never convert
anything for its own sake.

Everything written for this migration is new Kotlin in `app/`: the `AndroidView` wrapper,
the `Highlighter`→analyzer adapter, `AlchemyAccents`→`EditorColorScheme`, and symbol pairs.
sora's own Java and Kotlin arrive compiled in the artifact and are never touched.

## Decisions taken along the way

### Themes are slot ids, not colours

sora separates *what a run of text is* from *what that looks like*: the analyzer emits a
colour **slot id**, and the `EditorColorScheme` in force maps slots to colours. Alchemy's
highlighter is written to that shape in `ui/editor/sora/EditorPalette.kt`.

The consequence worth planning around: **switching themes costs one object swap and no
re-analysis.** A light theme, or a second highlighting scheme beside Monokai, is a new
`EditorPalette` instance and nothing else — no branch in the scanner, no invalidation of
work already done. `AlchemyAccents` is already a data class carrying every syntax colour,
so it needs no change to serve a second theme.

sora's own slots are reused where they mean the same thing. Alchemy's extra categories
(number, builtin, decorator, self-reference, punctuation) start at 100, above sora's
`END_COLOR_ID` of 83, so a future library version cannot collide with them.

Note this covers the *editor* only. The app chrome is still `darkColorScheme` in
`ui/theme/Theme.kt` and is a separate piece of work.

### The scanner reports kinds, and two sinks draw them

`Highlighter` used to hand back a Compose `AnnotatedString` with colours already baked in,
which the editor cannot use and which made a theme change a full rescan. It now has one
entry point that reports runs to a `TokenSink` as a `TokenKind` — `KEYWORD`, `STRING`,
`COMMENT` and so on — and knows nothing about colour at all. `Highlighter.kt` imports
nothing from `androidx.compose.ui.graphics` any more, which is the check that the split is
real rather than nominal.

Two sinks consume it:

| sink | for | produces |
|---|---|---|
| `syntax/AnnotatedStringSink` | the Markdown preview's code blocks | Compose spans |
| `ui/editor/sora/SoraSpanSink` | the code surface | sora `Styles`, in colour slots |

C's palette — its own numbers, strings, types and brackets — moved out of the scanner and
into `AlchemyAccents.colorOf(kind, lang)` where the rest of the drawing lives. It was
previously done by handing the scanner a doctored copy of the accents, which is what forced
the scanner to know about colours in the first place.

`Highlighter.highlight()` keeps its old signature on top of the new `scan()`, so
`HighlighterTest` is untouched. `HighlighterScanTest` pins the layer underneath it.

### `dirty` is tracked so that undoing back to the saved state is clean

The obvious approach — compare the buffer to the saved text on every change — is O(document)
per keystroke, which is the cost this migration exists to remove. The next idea, mirroring
sora's undo stack pointer, does not work either: `UndoManager.stackPointer` is private with
no getter, and `canUndo()`/`canRedo()` do not determine it. Counting `ContentChangeEvent`s
instead drifts, because sora **merges** consecutive keystrokes into a single undo action,
so three typed characters can be one stack entry.

What is used instead: the buffer can only equal the saved text if it is the same *length*,
and length is O(1). So

```
dirty = content.length != savedLength || content.toString() != savedText
```

memoised against a change counter, so it is evaluated at most once per edit. Typing almost
always changes the length, so the second half is normally never reached; it runs when an
undo brings the buffer back to the saved length, which is exactly the case that has to be
answered exactly, and costs one comparison to answer.

### The Markdown preview re-reads only when the buffer has moved

The preview cannot read `content.toString()` per recomposition — that is a full copy of the
document. It holds the text it last rendered along with the change counter it was taken at,
and re-reads only when switching into preview with a counter that has moved since. Edit
nothing and switch back and forth, and nothing is copied.

## What this breaks in Alchemy

The coupling to `TextFieldValue` is six files:

| file | fate |
|---|---|
| `ui/editor/CodeField.kt` (330 lines) | **deleted** — replaced by an `AndroidView` wrapper |
| `ui/editor/UndoHistory.kt` + `UndoStore.kt` (14 KB) | **deleted** — sora's `UndoManager` |
| `ui/editor/SmartEdit.kt` (17 KB, 46 references) | **the hard one.** Auto-pair and indent move to sora's `SymbolPairMatch` / `NewlineHandler`; the line operations are rewritten against `Content` |
| `ui/editor/EditorViewModel.kt` (8 references) | `TextFieldValue` → `Content`. Parked drafts become "keep the `Content` alive per tab", which is simpler than the current draft map |
| `ui/editor/KeyBarModel.kt` (4 references) | outcomes retarget to `Content` |
| `ui/common/Bits.kt` (2 references) | minor clipboard rewire |
| `syntax/Highlighter.kt` (21 KB) | **kept** — wrapped in a `SimpleAnalyzeManager`, which sora already runs off the main thread and re-runs on edits, emitting sora spans instead of an `AnnotatedString` |

Tests: `UndoHistoryTest` and `UndoStoreTest` (17 KB) are deleted with the code they cover.
`SmartEditTest` and `KeyBarModelTest` are rewritten. `HighlighterTest`,
`MarkdownParserTest`, `FileTransferTest`, `ConsoleTest`, `RuntimeTest` are untouched.

Unaffected entirely: the Markdown preview, the terminal, the file browser, transfers,
pins, and the editor's file tree sheet.

## What it buys

- Per-frame and per-open cost stops scaling with document size.
- `EDIT_LIMIT_BYTES`, the 2MB read-only ceiling, can go. Multi-megabyte files become
  editable.
- Already built and free: magnifier, working selection handles, long-press select,
  virtualised word wrap, correct IME composing regions, cursor blink, scrollbars, and the
  text action popup.
- An undo implementation that thousands of people have already found the bugs in.

## Step 3, as designed

Not yet built. Recorded here because the shape was worked out against the real API and
should not have to be re-derived.

### One buffer per tab replaces two stores

A sora `Content` carries its own `UndoManager`, and `setText(content, reuseContentObject =
true)` hands the same object back to the editor. So a tab that keeps its `Content` keeps
its text *and* its history for free, and `drafts` (unsaved buffers) and `UndoStore` (undo
per file) collapse into one map of path to `Content`.

Retention rules, carried over unchanged because they were right:

- Bounded by **retained characters**, not by a count of files. A tab costs nothing until it
  holds work that is not on disk.
- Evict least recently *shown*, never least recently opened.
- **Never evict a buffer holding unsaved work**, even if that means exceeding a limit.
- On reopening a path: a dirty retained buffer wins over disk; a clean one is kept if the
  file on disk still matches, and replaced if it does not, which is what picks up an edit
  made outside the app.

`UndoHistory.kt`, `UndoStore.kt`, `UndoHistoryTest.kt` and `UndoStoreTest.kt` are deleted
by this — about 31 KB of code and tests that sora already provides.

### `dirty`, `canUndo`, `canRedo` recompute on one signal

`ContentChangeEvent` fires for typing, undo and redo alike. One handler bumps a revision
counter and recomputes all three. `dirty` uses the length guard described above, so it is
O(1) except when a change lands the buffer back on the saved length — which is exactly the
case that has to be answered exactly.

### Steps 3 and 4 are one piece of work

They were listed apart and cannot be done apart. `SmartEdit` has fifteen public operations
and every one takes and returns a `TextFieldValue`; `KeyOutcome.Edit` is literally
`(TextFieldValue) -> TextFieldValue`. The view model cannot stop holding a `TextFieldValue`
until those are rewritten against `Content` and `Cursor`, so the build cannot be green
between the two. Treat them as one change.

### Open question: testing anything written against `Content`

`ContentLine` imports `android.text.GetChars` and `UndoManager` imports `android.os.Parcel`,
so `Content` cannot be constructed in a plain JVM unit test. The ported `SmartEdit`
operations are exactly the kind of index arithmetic that needs tests — `SmartEditTest` is
what currently pins them — so this has to be settled before the port, not after:

1. **Add Robolectric** to the unit test source set. sora tests itself this way. Costs a
   test dependency and slower tests; keeps the operations covered.
2. **Keep the operations pure** by writing them against `CharSequence` and a caret offset,
   with a thin `Content` adapter. Testable on plain JVM, but a second representation to
   keep honest.
3. **Cover them with instrumented tests instead.** Real device, no new dependency, much
   slower to run and not part of the current CI.

## To verify on device at the end

Not defects, but the places where the two models differ enough that reading the code will
not tell you whether it works. Each needs a real device and a real soft keyboard.

- **The key bar against sora's IME.** `CodeEditor` owns its own scrolling, selection
  handles and input connection. `KeyBar`'s modifier latches were written against a
  `TextFieldValue` that Compose owned and could be replaced wholesale between frames; they
  now sit beside an input connection that is being driven by the keyboard at the same time.
  Tab, the arrow keys and the modifier latches all need trying against a real IME rather
  than against the emulator's hardware keyboard.
- **Selection handles inside a scrolling parent.** The editor is a view inside Compose
  layout; its handles are drawn in its own window. Dragging one near the edge of the screen
  is the case to try.
- **`imePadding` and the key bar together.** The editor scrolls itself, so the previous
  arrangement - a scrolling text field inside `imePadding` - no longer describes what is
  happening, and the caret staying visible above the keyboard has to be re-checked.
- **Read-only files.** `setEditable(false)` should also stop the IME appearing at all.
- **The frame log, by the same method as the original diagnosis.** Open the same Python
  files, capture `Quality ... cost`, and compare against 505ms.

## Order of work

| | |
|---|---|
| 1 | Dependency in — done. `AndroidView` showing a file — next |
| 2 | ~~Colour scheme, and the `Highlighter` analyzer adapter~~ — done |
| 3 | View model on `Content`; save, dirty, tabs, drafts |
| 4 | KeyBar and the SmartEdit line operations; caret status |
| 5 | Tests; word wrap, font size, line numbers, read-only |
| 6 | Device testing against the frame log, same method as above |

## History

- **1.1.3** — the gutter fixed to draw only the viewport, highlighting moved off the main
  thread past 20k characters, simple line breaking, and the tab strip's limits rewritten
  against unsaved characters rather than a count of files. Worst frame 6114ms → 505ms.
  This is the fallback if the migration stalls.
- **`feat/sora-editor-core`** — the migration, built on top of 1.1.3.
