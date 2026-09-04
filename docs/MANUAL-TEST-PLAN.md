# Manual test plan

Every change that a person can see, in the order it was made, with what should happen.
Written as it went rather than reconstructed at the end, so nothing is missing because
nobody remembered it.

Work down it on a real device. Mark each row **pass**, **fail** or **n/a**, and when
something fails write what actually happened next to it — "fail" on its own is not
findable later.

Test files: `/sdcard/Download/python-playground/00 bootcamp/` — `exercises/` are ~600 B,
`practice/` ~12 KB, `solutions/` ~125 KB, and `99_big_dataset.py` is ~880 KB. Several rows
need a file of a particular size and say so.

---

## Phase 1 — the freeze (`c9a78fc`, PR #20, shipped as 1.1.3)

### The gutter drawing only what is on screen

| # | Do this | Expect | Result |
|---|---|---|---|
| 1.1 | Open `solutions/01_solution.py` (125 KB) | Opens in well under a second, no frozen screen | |
| 1.2 | Fling-scroll it top to bottom several times | Stays smooth. No pause of seconds at any point | |
| 1.3 | Open → back → another folder → open another file. Repeat 6–8 times | Never freezes. This is the exact sequence that used to cost 6 seconds a frame | |
| 1.4 | Open `99_big_dataset.py` (880 KB) | Opens read-only (over the 2 MB edit limit? no — under it, so editable). Scrolls without stalling | |
| 1.5 | With `adb logcat`, repeat 1.3 and grep `Quality.*cost` | Worst frame well under 1000 ms. Was 6114 ms | |

### Line numbers

| # | Do this | Expect | Result |
|---|---|---|---|
| 1.6 | Scroll a long file slowly | Numbers always match their lines. None missing, none doubled | |
| 1.7 | Type in the middle of a long file | Numbers never flicker between N and N+1 | |
| 1.8 | Turn word wrap on, find a line that wraps | Wrapped continuation rows carry **no** number. Only real lines are numbered | |
| 1.9 | Scroll to past line 512 in a long file | Numbers still correct — this is where the old cache overflowed | |
| 1.10 | Open a file with 1000+ lines | Gutter is wide enough for 4 digits, nothing clipped | |
| 1.11 | Put the caret on a line and look at the gutter | That line's number is in the accent colour, and the highlight band runs across gutter and text as one bar | |

### Highlighting moved off the main thread

| # | Do this | Expect | Result |
|---|---|---|---|
| 1.12 | Open `exercises/01_exercise.py` (small) | Colours are there on the first frame — no flash of plain text | |
| 1.13 | Open `solutions/01_solution.py` (125 KB) | Text appears immediately; colours land a moment later | |
| 1.14 | Type continuously in the 125 KB file | Typing keeps up. Characters do not lag behind the keyboard | |
| 1.15 | Type fast, then stop | Colours catch up within a beat of stopping. Never wrong for the text on screen | |
| 1.16 | Type a `"` in the middle of a long file and watch briefly | Colouring may be a beat stale, but text and caret are never wrong | |
| 1.17 | Open a `.json`, `.md`, `.kt`, `.sh` file | Each is coloured for its own language | |

### Re-entering the editor

| # | Do this | Expect | Result |
|---|---|---|---|
| 1.18 | Open file A → back → open file B | No flash of A's contents before B appears | |
| 1.19 | Same with two large files | Same, and noticeably quicker than before | |

### The file tree sheet

| # | Do this | Expect | Result |
|---|---|---|---|
| 1.20 | Tap the filename in the editor bar | Sheet opens about half way up | |
| 1.21 | Drag it to the very top | Reaches the top and **stays**. No up-down shaking | |
| 1.22 | At the top, scroll the tree to its first row and keep pulling down | Sheet drags down smoothly. No fighting between sheet and list | |
| 1.23 | Expand and collapse several folders, then drag again | Still no flicker | |
| 1.24 | Tap a file in the tree | Sheet closes, that file opens | |
| 1.25 | Tap the up arrow | Moves to the parent folder, previous folder is expanded | |

