package dev.hazel.code.exec

/**
 * A language Hazel knows how to run, and what has to be installed to run it.
 *
 * Three languages, chosen because all three work on a phone without qualification. The
 * list is deliberately short: a Run button that works for what it offers is worth more
 * than one that offers everything and fails at half of it. Anything not listed here is
 * still editable, still highlighted, and simply has no Run button.
 *
 * Left out on purpose, and why:
 *
 * - Node and Ruby install cleanly but pull large dependency trees the moment a real
 *   project is involved, and a single-file run is rarely what anyone wants from them.
 * - Java needs a JDK, a compile step and a class name that matches the file.
 * - HTML and CSS are not run at all; they are rendered, which is a different feature.
 */
enum class Runtime(
    val label: String,

    /**
     * Termux packages to install, in order.
     *
     * More than one where the language needs a toolchain rather than an interpreter: C
     * cannot get from source to a program without an assembler and a linker, which is
     * what `binutils` carries.
     */
    val packages: List<String>,

    /** The command whose presence on PATH proves the runtime is usable. */
    val probe: String,

    /** Lower case, no leading dot. */
    val extensions: Set<String>,

    /** Roughly what installing this costs, to show before the download starts. */
    val approximateDownload: String,
) {
    PYTHON(
        label = "Python",
        packages = listOf("python"),
        probe = "python",
        extensions = setOf("py"),
        approximateDownload = "~50 MB",
    ),

    C(
        label = "C",
        packages = listOf("clang", "binutils", "make"),
        probe = "clang",
        extensions = setOf("c", "h"),
        approximateDownload = "~120 MB",
    ),

    GO(
        label = "Go",
        packages = listOf("golang"),
        probe = "go",
        extensions = setOf("go"),
        approximateDownload = "~250 MB",
    );

    /** The one line that installs this runtime, for the user to copy or for us to send. */
    val installCommand: String get() = "pkg install -y ${packages.joinToString(" ")}"

    /**
     * The shell line that runs [path].
     *
     * C compiles to Termux's own temporary directory rather than next to the source. A
     * program written into shared storage cannot be executed there: Android mounts that
     * filesystem non-executable, so `./a.out` beside the file would be denied no matter
     * who compiled it. `$TMPDIR` is inside Termux's private storage, where execution is
     * allowed.
     */
    fun commandFor(path: String): String {
        val file = ShellQuote.single(path)
        return when (this) {
            PYTHON -> "python $file"
            GO -> "go run $file"
            C -> {
                val binary = "\"\$TMPDIR/hazel-run\""
                "clang $file -o $binary && $binary"
            }
        }
    }

    companion object {
        /**
         * The runtime that claims [fileName], or null when nothing here runs it.
         *
         * Accepts a bare name or a whole path. A dot in the first position is a hidden
         * file rather than an extension, so `.py` is a config file someone happened to
         * name that way, not a Python script.
         */
        fun forFile(fileName: String): Runtime? {
            val name = fileName.substringAfterLast('/').substringAfterLast('\\')
            val dot = name.lastIndexOf('.')
            if (dot <= 0) return null
            val extension = name.substring(dot + 1).lowercase()
            return entries.firstOrNull { extension in it.extensions }
        }
    }
}

/** Turning paths and arguments into something a shell will not misread. */
object ShellQuote {

    /**
     * Wraps [value] in single quotes, which a POSIX shell takes completely literally.
     *
     * A single quote inside the value has to leave the quoting, contribute an escaped
     * quote, and go back in, which is what the `'\''` looks like. Without this a file
     * named `my project's notes.py` would arrive at the shell as three broken arguments.
     */
    fun single(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
