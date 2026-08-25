# The distribution split

Hazel IDE is built twice from one codebase.

| Flavour | Ships to | Application id | Can run code |
|---|---|---|---|
| `fdroid` | GitHub releases, F-Droid | `dev.hazel.code` | Yes, by asking Termux |
| `playstore` | Google Play | `dev.hazel.code.ps` | No |

Everything a person uses the app for is identical in both: the editor, the file browser,
the syntax highlighter, undo, the Markdown preview. The only difference is whether the
build can hand a file to something that runs it.

## Why

Termux stopped being distributable through Google Play years ago, and the copy still
listed there is frozen far behind and cannot accept commands from other apps. An editor
that depends on Termux therefore cannot promise a working Run button to someone who found
it on Play. Rather than ship one build that half works, there are two: the GitHub build
does everything, and the Play build is an editor that does not claim to run anything.

This is what product flavours are for, and it is not a policy risk. Only one build is
ever submitted to Google Play, so there are never two similar listings competing there.

## The rule

> Shared code may never name Termux, check for a package, or assume a runner exists.
> It asks `ExecutionProvider` and reacts to the answer.

If you find yourself writing `if (isFdroidBuild)` anywhere in `src/main`, the thing you
are writing belongs behind the interface instead.

## The folders

```
app/src/
├── main/                        shared, compiled into BOTH builds
│   └── java/dev/hazel/code/
│       ├── ui/ ...              editor, explorer, preview, key bar
│       └── exec/
│           ├── ExecutionProvider.kt   the interface, plus Readiness and RunResult
│           └── Runtime.kt             which languages run, and how
│
├── fdroid/                      ONLY in the GitHub and F-Droid build
│   ├── AndroidManifest.xml            RUN_COMMAND permission, <queries> for com.termux
│   └── java/dev/hazel/code/exec/
│       ├── Execution.kt               provider() returns the Termux implementation
│       ├── Termux.kt                  Termux's published contract, in one place
│       └── TermuxExecutionProvider.kt the intent plumbing
│
├── playstore/                   ONLY in the Google Play build
│   └── java/dev/hazel/code/exec/
│       └── Execution.kt               provider() returns a provider that runs nothing
│
├── test/                        shared unit tests, run against both
└── testFdroid/                  unit tests for the Termux code only
```

## How the wiring works

There is no runtime switch. Both flavours declare the same object at the same fully
qualified name, and Gradle compiles exactly one of them:

```kotlin
// src/fdroid/.../exec/Execution.kt
object Execution {
    fun provider(context: Context): ExecutionProvider = TermuxExecutionProvider(context)
}

// src/playstore/.../exec/Execution.kt
object Execution {
    fun provider(context: Context): ExecutionProvider = NoExecutionProvider
}
```

Shared code calls it without knowing which one it got:

```kotlin
val execution = Execution.provider(context)
if (execution.supported) {
    // show the Run button
}
```

When Gradle builds `fdroid` it compiles `src/main` plus `src/fdroid`, and never opens
`src/playstore`. The other variant's code is not disabled at runtime, not shrunk away by
R8, not present in a dead branch. It does not exist in that APK. The build system
enforces the separation, so keeping it right is not a matter of discipline.

## Where does my change go

| What you are adding | Where |
|---|---|
| Anything to do with editing, browsing, highlighting, undo, preview | `src/main` |
| A new language the Run button supports | `src/main`, in `Runtime.kt` |
| A new capability the runner must offer | `src/main`, on the `ExecutionProvider` interface |
| Termux intents, Termux constants, Termux setup instructions | `src/fdroid` |
| A permission only the runner needs | `src/fdroid/AndroidManifest.xml` |
| A bundled interpreter for the Play build, if that day comes | `src/playstore` |
| A test of shared code | `src/test` |
| A test of Termux code | `src/testFdroid` |

UI that reacts to `Readiness` belongs in `src/main`, not in the flavour. The Play build
simply never reaches those branches, because its provider reports `Unsupported` and the
entry points stay hidden.

## Gradle tasks

Flavours rename most tasks. The variant name goes between the verb and the build type.

| Before | Now |
|---|---|
| `:app:compileDebugKotlin` | `:app:compileFdroidDebugKotlin`, `:app:compilePlaystoreDebugKotlin` |
| `:app:testDebugUnitTest` | `:app:testFdroidDebugUnitTest`, `:app:testPlaystoreDebugUnitTest` |
| `:app:lintDebug` | `:app:lintFdroidDebug`, `:app:lintPlaystoreDebug` |
| `:app:assembleRelease` | `:app:assembleFdroidRelease`, `:app:assemblePlaystoreRelease` |

`:app:testSummary` and `:app:verifyTestFloor` hang off `testFdroidDebugUnitTest`. The
shared tests are the same in both flavours, so running them twice would only cost time,
and the F-Droid variant is the one with extra tests of its own.

`:app:packageReleaseApks` builds `assembleFdroidRelease` and copies its APKs out under
release names. The Play Store build never goes through it: that one is uploaded as a
bundle from its own variant.

## Checking the separation yourself

After a build, these should hold. The first three must return zero.

```bash
PS=$(find app/build/intermediates/built_in_kotlinc -path "*playstoreDebug*" -name classes -type d | head -1)

find "$PS" -name 'Termux*.class' | wc -l          # 0
grep -rl 'com\.termux' "$PS" | wc -l              # 0
grep -c termux app/build/intermediates/merged_manifest/playstoreDebug/*/AndroidManifest.xml
```

```bash
FD=$(find app/build/intermediates/built_in_kotlinc -path "*fdroidDebug*" -name classes -type d | head -1)
find "$FD" -name 'Termux*.class' | wc -l          # more than 0
```

## Things that must not happen

- The `fdroid` flavour must never gain an `applicationIdSuffix`. It is the build already
  released as `dev.hazel.code`, and changing its id would turn the next update into a
  second, separate app for everyone who has it installed.
- The Play Store build must never declare `com.termux.permission.RUN_COMMAND`. Keep it in
  the flavour manifest.
- Shared code must never import anything from `src/fdroid`. It will compile for the
  F-Droid variant and break the Play Store one, which is a failure the pull request CI is
  set up to catch because it compiles both.
