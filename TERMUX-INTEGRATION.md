# Running code through Termux

How Hazel IDE runs a file without shipping a single interpreter, what it costs, and every
place a change in Termux would land.

Written after getting it wrong twice on a real device. The two mistakes are documented
here on purpose, because both were silent: nothing crashed, nothing logged an error, and
the app simply did not work.

---

## 1. The shape of it

Hazel never executes anything. Termux does.

```
Hazel IDE                      Android                      Termux
    |                             |                            |
    |-- startForegroundService -->|                            |
    |   com.termux.RUN_COMMAND    |--- RunCommandService ----->|
    |   + a PendingIntent         |                            |-- bash -c "python x.py"
    |                             |                            |
    |<-------- broadcast to TermuxResultReceiver --------------|
    |          stdout, stderr, exitCode                        |
```

Hazel's APK stays about 1.5 MB. Every language the user installs lives inside Termux, and
Hazel does not know or care what is in there beyond asking whether a command exists.

Termux is not a dependency in the Gradle sense. It is a separate app the user installs
themselves, and it can be missing, wrong, out of date, or refusing to talk. Most of the
code in `exec/` is about finding out which.

---

## 2. Where everything lives

Only the F-Droid and GitHub build can run code. The Play Store build does not have this
code compiled into it at all. See `docs/DISTRIBUTION-SPLIT.md` for that split.

```
app/src/main/java/dev/hazel/code/exec/     shared, in both builds
  ExecutionProvider.kt    the interface, Readiness ladder, RunRequest, RunResult
  Runtime.kt              Python, C, Go: packages, probe, command, shell quoting
  Console.kt              cd handling, path resolution, line types
  InstallPlanner.kt       what to install, in what order
  SetupGuide.kt           the shape of the setup instructions

app/src/fdroid/java/dev/hazel/code/exec/   the Termux build only
  Termux.kt                    every string Termux publishes, in one place
  TermuxExecutionProvider.kt   the intent plumbing
  TermuxResultReceiver.kt      where the answer arrives
  Execution.kt                 hands the provider to shared code

app/src/fdroid/AndroidManifest.xml         permission, queries, receiver
```

Shared code never names Termux. It asks `ExecutionProvider` and reacts to what comes back.

---

## 3. The two mistakes, and why they were invisible

### 3.1 The callback extra has no RESULT in it

Every key **inside** the bundle Termux sends back is named `result`-something. The extra
that carries the callback **into** Termux is not:

```kotlin
// Wrong. Termux never looks for this.
"com.termux.RUN_COMMAND_RESULT_PENDING_INTENT"

// Right.
"com.termux.RUN_COMMAND_PENDING_INTENT"
```

What this looked like on a device: every command hung for the full 60 second timeout and
then reported "Timed out". Nothing failed anywhere. Termux accepted the intent, ran the
command correctly, looked for a callback under the name it knows, found nothing, and threw
the output away. There is no error path for this, because from Termux's side nothing went
wrong.

It was found by pulling Termux's own `classes.dex` off the device and reading the strings:

```bash
adb shell "cd /data/app/*/com.termux-*/ && unzip -o -q base.apk 'classes*.dex' -d /data/local/tmp/tdex"
adb shell "grep -a -o '[a-zA-Z._]*PENDING_INTENT[a-zA-Z._]*' /data/local/tmp/tdex/classes.dex | sort -u"
```

**Do this again rather than trusting documentation or memory** if a future Termux stops
answering.

### 3.2 Termux numbers success as -1

Termux's `Errno` type uses `-1` for success and positive numbers for real failures. Reading
the field with a default of `0` and treating "not zero" as an error marks every successful
command as failed:

```kotlin
// Wrong: every working command becomes a failure.
val err = bundle.getInt(RESULT_ERR, 0)
if (err != 0) return failure()

// Right.
val err = bundle.getInt(RESULT_ERR, Termux.ERR_SUCCESS)   // -1
if (err > 0) return failure()
```

### 3.3 A runtime receiver was not enough

The first version registered the result receiver at runtime with `RECEIVER_NOT_EXPORTED`.
It was replaced with a receiver declared in the manifest, targeted by an explicit
component:

```kotlin
PendingIntent.getBroadcast(
    context,
    id,                                                    // the request id, also the request code
    Intent(context, TermuxResultReceiver::class.java)
        .putExtra(TermuxResults.EXTRA_REQUEST_ID, id),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
)
```

A PendingIntent aimed at an explicit component has no question to answer about export
rules, and it arrives whether or not the app is still in memory. This did not turn out to
be the cause of the outage, but it removes a variable permanently and it is the right
shape regardless.

