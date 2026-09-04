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

## Order of work

| | |
|---|---|
| 1 | Dependency in, an `AndroidView` showing a file |
| 2 | Colour scheme, and the `Highlighter` analyzer adapter |
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
