# Changelog

All notable changes to Alchemy IDE are recorded here. Releases up to 1.1.0 shipped under
the name Hazel IDE.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.3] - 2026-09-05

### Fixed
- **The terminal setup screen no longer says it is ready when it is not.** Readiness was
  measured by one handshake, which proves only that the terminal app answers. Somebody who
  had run the first of the three instructions and neither of the others was told everything
  was in place, and then every run failed on a file that plainly existed. It is a checklist
  now: install, permission, take commands, reach your files, each verified separately and
  each showing its own answer. The rows are checked in order and the run stops at the first
  failure, so nothing below a real problem is reported as broken too.
- **Program output in the terminal is no longer dimmer than the prompt above it.** It was
  drawn a step down from the command that produced it, which made the result of every
  successful run read as an aside.

- **A long file no longer freezes the app when it is opened or scrolled.** The line-number
  gutter drew every line in the file on every frame, not the forty on screen, and measured
  the numbers it had no room to cache while it was drawing them. On a two thousand line
  file that was a single frame costing six seconds, with the app entirely unresponsive
  inside it; the device's own frame log recorded 6114ms, 4723ms and 3178ms while opening
  a few Python files in turn. The gutter now draws the rows the viewport can show, and
  finds their numbers by halving an index of the line starts rather than by counting from
  the top of the file. The same files now cost 505ms at worst, and the system's severe
  jank detector no longer fires at all.
- **The file tree no longer shakes when it is dragged to the top.** The sheet's contents
  were laid out at 92% of the screen, which put its expanded position a sliver below the
  top edge rather than on it. A drag that ended near there left the sheet and the tree
  inside it each trying to consume the same last few pixels, one undoing the other every
  frame. The contents are full height now, so the drag lands exactly on the sheet's own
  anchor. It still opens half way, which is measured from the sheet's height either way.
- **Re-entering the editor no longer draws the file you had last time first.** The screen
  followed the open file through an effect, which runs after the frame that opened it, so
  a file picked after backing out to the browser arrived one frame late and the previous
  one was composed and thrown away in the meantime. On a long file that discarded frame is
  a whole document laid out for nothing, and it was most of the cost of opening anything
  after walking through a few folders.

- **Rearranging the keys above the keyboard works more than once.** The row kept its
  working order in a `remember` keyed on the saved order, so writing an order back handed
  out a new state object - while the drag went on writing to the one it had closed over,
  because a gesture keeps the lambda it started with for as long as it runs. From the
  second drag onwards every crossing landed in a list nothing rendered: the row stood
  still under the finger, the held key jittered as it paid for swaps that were not on
  screen, and the whole bar rearranged itself at once when the finger came up. Moving a
  key also asks for slightly more than half a slot now, and moving it back asks the same
  again, so a thumb resting on a boundary no longer shivers a key between two places.
- **Turning line numbers back on in a very long file no longer closes the app.** The
  renderer asks for the colours of whichever line it is about to draw, and there were two
  ways to be asked for a line that had none. A file past the highlighting cap carries one
  uniform style, and the span builder adds nothing when the style has not changed - not
  even for the line it was asked about - so a hundred thousand lines built exactly one.
  Separately, the colours are collected on a background thread that abandons its copy of
  the document when a newer request arrives, so a full buffer could be coloured against a
  truncated one. Every line gets a span now, the colours are padded to the buffer's real
  length rather than the copy's, and a line asked about past the end is answered with the
  last one there is rather than by throwing in the middle of a frame.
- **Markdown tables are as wide as what is in them.** Columns were laid out at a fixed
  width per character cell regardless of content, which is not what a table is, and
  several places that had no reason to be tinted were drawing their text in the accent
  colour. Columns are measured from their contents and ordinary text is ordinary again.

- **Leaving the editor asks about every unsaved file, not just the one on screen.** The
  prompt was gated on the visible buffer, so a file edited and then switched away from was
  discarded silently on the way out. Closing a single tab already asked; this is the same
  question for the whole strip.

### Changed
- **Colouring a long file happens off the main thread.** Past about twenty thousand
  characters the scan is no longer run in the middle of composition on every keystroke;
  it runs on a background thread once typing pauses, and the previous colouring stays on
  screen until it lands. Colour is the part of an editor that can afford to be a frame
  late. The character just typed is not, and it was waiting behind a scan of the whole
  file - thirteen milliseconds of one on a desktop, and a phone is not a desktop.
- **Code is broken at the edge of the line and nowhere else.** The text stack was using
  the high-quality line breaker, which balances and hyphenates. Neither means anything in
  a monospace file, and breaking lines is the single most expensive part of laying a long
  one out.
