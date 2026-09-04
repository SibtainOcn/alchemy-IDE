package com.sibtainocn.alchemy.exec

/**
 * One entry in the terminal's scrollback.
 *
 * Kept as a list of typed lines rather than as one growing string so the view can colour
 * an error differently from output, and so a long run does not re-lay-out everything
 * above it every time another chunk arrives.
 */
sealed interface ConsoleLine {

    /** The echo of something that was typed, shown with the prompt in front of it. */
    data class Typed(val command: String) : ConsoleLine

    data class Output(val text: String) : ConsoleLine

    data class Error(val text: String) : ConsoleLine

    /** Where the session is, or something worth saying about the output. */
    data class Note(val text: String) : ConsoleLine

    /**
     * How long a command took, and what it exited with.
     *
     * Separate from [Note] only so it can be turned off. Useful while you are waiting on
     * something, noise once you are reading the output of something that works.
     */
    data class Timing(val text: String) : ConsoleLine
}

/**
 * The parts of a terminal that are just text handling.
 *
 * Everything here is pure, because the interesting cases are all about paths and none of
 * them need a device to get wrong.
 *
 * The reason any of this exists: each command runs in its own process, so nothing carries
 * from one to the next. A shell would remember where `cd` left it. This has to remember
 * for it, and pass the answer back in with every command.
 */
object Console {

    /**
     * The argument of a bare `cd`, or null when the line is anything else.
     *
     * Only a line that is nothing but a `cd` is taken over. `cd build && make` is left to
     * the shell, where the `cd` correctly applies to that command and no further, which
     * is what the person who wrote it meant.
     */
    fun cdTarget(line: String): String? {
        val trimmed = line.trim()
        if (trimmed != "cd" && !trimmed.startsWith("cd ")) return null
        if (trimmed.any { it in ";&|<>`" } || trimmed.contains("$(")) return null

        val argument = trimmed.removePrefix("cd").trim()
        return argument
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .ifEmpty { "~" }
    }

    /**
     * Where [argument] points, starting from [cwd].
     *
     * Resolved here rather than left to the shell so the command sent is always an
     * absolute path. A relative one would be read against whatever directory the next
     * process happens to start in, which is not necessarily this one.
     */
    fun resolve(cwd: String, argument: String, home: String): String = when {
        argument.isEmpty() || argument == "~" -> home
        argument.startsWith("~/") -> normalise("$home/${argument.removePrefix("~/")}")
        argument.startsWith("/") -> normalise(argument)
        else -> normalise("$cwd/$argument")
    }

    /**
     * Collapses `.`, `..` and repeated slashes.
     *
     * A `..` at the top of an absolute path has nowhere to go and is dropped, which is
     * what the filesystem does with it too.
     */
    fun normalise(path: String): String {
        val absolute = path.startsWith("/")
        val parts = ArrayDeque<String>()

        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> when {
                    parts.isNotEmpty() && parts.last() != ".." -> parts.removeLast()
                    !absolute -> parts.addLast("..")
                    else -> Unit
                }
                else -> parts.addLast(part)
            }
        }

        val joined = parts.joinToString("/")
        return when {
            absolute -> "/$joined"
            joined.isEmpty() -> "."
            else -> joined
        }
    }

    /**
     * How a finished command is summarised.
     *
     * The exit code is only worth showing when it is not zero: a successful command that
     * announces its own success is noise on every single line.
     */
    fun summarise(exitCode: Int?, millis: Long): String {
        val seconds = millis / 1000.0
        val took = if (seconds < 1) "${millis}ms" else String.format("%.1fs", seconds)
        return when (exitCode) {
            null -> "did not run"
            0 -> "done in $took"
            else -> "exit $exitCode after $took"
        }
    }
}