### Open files strip

| # | Do this | Expect | Result |
|---|---|---|---|
| 1.26 | Open one file | No strip — it would just repeat the bar | |
| 1.27 | Open a second | Strip appears with both, current one filled in | |
| 1.28 | Open 20+ files one after another | Strip holds at 20, oldest-*visited* dropped first | |
| 1.29 | Edit a file without saving, then open many others | The unsaved one is **never** dropped, even past 20 | |
| 1.30 | Edit without saving, switch away, come back | Edits are still there | |
| 1.31 | Tap the × on a tab with unsaved edits | Asks first | |
| 1.32 | A tab with unsaved edits | Shows a dot where its × would be | |
| 1.33 | Close the tab that is on screen | Falls to its left neighbour | |
| 1.34 | Close the last tab | Leaves the editor | |

---

## Phase 2 — Markdown table (`6729a0c`)

Needs a `.md` file with a table whose columns differ a lot — one of `yes`/`no` cells and
one of long sentences.

| # | Do this | Expect | Result |
|---|---|---|---|
| 2.1 | Open it in preview | Narrow columns are narrow, wide ones wide. **Not** all the same width | |
| 2.2 | A cell with a very long sentence | Column stops growing at a cap and the text wraps; the row gets taller | |
| 2.3 | The header row | Ordinary bright text — **not** cyan, not link-coloured | |
| 2.4 | A real `[link](url)` in a cell | Still cyan and underlined | |
| 2.5 | Inline `` `code` `` in a cell | Monospace on a tinted chip, not cyan | |
| 2.6 | Zoom the preview in and out | Columns grow and shrink with everything else | |
| 2.7 | A table wider than the screen | Scrolls sideways; the rest of the page does not | |
| 2.8 | Alignment markers `:---`, `:---:`, `---:` | Left, centre, right respected | |
| 2.9 | A one-character column | Still wide enough to read, not squashed | |
| 2.10 | Task lists, headings, quotes, code blocks | Unchanged from before | |

---

## Phase 3 — the sora editor (steps 3–6, in progress)

Nothing here is testable until the view model port lands. Listed now so it is not
reconstructed later.

### It draws and edits

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.1 | Open a small `.py` | Text, colours, gutter, current-line band all as before | |
| 3.2 | Compare colours against phase 1 screenshots | Identical — same palette, same Monokai | |
| 3.3 | Open `99_big_dataset.py` | Opens **fast**, scrolls at full speed. This is the point of the migration | |
| 3.4 | Type in it | No lag at any file size | |
| 3.5 | Word wrap on/off | Wraps, and the gutter still numbers real lines only | |
| 3.6 | Font size up and down | Text and gutter scale together | |
| 3.7 | Line numbers off | Gutter disappears, text fills the width | |
| 3.8 | A file over 2 MB | Read-only, and the keyboard does **not** appear when tapped | |

### Undo, and what a tab keeps

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.9 | Type a word, undo | Whole word goes, not one letter | |
| 3.10 | Undo repeatedly to the start | Reaches the original file and stops | |
| 3.11 | Redo back | Returns to where you were | |
| 3.12 | Edit, switch tab, switch back, undo | History is still there — a tab keeps its own | |
| 3.13 | Edit, then undo back to the saved state | Dirty marker **clears**. This is the one that needed a specific design | |
| 3.14 | Undo to saved, then redo | Dirty marker returns | |
| 3.15 | Save, edit, undo to the saved text | Dirty clears | |
| 3.16 | Edit, undo past the save point, type something new | Stays dirty — the saved state is now unreachable | |