- **The strip carries twenty files rather than twelve, and drops the least recently
  looked at rather than the oldest.** A tab is a path and a name, so a longer strip costs
  nothing; what costs memory is a file's unsaved text, and that is now what the limit is
  actually written against - roughly eight megabytes of it across everything not on
  screen. A tab holding work that is not on disk is never dropped to honour either limit,
  because that work is not the app's to throw away. Reaching the ceiling now needs several
  large files edited and left unsaved at once, rather than twelve files merely opened.

- **The editing surface draws the lines on screen rather than the whole document.** The
  editor is built on sora-editor now, with this app's own scanner, palette, key bar and
  file handling on top of it. This is what the gutter fix above was a down payment on:
  selection, the IME, undo across many lines and horizontal scrolling all cost the
  viewport instead of the file, so a three megabyte file costs about what a small one
  does - the worst frame while scrolling one is 42ms, against six seconds before any of
  this. Pinch-to-zoom, a horizontal scrollbar and smoother scrolling come with it.
  sora-editor is LGPL-2.1, whose third section permits taking it under the GPL.
- **Word wrap starts off.** Code has meaningful line ends and a wrapped line hides them.
  It is still one switch away, and the switch is remembered.
- **The three-dot menu uses switches instead of the words on and off.** A row that reads
  "Line numbers  On" tells you the state and not what tapping it does; a switch is both.
- **The splash is the name alone, centred.** The mark above it was a letter A standing in
  for a logo that does not exist yet. The shimmer across the name is unchanged.
- **The splash is on screen for 1.1 seconds rather than 1.5.** One pass of the shine is
  the whole of it, and the pass was longer than it needed to be.

- **Undo covers what you actually did.** The buffer is the editor's own, so undo spans as
  many lines as an edit touched instead of stopping at one, and a block indent or a
  comment toggle comes back in one step. Undoing back to the text that is on disk also
  clears the unsaved marker, which it did not before: the file and the buffer agree again,
  so saying otherwise was simply wrong.
- **Colours are named once, in one place.** The highlighter reports what a token *is*
  rather than what colour it should be, and a single palette turns those into colours for
  both the editor and the Markdown preview. Nothing user-visible changes today; it is what
  makes a light theme, or any other, a palette rather than a rewrite.
- **Release builds number themselves one at a time.** `versionCode` was the repository's
  commit count, so it moved by however many commits a release happened to contain. It is
  now one per release. It continues from where the old scheme left off rather than
  restarting, because a version code may never go backwards: v1.1.3 shipped as 54, so the
  next release is 55.
- **`Home` goes to the start of the line.** It used to toggle between the first non-space
  character and column zero. The editor's own line-start movement does not, and the
  toggle was not worth reimplementing on top of it.

- **The setup screen is a checklist rather than a wall.** Numbered rows with a live status
  each, a progress track in the header, commands set in the code face at a size they can be
  read at against near black, and a copy button that says Copied when it has. The buttons
  answer to what was found: while something is outstanding it offers Check again, and when
  every row passes it offers one button that says Done. It previously offered Check again,
  Open Termux and Not now to somebody who had just been told they were ready.
- **The terminal opens at half height and can be dragged to full.** It went straight to
  full because the prompt was a bar pinned under the transcript, which a half sheet pushed
  below the fold. The prompt is not a bar any more, so the sheet can behave like a sheet
  and leave the file underneath it in view.
- **Commands are typed in the terminal, on its last line.** The input was a field docked
  over the keyboard, separate from the output it produced, which read as a search box that
  happened to run things. It is now one more row of the console, in the console's own face
  and size, sitting where the next line of output will appear. The view follows down to it
  rather than stopping one line short.
- **The terminal draws in Hack, not the editor's face.** The two are read differently:
  editor text is scanned in blocks with syntax colour carrying much of the meaning, while
  terminal text is a wall of one colour where every character stands alone, often smaller
  and often not one anybody chose to type. Hack descends from Bitstream Vera by way of
  DejaVu, which is what desktop terminals have used for twenty years. Rows are given more
  air and the default size goes from 12 to 13.

- **Code is set in JetBrains Mono.** The editor, the gutter, the previewer and the setup
  commands all used the platform's monospace, which varies by vendor and
  draws 0 like O and 1 like l. Bundled rather than downloaded, under the SIL Open Font
  License, which is compatible with the GPL.
- **The command history file always has something in it.** An empty file opened in the
  editor is indistinguishable from a button that did nothing, so it carries a note when
  there is no history yet. Lines opening with `#` are not offered back as commands, so the
  note, and anything written next to it, stays out of the recall list.

