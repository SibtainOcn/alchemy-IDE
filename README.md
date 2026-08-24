# Hazel IDE

A file browser and a code editor for Android. Deliberately nothing else: no plugins, no
build tools, no code execution, and no network permission at all.

Built for reading and editing code on a phone — opening a Python file to fix a line,
checking a README, editing a config on the device it runs on.

## What it does

**Browse.** One list of folders and files. Tap a folder to go in, tap a file to open it,
`..` to go back up. Breadcrumbs jump to any level, scroll position is remembered per
folder, and long-pressing anything offers rename, copy path and delete.

**Edit.** Syntax highlighting for Python, Kotlin, Java, JavaScript/TypeScript,
C/C++/Rust/Go/Swift, shell, JSON, XML/HTML, Markdown and config formats — Monokai colours
on a true black background.

The typing gets out of your way: brackets and quotes pair themselves, typing a closer
steps over the one already there, backspace between an empty pair removes both halves, and
newlines carry the indent — including opening a block after a Python colon.

A key bar above the keyboard supplies what a phone IME has no way to offer: Ctrl, Shift,
Caps and Tab, arrow keys, and every symbol code needs. Long-press any key and drag it into
the order you want; it is remembered per language.

**Preview.** Markdown renders with tables, task lists, quotes and code blocks — the code
highlighted by the same engine as the editor, so a snippet reads like the file it came
from. Toggle between rendered and raw source at any time.

## Design

True black (`#000000`), one accent (`#0FCBE8`), and a Material 3 expressive motion
vocabulary: spring transitions, directional navigation, and a shape-morphing loader drawn
from real polygon geometry rather than a spinner.

Icons are hand-drawn on a 24-unit grid. The Material icon library is not a dependency.

## Building

```bash
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK. `minSdk` 24, `compileSdk` 37.

To run the tests:

```bash
./gradlew testSummary
```

## Releases

Grab an APK from [Releases](../../releases). Install the `arm64-v8a` build unless you know
your device needs another; `universal` works everywhere and is slightly larger.

Builds are named `HAZEL-IDE-<CHANNEL>-v<version>-<abi>.apk`, where the channel is `STABLE`,
`BETA` or `DEBUG`.

## Privacy

Hazel declares no `INTERNET` permission. Nothing it reads can leave the device. It asks
for All files access once, because a file manager that cannot see your files is not one.

## Licence

Not yet chosen.