### The key bar (highest risk — see SORA-MIGRATION.md)

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.17 | Every symbol key | Inserts at the caret, keyboard stays up | |
| 3.18 | Arrow keys | Caret moves one character / one line | |
| 3.19 | Home / End | Start and end of the line | |
| 3.20 | Tab, no selection, mid-line | Advances to the next 4-column tab stop — not a full line indent | |
| 3.21 | Tab with lines selected | Indents every touched line, one undo step | |
| 3.22 | Dedent | Removes up to one indent per line; lines with none are untouched | |
| 3.23 | Comment toggle in `.py` | `# ` on each line | |
| 3.24 | Comment toggle in `.kt` / `.js` | `// ` on each line | |
| 3.25 | Comment toggle on an already-commented block | Uncomments it | |
| 3.26 | Comment toggle on a **mixed** block | Comments all of it, does not invert | |
| 3.27 | Comment toggle in `.xml` | Does nothing (no half-written pair) | |
| 3.28 | Duplicate line | Copy appears below | |
| 3.29 | Delete line | Line and its break go, caret lands at the start of the next | |
| 3.30 | Delete the **last** line of a file | No blank line left behind | |
| 3.31 | Delete the **only** line | Empties it, file survives | |
| 3.32 | Ctrl latch, then a key | Applies once, then releases | |
| 3.33 | Select all, cut, paste | Round-trips exactly | |
| 3.34 | Drag to reorder keys, reopen the file | Order remembered for that language | |
| 3.35 | Undo any block operation above | One undo step, not one per line | |

### Auto-pair and Enter

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.36 | Type `(` | `)` appears, caret between | |
| 3.37 | Type `)` where one already sits | Steps over it, does not double | |
| 3.38 | Select a word, type `(` | Wraps the selection | |
| 3.39 | Type `'` in `it` → `it's` | Does **not** pair inside a word | |
| 3.40 | Turn auto-pair off, type `(` | No partner | |
| 3.41 | Enter after `def f():` in Python | New line indented one level further | |
| 3.42 | Enter after `{` with `}` already after the caret | Three lines: opener, indented blank, closer on its own | |
| 3.43 | Enter on an indented line | Indent carried down | |

### IME and selection (needs a real soft keyboard, not adb)

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.44 | Tap the text | Keyboard opens, caret lands where tapped | |
| 3.45 | Type with the caret near the bottom | Caret stays visible above the keyboard | |
| 3.46 | Long-press a word | Selects it, handles appear | |
| 3.47 | Drag a handle to the screen edge | Selection follows, scrolls, no handle left stranded | |
| 3.48 | Long-press and drag with the key bar showing | Both usable, neither covers the other | |
| 3.49 | Gboard prediction / swipe typing | Text arrives correctly, no duplication | |
| 3.50 | Rotate the device mid-edit | Text, caret and scroll position survive | |

### Preview refresh

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.51 | Open a `.md`, switch to edit and back without typing | Preview unchanged, no visible re-render | |
| 3.52 | Edit, then switch to preview | Shows the edit | |
| 3.53 | Switch back and forth several times without editing | No stutter — nothing is being re-read | |

### Save and files

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.54 | Edit and save | "Saved", dirty marker clears, file on disk changed | |
| 3.55 | Back out with unsaved edits | Asks before discarding | |
| 3.56 | Run a Python file (fdroid build, Termux) | Saves first, then runs | |
| 3.57 | Open a binary file | Refuses clearly, does not hang | |
| 3.58 | Open a file, change it from Termux, reopen the tab | Picks up the outside change — **if the tab was clean** | |
| 3.59 | Same but with unsaved edits in the tab | Keeps your edits, does not silently overwrite | |

### Regression sweep (should be untouched)

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.60 | File browser: cut, copy, paste, move, rename, delete | All as before | |
| 3.61 | Pin and unpin | As before | |
| 3.62 | Terminal: run, history, output | As before | |
| 3.63 | Markdown preview, and phase 2 rows again | As before | |
| 3.64 | Splash, storage permission gate | As before | |
| 3.65 | Open a file from another app ("open with") | Opens it | |

### The measurement that started this

| # | Do this | Expect | Result |
|---|---|---|---|
| 3.66 | Repeat 1.3 with logcat, grep `Quality.*cost` | Worst frame lower again than 1.5. Record the number | |
| 3.67 | Watch `Background concurrent copying GC` while opening ten files | Heap steady, no runaway growth | |