- **A file can be edited up to four megabytes rather than two.** Editing is no longer
  bounded by what a Compose text field could lay out, so the limit is about memory now:
  four megabytes to edit, sixteen to open read-only.

### Added
- **Files Alchemy does not edit open in the app that does.** Tapping a picture, a video,
  an archive, a PDF, an installer, an Office or OpenDocument file, or a page hands it to
  whatever the device already opens it with, instead of loading it and reporting that it
  is not text. Code and plain text still open in the editor.

  HTML is the deliberate case: it is source and it is also a page, so a tap renders it in
  a browser and **Open in editor**, on the entry's own press-and-hold menu, edits it. That
  menu also carries **Open with another app** for everything else, so the routing a tap
  chooses is never the only way in. When a text-shaped name turns out to hold binary
  anyway, the editor offers the same hand-off rather than stopping at a message.

  The file is passed as a `content://` URI through a `FileProvider`, read-only and for as
  long as the receiving app is on screen, because since API 24 a `file://` URI crossing to
  another process throws.

- **Auto-pairing, and block edits that know what a line is.** Typing an opening bracket or
  quote closes it and puts the caret between the halves; Enter after a line that opens a
  block indents the new line to match. The key bar indents, dedents and toggles comments
  across a whole selection, duplicates a line and deletes one, each as a single undo step
  rather than as the several edits it is made of.
- **The mark on the launch screen is the app icon.** The starting window drew its own
  copy of the old letter A, which no longer matched anything.

- **The file access screen shows what is being asked for.** It led with the app's own
  mark, which tells the reader who is asking at a moment when they already know. It now
  leads with the permission's icon in a tonal container, which is the pattern the system's
  own permission screens use. The last of the old letter A artwork goes with it: the
  launcher, the launch screen and this screen were three separate drawings of the mark,
  and there is now one.

- **A launcher icon built from the brand mark.** Adaptive, so the launcher masks it into
  whatever shape the device uses rather than showing a rectangle inside that shape: the
  foreground is the mark on transparency, sized inside the 66dp safe zone, over a near
  black background layer. Ships a themed variant for Android 13, which the launcher tints
  itself, and plain square and round bitmaps for API 24 and 25, which have no adaptive
  icons at all. The starting window the system draws before the first frame uses that same
  foreground layer rather than a second copy of the mark, so the icon on the home screen
  and the icon on the launch screen cannot drift apart.
- **The GPL-3 text is in the repository.** The README's badge and its licence section both
  pointed at a `LICENSE` that was not there. Taken verbatim from gnu.org.

- **Alchemy is offered for code files sent from other apps, and opens them directly.**
  It previously claimed only `text/*`, which is not what a file manager sends: Android's
  own type table reports most source extensions as `application/octet-stream`, so a `.kt`
  or a `.rs` never reached the list. It now claims the text formats registered outside
  `text/` as well, and claims `octet-stream` bounded by 57 source extensions rather than
  outright, so it is not offered as a handler for every unknown binary on the device.
  Files arriving as `content://` are resolved through the external storage, downloads and
  media providers rather than only the first of those. The activity is `singleTask`, so
  opening a second file while Alchemy is running reuses the running editor instead of
  building another one, rather than paying for a second activity, theme inflation and
  first composition.
- **Reading a large file reports how far it has got.** Above 256 KB the file is decoded in
  64 KB chunks and the loader shows the name, the size and a percentage taken from bytes
  actually consumed off the stream. The chunked path is also interruptible, so backing out
  of a large file stops the read rather than letting it run to completion in the
  background, and opening another file cancels the one before it instead of racing it.

- **The app comes back from a crash knowing what it was.** An uncaught exception on
  Android ends the process behind a system dialog that names nothing, which leaves the one
  person who knows what they were doing with no way to say it. The last thing to run now
  writes the failure down - version, device, thread, trace - and the next launch shows it
  once, with a button that copies it. It deliberately does not try to continue through the
  failure: after an error nothing anticipated, the text held in memory may be damaged, and
  writing that back over a real file is worse than closing. The report is taken off the
  device as soon as it has been read rather than when the dialog is dismissed, so it
  cannot survive being swiped away and greet a later launch as though the app had just
  crashed again.

### Documentation
- `docs/SORA-MIGRATION.md` records the whole migration: why the previous approach could
  not be made fast, what was built, what was measured, the two draw-thread crashes and
  why `SafeSpans` is a net rather than a cure, and the gaps left open.
- `docs/MANUAL-TEST-PLAN.md` lists every check to run by hand, per phase, with expected
  against actual results.
- The README has the banner and the assets it points at, and `NOTICE` records sora-editor
  and the LGPL-2.1 section 3 basis for conveying it under the GPL.