---

## 4. The setup the user has to do, and why none of it can be automated

| Step | Why it cannot be done for them |
|---|---|
| Install Termux from GitHub, not Google Play | The Play copy is frozen years behind and cannot take commands from other apps. It cannot be updated into one that can. |
| Grant `com.termux.permission.RUN_COMMAND` | A dangerous-level permission Termux declares. Requires a runtime prompt. |
| `allow-external-apps=true` in `~/.termux/termux.properties` | Lives inside Termux's private storage. Nothing outside Termux can write it, and until it is set there is no channel to ask Termux to write it either. |
| `termux-setup-storage` | Grants Termux access to shared storage, where Hazel's files live. Raises its own Android permission dialog. |
| Restart Termux | `termux-reload-settings` usually suffices, but the service that takes commands sometimes holds the old answer. |

The command Hazel offers for the third step repairs a broken file rather than assuming an
empty one:

```bash
mkdir -p ~/.termux && touch ~/.termux/termux.properties \
  && sed -i '/allow-external-apps/d' ~/.termux/termux.properties \
  && echo 'allow-external-apps=true' >> ~/.termux/termux.properties \
  && termux-reload-settings && echo done
```

An earlier version appended the line only when `grep` did not find it, which left a file
carrying the property commented out, spaced differently, or set to `false` exactly as it
was.

---

## 5. Detection, and why silence is the signal

Termux reports a refusal by **posting its own notification and never replying**. There is
no error to read. The absence of an answer is the only evidence, so it is waited for once,
deliberately, rather than discovered by every command the user types.

`TermuxExecutionProvider.verify()` runs `echo hazel-ok` with a seven second budget. A reply
means the whole channel works. No reply means `Readiness.RunnerNotAnswering`, which on
screen says both of its likely causes: refusing external apps, or never opened since
install.

The same call wakes Termux, because a background command starts its service whether or not
the app was running. There is no separate "launch Termux" step.

The ladder, in the order a person climbs it:

| Rung | How it is found |
|---|---|
| `RunnerMissing` | PackageManager, needs `<queries>` from Android 11 |
| `RunnerFromAppStore` | `getInstallSourceInfo`, API 30+, falls back below |
| `RunnerTooOld` | version code below 118 |
| `PermissionMissing` | `checkSelfPermission` |
| `RunnerNotAnswering` | the handshake times out |
| `StorageUnreachable` | `test -d <dir>` comes back empty |
| `Ready` | all of the above passed |

The cheap rungs are cached for three seconds so that typing `ls` does not pay for a
cross-process package lookup first.

---

## 6. What a command is, and what it is not

Every command is **a separate process with no terminal attached**. This is the single fact
that shapes everything above it.

| | Works | Does not |
|---|---|---|
| `ls`, `git status`, `python x.py`, `go run .` | yes | |
| `cd src` remembered next command | | tracked by Hazel, not by a shell |
| `export FOO=1`, an activated venv | | nothing persists between commands |
| A script calling `input()` | | **blocks forever** |
| `vim`, `htop`, anything full-screen | | no terminal to draw on |
| Live output while running | | arrives in one piece at the end |
| Coloured output | | no ANSI parsing yet |
| Output above roughly 1 MB | | cut, and the terminal says by how much |

### Measured on a device (Realme RMX3151, Android 13, Termux 0.119.0-beta.3)

| Command | Time |
|---|---|
| `echo hazel-ok` (the handshake) | 80 ms |
| `ls` | 162 ms |
| a 330 line Python script | 3.2 s |
| `pkg install -y python` | 103 s, exit 0 |
| a script calling `input()` | **never returns** |

The Run button and the terminal use the same path, so a file runs at the same speed either
way. A slow command is a slow command, not a slow terminal.

### The `input()` problem in detail

A script that asks for input does not fail. It blocks, because stdin is an open pipe
nobody will ever write to. Hazel gives up waiting after its timeout, and **the process
stays alive inside Termux** until something kills it. Hazel's stop control stops the
waiting, not the command, and says so rather than implying otherwise.

Fixing this properly needs a persistent session rather than one process per command. The
approach that would work without adding the `INTERNET` permission is a file bridge: one
long-lived shell inside Termux reading from a file Hazel appends to, writing to a file
Hazel tails.

```bash
tail -n +1 -f ~/storage/shared/.hazel/in.sh | bash >> ~/storage/shared/.hazel/out.log 2>&1
```

