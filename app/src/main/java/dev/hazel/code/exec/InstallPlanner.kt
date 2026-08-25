package dev.hazel.code.exec

/**
 * Deciding what to install, and in what order.
 *
 * Kept apart from the code that does the installing so the decisions can be tested
 * without a device, a network or a package manager.
 */
object InstallPlanner {

    /**
     * The runtimes worth installing out of [selected], in the order to do it.
     *
     * Three rules, each earning its place:
     *
     * - Anything already present is dropped. Reinstalling Go is a quarter of a gigabyte
     *   spent to arrive where we started, and on a phone that is somebody's data plan.
     * - The rest go smallest first, so something works within a minute instead of after
     *   the longest download in the set.
     * - The order is deterministic, because a install that picks a different order each
     *   time is one that cannot be reasoned about when it goes wrong.
     */
    fun plan(selected: Set<Runtime>, present: Set<Runtime>): List<Runtime> =
        selected.filterNot { it in present }
            .sortedWith(compareBy({ it.approximateMb }, { it.name }))

    /**
     * The packages to hand the package manager for [runtimes], with duplicates removed.
     *
     * Two runtimes can want the same package, and asking for it twice in one command is
     * how you get an error from apt about a duplicate rather than an install.
     */
    fun packagesFor(runtimes: List<Runtime>): List<String> =
        runtimes.flatMap { it.packages }.distinct()

    /** Roughly how much this will download, to show before it starts. */
    fun totalMb(runtimes: List<Runtime>): Int = runtimes.sumOf { it.approximateMb }
}

/** Where one runtime has got to during a run of the installer. */
sealed interface InstallState {

    /** Selected, not started. */
    data object Waiting : InstallState

    /** Nothing to do. */
    data object AlreadyInstalled : InstallState

    data object Installing : InstallState

    data object Installed : InstallState

    /**
     * Did not install.
     *
     * [reason] is shown to the user, so it holds the last useful line of what the package
     * manager said rather than a stack trace or an exit code.
     */
    data class Failed(val reason: String) : InstallState

    /**
     * The package manager reported success but the runtime still is not usable.
     *
     * Worth its own state: it means something else is wrong, usually a broken mirror or a
     * half-finished earlier install, and telling someone it worked when it did not is how
     * they end up filing a bug about the Run button instead.
     */
    data object InstalledButMissing : InstallState
}

/**
 * The last useful line of output from a failed install.
 *
 * Package managers are verbose in success and verbose in failure, and the sentence that
 * explains what went wrong is almost always the last one that is not blank. Showing the
 * whole log in a dialog would bury it.
 */
fun lastMeaningfulLine(output: String, fallback: String): String =
    output.lineSequence()
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() }
        ?.take(300)
        ?: fallback