## [1.1.2] - 2026-09-04

### Fixed
- **Picking a file from the editor's tree now actually opens it.** The editor captured its
  file once when the screen was created, so choosing another one from the tree, from a
  terminal result, or from anywhere else changed the path and nothing else: the old file
  stayed on screen with no sign that anything had been asked for. The screen now follows
  the file it is given, while still holding the last one long enough to draw its own
  closing animation, which is what the original capture was there to solve.

### Added
- **A row of open files under the editor bar.** Every file opened this session sits there
  as a name with a cross, scrolling sideways, the current one filled in. Tapping one goes
  to it, the cross closes it, and closing the file on screen falls to its neighbour.
  It appears from the second file onwards: with one file open it would be the name from
  the bar, repeated directly under the bar.
- **Unsaved edits survive switching files.** A file with unwritten changes keeps its
  buffer when you move to another tab and hands it back when you return, so a tab is a
  place your work is rather than a bookmark that discards it. Clean files are re-read from
  disk instead, which is what picks up a change made to them from outside. Closing a tab
  with unwritten edits asks first, and a tab holding them shows a dot where its cross
  would be.

### Changed
- **The file tree opens half way** rather than filling the screen, and drags the rest of
  the way up. It is something you glance at to find one file, and taking the whole screen
  to do that hid the file you came from while you looked for the next one.

## [1.1.1] — 2026-00 - 04

### Changed
- **The app is now Alchemy.** New name everywhere it is shown, a new mark, and a new
  application id: `com.sibtainocn.alchemy`, with `.ps` still appended for the Play Store
  flavour. An application id is what Android uses to tell one app from another, so a
  device holding the old build will see this as a separate install rather than as an
  update to it. There is no migration path around that, and none is pretended here.
- **Launch goes straight into the opening animation.** The system splash hands over on
  the first frame onto a pure black screen with the mark and the name, and a single band
  of light crosses the letters over 1.5 seconds. It replaces the spinner that used to sit
  there, which said "wait" when there was nothing to wait for.
- **The app icon is the letter A**, stroked white on the app's own near-black, with the
  crossbar raised so its counter is a triangle. It ships as an adaptive icon with a
  monochrome layer, so Android 13 and later can tint it with the system theme.
- **Files that are not source get a real icon.** A page with its corner turned back,
  coloured by kind and carrying a mark for it: ruled lines for text, a frame and a horizon
  for images, a play triangle for video, a note for audio, a zip pull for archives, and
  the format's name for PDFs, packages and binaries. Source files keep the extension tag,
  because in a folder of thirty Python files the extension is the part worth reading, but
  the tag now sits on a graded plate with a hairline edge rather than a flat wash.
- **The date on each row is smaller than the two lines beside it.** It is what you check
  after finding the row, not what should be competing to be read first.
- **The filename in the editor bar sits on a plate.** It was always the way into
  something, and nothing about bare title text said so.

- **CI debug APKs are dropped after a day**, down from a fortnight. One is only ever
  wanted on the day of the push that produced it, and the next push rebuilds it. Release
  APKs are untouched by this: they go out as GitHub Release assets, which are kept for as
  long as the release is.

### Added
- **Cut, copy and paste, at the filesystem level.** Long-press any file or folder and it
  can be picked up. A strip appears above the list saying what is being carried, with an X
  that puts it down again, and the + button in the corner becomes Paste for as long as
  something is waiting. A move inside one volume is a rename, so a folder of any size
  arrives instantly; across volumes the bytes travel through the kernel by channel
  transfer, falling back to a buffered copy on the volumes that refuse it. The buffer is
  sized against what the heap can actually spare, progress is throttled so the copy spends
  its time copying, and a run that fails part way removes only what it wrote.
- **A conflict dialog with Skip, Replace and Keep both.** Both sides are shown with their
  size and date, because the question is never really "replace?" but "which of these two
  did I mean". Two folders of the same name merge rather than displacing each other, and
  each clash inside is asked about in turn until "do this for everything else" is ticked.
  Dismissing the prompt stops the whole transfer, which is the only reading of a dismissed
  prompt that cannot lose anything.
- **Move to**, which asks for a destination instead of going through the clipboard. A
  folder browser with the confirm button naming where you are standing, and that button
  disabled with a reason if you have walked inside the thing you are moving.
- **Pin to top.** Any file or folder can be lifted to the head of its list with a pin mark
  beside it, and unpinned the same way. Pins live in the app's own storage rather than
  being worked out from the filesystem, which has nowhere to record them, so they cost one
  read at startup and nothing at all when a folder is opened. A pin follows its file
  through a rename and goes when the file does.