That buys real session state, streaming output, and `input()` that works. It costs a
background process to manage and needs `tail -f` verified against FUSE storage. A real TTY
is not reachable at all without a socket, and a socket needs `INTERNET`, which would end
the app's "no network permission" promise.

---

## 7. Languages

Three, and deliberately three: Python, C, Go. All work on a phone without qualification.

`Runtime.kt` holds, for each: the Termux packages, the command that proves it is installed,
the file extensions, an approximate download size, and the shell line that runs a file.

C is the interesting one:

```kotlin
C -> "clang $file -o \"\${TMPDIR:-/data/data/com.termux/files/usr/tmp}/hazel-run\" && ..."
```

The compiled program **cannot** be written next to the source. Android mounts shared
storage non-executable, so `./a.out` beside the file compiles fine and is then refused at
the moment it runs. It goes into Termux's own temporary directory instead, with a literal
fallback because a process started by a service does not reliably inherit `TMPDIR`.

Node, Ruby, Java, HTML and CSS are left out on purpose. Node and Ruby pull large dependency
trees the moment a real project appears, Java needs a JDK and a class name that matches the
file, and HTML is rendered rather than run.

---

## 8. If Termux changes its intent API

It is another project, with its own release schedule, and this integration depends on
strings it publishes rather than on a versioned API. A Termux update **can** break it.

**Everything Termux owns is in one file: `app/src/fdroid/java/dev/hazel/code/exec/Termux.kt`.**
That is the whole point of that file existing. If Termux changes something, that is where
the change goes, and in most cases nowhere else.

| If Termux changes | Change this | Also check |
|---|---|---|
| An extra name (`RUN_COMMAND_PATH`, `_ARGUMENTS`, `_WORKDIR`, `_BACKGROUND`, `_PENDING_INTENT`) | the matching `EXTRA_*` constant in `Termux.kt` | nothing else |
| The service class or action | `RUN_COMMAND_SERVICE`, `ACTION_RUN_COMMAND` | nothing else |
| A result bundle key (`stdout`, `stderr`, `exitCode`, `err`, `errmsg`) | the matching `RESULT_*` constant | `resultOf()` if a key's meaning changes |
| The success value of `err` | `ERR_SUCCESS` | the `termuxError > 0` test in `resultOf()` |
| Install paths (`/data/data/com.termux/files/...`) | `BIN_DIR`, `BASH`, `HOME` | `Runtime.TERMUX_TMP` in shared code |
| The permission name | `PERMISSION_RUN_COMMAND` | `app/src/fdroid/AndroidManifest.xml` |
| The package name | `PACKAGE` | the `<queries>` entry in the flavour manifest |
| The wording of the external-apps refusal | `rejectedForExternalApps()` | nothing else |
| `BACKGROUND` replaced by `RUNNER` | add the extra in `dispatch()` | `TermuxExecutionProvider.dispatch()` |
| The minimum usable version | `MINIMUM_VERSION_CODE` | nothing else |

Two things outside `Termux.kt` know anything about the protocol, and both are small:

- `TermuxExecutionProvider.dispatch()` builds the intent from those constants.
- `TermuxExecutionProvider.resultOf()` reads the bundle from those constants.

### How to diagnose the next breakage

The provider logs both sides under the tag `HazelTermux`:

```bash
adb logcat -c
# then use the app
adb logcat -d | grep HazelTermux
```

A healthy pair of lines:

```
D/HazelTermux: request 5: echo hazel-ok
D/HazelTermux: result for request 5: exit=0 err=-1 stdout=9 chars
```

A request with no matching result means Termux took the command and did not answer, which
is the signature of both mistakes in section 3. When that happens, read the extras out of
the installed Termux's dex with the commands in 3.1 and compare them against `Termux.kt`.

`TermuxResultReceiver` also logs a reply that arrives with nobody waiting, which
distinguishes "no answer came" from "an answer came too late".

---

## 9. Tests

The parts that can be tested without a device are, and they are the parts that were wrong
in ways a test could have caught:

- `RuntimeTest` — extension mapping, shell quoting of paths with spaces and quotes, the C
  command compiling into `TMPDIR`, every runtime having packages and a probe.
- `ConsoleTest` — `cd` recognition, path resolution including `..` past the root, tilde,
  compound commands left alone, timing summaries.
- `InstallPlannerTest` — skipping what is present, smallest first, deduplicating packages,
  extracting the useful line from apt output.
- `TermuxReadinessTest` (F-Droid only) — the ladder in order, the Play Store copy called
  out specifically, the setup line repairing rather than appending, the handshake being
  instant.

The protocol itself cannot be unit tested, because the other side of it is an app. That is
why section 8 exists.