- **A file tree behind the filename in the editor.** Tapping the name opens the folder
  around the open file as a tree: folders expand in place rather than pushing a new
  screen, so a file's neighbours and its parent's neighbours are visible at once, and
  tapping a file swaps what the editor is holding. Dotfiles are listed, because a
  `.gitignore` is exactly the kind of file you open from there. What the file itself is,
  its path and size and line count, is still one row down in the menu.

## [1.1.0] — 2026-08-25

### Added
- **Command history in the terminal.** An up arrow in the header writes the previous
  command into the prompt rather than running it, so it can be edited first, and tapping
  again walks further back. Every command typed is kept in a plain text file in the app's
  own private storage, so the history survives a restart; the terminal menu opens that
  file in the editor like any other text file, and clears it in one action. Bounded to the
  last 500 commands, because a terminal used for a year is a file nobody meant to keep.
- **A Logs button on the language installer**, which puts everything the package manager
  printed into the terminal, unedited. A failed install is a few hundred lines of apt with
  one useful line somewhere in it, and a dialog has room for one line.
- **An options menu in the terminal**: text size, whether each command reports how long it
  took, and copying the whole session out at once. Both settings are remembered and kept
  apart from the editor's own text size.
- **The filename opens a sheet about the file.** Full path, folder, size, modified, type,
  lines, characters, and whether it is writable, all selectable so any of it can be
  copied. A path is the thing people most often need out of an editor and least often
  have anywhere to read.
- **Terminal setup and language install are reachable from the file browser too**, through
  a new overflow menu that also copies the current folder's path. Setting up a terminal
  has nothing to do with any one file, so it no longer requires opening one.
- **A stop control in the terminal**, for when a command is taking longer than it should.
  It stops the waiting rather than the command, which the note it leaves says out loud:
  there is no way to reach into the runner and kill a process.
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
- **Caret keys.** Arrows, home and end, with Shift to extend the selection - none of which
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
- **Nothing the terminal sent ever came back.** The extra naming the callback was
  `com.termux.RUN_COMMAND_RESULT_PENDING_INTENT`; Termux looks for
  `com.termux.RUN_COMMAND_PENDING_INTENT`, with no RESULT in it, unlike every key inside
  the bundle it delivers. Nothing went wrong anywhere: Termux took each command, ran it,
  found no callback under the name it looks for, and dropped the output. Confirmed against
  the extras in Termux 0.119's own dex on a device, not from memory.
- **Every successful command was reported as a failure.** Termux numbers success as `-1`
  in its error field, not `0`, so treating a non-zero value as a problem condemned every
  command that worked.
- **The reply is delivered to a manifest receiver** rather than one registered at runtime.
  A PendingIntent aimed at an explicit component has no question to answer about whether
  it is exported, and reaches the app whether or not it is in memory.
- **Every command hung for a minute and then said "Timed out".** Termux refuses a command
  from an app it has not been told to trust by posting a notification of its own and never
  replying, so the terminal sat waiting for an answer that was never coming while the real
  explanation was on the notification shade. The channel is now tested with an `echo`
  before the terminal opens, and silence for seven seconds is read as the refusal it is
  and answered with the step that fixes it.
- **The setup command could not repair a file that was already wrong.** It appended the
  property only when it was absent, so a `termux.properties` carrying it commented out,
  spaced differently, or set to false was left exactly as it was. Every form of the line
  is now removed before one clean one is written, and the command prints `done` when it
  worked.
- **The terminal opened at half height with its prompt below the fold**, which is the one
  part of a terminal that has to be reachable the moment it opens. It opens full height,
  the prompt sits on top of the keyboard, and it carries a placeholder rather than an
  empty line.
- **Undo and redo sat lower than the icons beside them.** Their arc swung two units below
  the grid everything else is drawn on. Redrawn to the same optical centre, and the bar's
  controls are tighter so seven of them and a filename fit a phone.
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
- Single-list browser over internal storage - folders and files together, tap a folder to
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
- Syntax highlighting via a single-pass character scanner - correct around strings inside
  comments, comments inside strings, and Python's triple-quoted blocks.
- Languages: Python, Kotlin, Java, JavaScript/TypeScript, C/C++/Rust/Go/Swift, shell,
  JSON, XML/HTML, Markdown, and config formats.
- Monokai colours: pink keywords and operators, green definitions, cyan italic types,
  purple literals - on a true-black ground.
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
- Undo/redo with coalesced steps - a run of typing is one step, not one per character.
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
  press states, and a shape-morphing loader built from `RoundedPolygon` geometry - the
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
