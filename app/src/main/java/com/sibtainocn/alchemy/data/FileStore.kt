package com.sibtainocn.alchemy.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Raised when the OS refuses an operation. The Java file API signals this with a null
 * return or a plain false, which is indistinguishable from "empty" or "already gone", so
 * it is turned into a typed failure here and handled explicitly upstream.
 */
class AccessDenied(val target: File, val action: String) :
    IOException("Android denied " + action + " access to " + target.name)

/**
 * Every filesystem touch goes through here, so the UI layer never blocks the main thread
 * and never has to think about IO exceptions.
 */
object FileStore {

    /**
     * Above this size a file opens read-only.
     *
     * It was 2 MB because the whole document went into one text field and one text layout,
     * so everything cost what the file was long. The editor draws only the lines on screen
     * now, and a 3.17 MB Python file measured on a mid-range phone scrolls at a worst
     * frame of 42ms - two dropped frames, against six *seconds* for a 125 KB file under
     * the old arrangement.
     *
     * So this is now about memory rather than about drawing: the text is read into a
     * string and then into the buffer, so a file costs a few times its own size while it
     * is open. Four megabytes is comfortably past what was measured and comfortably short
     * of a heap that a cheap phone would struggle with.
     */
    const val EDIT_LIMIT_BYTES = 4L * 1024 * 1024

    /** Hard ceiling for reading at all. */
    private const val OPEN_LIMIT_BYTES = 16L * 1024 * 1024

    val storageRoot: File
        get() = android.os.Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")

    suspend fun list(dir: File, showHidden: Boolean, sortBy: SortBy, descending: Boolean): List<Entry> =
        withContext(Dispatchers.IO) {
            // null here means refused or unreadable, which is very different from empty.
            val children = dir.listFiles() ?: throw AccessDenied(dir, "read")
            children.asSequence()
                .filter { showHidden || !it.name.startsWith('.') }
                .map { f ->
                    val dirFlag = f.isDirectory
                    Entry(
                        file = f,
                        isDir = dirFlag,
                        name = f.name,
                        sizeBytes = if (dirFlag) 0L else f.length(),
                        modified = f.lastModified(),
                        // listFiles() on an unreadable directory returns null rather than an
                        // empty array - keep those apart from a genuinely empty folder.
                        childCount = if (dirFlag) f.list()?.size ?: -1 else 0,
                    )
                }
                .sortedWith(comparator(sortBy, descending))
                .toList()
        }

    private fun comparator(sortBy: SortBy, descending: Boolean): Comparator<Entry> {
        val base: Comparator<Entry> = when (sortBy) {
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.SIZE -> compareBy { it.sizeBytes }
            SortBy.MODIFIED -> compareBy { it.modified }
            SortBy.TYPE -> compareBy<Entry> { it.name.substringAfterLast('.', "").lowercase() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val directed = if (descending) base.reversed() else base
        // Folders always lead, whichever way the rest is pointing.
        return compareByDescending<Entry> { it.isDir }.then(directed)
    }

    suspend fun read(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) throw IOException("That file no longer exists")
            if (!file.canRead()) throw AccessDenied(file, "read")
            if (file.length() > OPEN_LIMIT_BYTES) error("File is too large to open")
            file.readText()
        }
    }

    suspend fun write(file: File, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.exists() && !file.canWrite()) throw AccessDenied(file, "write")
            // Write beside the target and swap, so a failure mid-write cannot shred the
            // original. Falls back to a direct write when the rename is refused.
            val tmp = File(file.parentFile, "." + file.name + ".alchemy-tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
            Unit
        }
    }

    suspend fun looksBinary(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(1024)
                val n = input.read(buf)
                if (n <= 0) return@use false
                // A NUL byte inside the first KB is the cheap, reliable binary tell.
                (0 until n).any { buf[it] == 0.toByte() }
            }
        }.getOrDefault(true)
    }

    suspend fun createFile(dir: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.canWrite()) throw AccessDenied(dir, "write")
            val f = File(dir, name)
            if (f.exists()) throw IOException(name + " already exists")
            if (!f.createNewFile()) throw AccessDenied(dir, "write")
            f
        }
    }

    suspend fun createDir(dir: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.canWrite()) throw AccessDenied(dir, "write")
            val f = File(dir, name)
            if (f.exists()) throw IOException(name + " already exists")
            if (!f.mkdirs()) throw AccessDenied(dir, "write")
            f
        }
    }

    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(file.parentFile, newName)
            if (target.exists()) throw IOException(newName + " already exists")
            if (!file.renameTo(target)) throw AccessDenied(file, "rename")
            target
        }
    }

    suspend fun delete(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.deleteRecursively()) throw AccessDenied(file, "delete")
            Unit
        }
    }

    // -----------------------------------------------------------------------
    // Copy and move
    //
    // Nothing here is API-gated. `renameTo`, `FileChannel.transferTo` and plain streams
    // are all API 1, so the same path runs on 24 and on the newest release and there is
    // no second implementation to keep in step. What does change between devices is the
    // volume underneath: emulated storage, a FUSE mount and a FAT-formatted card do not
    // agree on what a channel transfer is willing to do, so the fast route is attempted
    // and the plain one is kept behind it.
    // -----------------------------------------------------------------------

    /** How far a transfer has got. [total] is 0 when the size could not be measured. */
    data class Progress(val done: Long, val total: Long) {
        /** 0f..1f, or null when there is nothing to measure against. */
        val fraction: Float? get() = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else null
    }

    /** A name at the destination that is already taken. */
    data class Conflict(val source: File, val existing: File) {
        /**
         * Two folders of the same name combine rather than displace each other, which is
         * what every desktop file manager does and what anyone pasting a source tree
         * expects. Anything else is a straight swap.
         */
        val isMerge: Boolean get() = source.isDirectory && existing.isDirectory
    }

    enum class Resolution {
        /** Leave what is there and move on to the next thing. */
        SKIP,

        /** Replace the file, or merge into the folder. */
        OVERWRITE,

        /** Land alongside it under a free name. */
        KEEP_BOTH,

        /** Stop the whole transfer here. */
        CANCEL,
    }

    /** An answer to one [Conflict]. [all] carries it to every later clash in the same run. */
    data class Decision(val resolution: Resolution, val all: Boolean = false)

    /** Raised when a conflict prompt is dismissed, which stops the run without an error. */
    class TransferAborted : IOException("Transfer cancelled")

    /** Smallest read we bother with; below this the syscall costs more than the bytes. */
    private const val MIN_CHUNK = 64 * 1024

    /** Ceiling for a heap buffer, so a big copy cannot become a big allocation. */
    private const val MAX_CHUNK = 4 * 1024 * 1024

    /**
     * How much a single `transferTo` is asked for.
     *
     * The kernel keeps the bytes out of this process either way, so the slice is not
     * about memory. It caps how long one uninterruptible call can run, which is what
     * makes cancellation land promptly and progress tick at all, and it stays well under
     * the 2 GB that some kernels quietly clamp a single transfer to.
     */
    private const val TRANSFER_SLICE = 32L * 1024 * 1024

    /** How often progress is allowed to reach the UI, in milliseconds. */
    private const val REPORT_EVERY_MS = 80L

    /**
     * Duplicates [source] inside [destDir]. Returns where it landed, or null if the whole
     * thing was skipped at a conflict prompt.
     *
     * [resolve] is asked about every name that is already taken, [onProgress] is called
     * on the worker thread and already throttled. A failure part-way removes only what
     * this transfer wrote, so a half copy never sits in the folder looking whole and a
     * folder being merged into never loses what was already in it.
     */
    suspend fun copyInto(
        source: File,
        destDir: File,
        onProgress: ((Progress) -> Unit)? = null,
        resolve: (suspend (Conflict) -> Decision)? = null,
    ): Result<File?> = withContext(Dispatchers.IO) {
        runCatching {
            validate(source, destDir)
            val total = measureIn(source)
            requireRoom(destDir, total)
            val session = Session(Tally(total, onProgress), resolve)
            val landed = place(source, destDir, session, move = false)
            session.tally.flush()
            landed
        }.rethrowCancellation()
    }

    /**
     * Relocates [source] into [destDir]. Returns where it landed, or null if it was
     * skipped.
     *
     * The rename is tried first and is the whole point of the operation: inside one
     * volume it is a directory-entry change, so a folder of any size arrives instantly
     * and nothing needs measuring. Only when the kernel refuses, which is what happens
     * across volumes and when something is already in the way, do the bytes travel.
     */
    suspend fun moveInto(
        source: File,
        destDir: File,
        onProgress: ((Progress) -> Unit)? = null,
        resolve: (suspend (Conflict) -> Decision)? = null,
    ): Result<File?> = withContext(Dispatchers.IO) {
        runCatching {
            validate(source, destDir)

            // The clear road: nothing in the way and the same volume underneath. Worth
            // taking before the tree is walked, because walking it is the expensive part
            // of a move that is about to cost nothing at all.
            val direct = File(destDir, source.name)
            if (!direct.exists() && source.renameTo(direct)) return@runCatching direct

            // Past here bytes may have to travel, so the size is worth knowing. No space
            // check: most of what follows is still renames, and a cross-volume move that
            // runs out of room fails on the write with a reason of its own.
            val session = Session(Tally(measureIn(source), onProgress), resolve)
            val landed = place(source, destDir, session, move = true)
            session.tally.flush()
            landed
        }.rethrowCancellation()
    }

    /** Total bytes under [file], counting the whole tree. Cancellable. */
    suspend fun measure(file: File): Long = withContext(Dispatchers.IO) { measureIn(file) }

    /**
     * Picks a name that is free inside [dir], starting from [name].
     *
     * A clash gets " (2)", then " (3)", inserted before the extension so a pasted
     * `main.py` stays a Python file. A leading dot is not an extension, so `.gitignore`
     * keeps its whole name rather than becoming " (2).gitignore".
     */
    fun freeNameIn(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var n = 2
        // Bounded, so a directory that somehow refuses every name fails loudly rather
        // than spinning here forever.
        while (n < 10_000) {
            val candidate = stem + " (" + n + ")" + suffix
            if (!File(dir, candidate).exists()) return candidate
            n++
        }
        throw IOException("Cannot find a free name for " + name)
    }

    /**
     * True when [candidate] is [ancestor] or sits somewhere beneath it.
     *
     * Canonical paths, so a symlinked route to the same folder is caught too. Pasting a
     * folder into itself would otherwise copy until the volume filled.
     */
    fun isInside(candidate: File, ancestor: File): Boolean = runCatching {
        val root = ancestor.canonicalPath
        val here = candidate.canonicalPath
        here == root || here.startsWith(root + File.separator)
    }.getOrDefault(false)

    // ---- The engine ----

    private fun validate(source: File, destDir: File) {
        if (!source.exists()) throw IOException(source.name + " is no longer there")
        if (!destDir.isDirectory) throw IOException("That destination is not a folder")
        if (!destDir.canWrite()) throw AccessDenied(destDir, "write")
        if (source.isDirectory && isInside(destDir, source)) {
            throw IOException("A folder cannot go inside itself")
        }
    }

    /**
     * Settles one entry into [destDir], asking about the name first if it is taken.
     *
     * Recursive only where it has to be: a folder merging into an existing folder comes
     * back through here for each of its children, so a clash three levels down is asked
     * about the same way as one at the top. Anything landing on a free name is written
     * whole by [writeTree], which never has to ask about anything.
     */
    private suspend fun place(source: File, destDir: File, session: Session, move: Boolean): File? {
        currentCoroutineContext().ensureActive()
        var target = File(destDir, source.name)

        if (target.exists()) {
            val conflict = Conflict(source, target)
            when (session.decide(conflict)) {
                Resolution.SKIP -> {
                    // Counted as done, or the bar would stop short of the end by exactly
                    // the size of what was deliberately not copied.
                    session.tally.add(measureIn(source))
                    return null
                }

                Resolution.KEEP_BOTH -> target = File(destDir, freeNameIn(destDir, source.name))

                Resolution.OVERWRITE -> {
                    if (conflict.isMerge) return merge(source, target, session, move)
                    if (!target.deleteRecursively()) throw AccessDenied(target, "delete")
                }

                // decide() has already thrown by this point; the branch keeps the when
                // exhaustive without a catch-all that would hide a new case.
                Resolution.CANCEL -> throw TransferAborted()
            }
        }

        // The name is free now, whichever way it got that way.
        if (move && source.renameTo(target)) return target
        try {
            writeTree(source, target, session)
        } catch (t: Throwable) {
            // Only ever what this call created: target was free a moment ago.
            target.deleteRecursively()
            throw t
        }
        if (move && !source.deleteRecursively()) {
            throw IOException(
                source.name + " was copied to " + destDir.name +
                    ", but the original could not be removed"
            )
        }
        return target
    }

    /** Two folders of the same name, combined child by child. */
    private suspend fun merge(source: File, into: File, session: Session, move: Boolean): File {
        if (!into.canWrite()) throw AccessDenied(into, "write")
        val children = source.listFiles() ?: throw AccessDenied(source, "read")
        for (child in children) place(child, into, session, move)
        runCatching { into.setLastModified(source.lastModified()) }
        // A plain delete, not a recursive one: anything skipped is still in there and has
        // to stay. An emptied folder goes, a partly-kept one stays with what was kept.
        if (move) source.delete()
        return into
    }

    /** Writes a whole tree onto a name that is known to be free. */
    private suspend fun writeTree(source: File, target: File, session: Session) {
        currentCoroutineContext().ensureActive()
        if (source.isDirectory) {
            if (!target.isDirectory && !target.mkdirs()) throw AccessDenied(target, "write")
            val children = source.listFiles() ?: throw AccessDenied(source, "read")
            for (child in children) writeTree(child, File(target, child.name), session)
        } else {
            copyFile(source, target, session.tally)
        }
        // Best effort: a volume that will not carry the timestamp is not a reason to fail
        // a copy that otherwise worked.
        runCatching { target.setLastModified(source.lastModified()) }
    }

    /**
     * One file, by whichever route the volume underneath will accept.
     *
     * The channel is tried first because it hands the copy to the kernel and the bytes
     * never enter this process. Some mounts refuse it, and some accept it and move
     * nothing, so the count is checked rather than trusted and the plain route starts
     * over from an empty file when it does not add up.
     */
    private suspend fun copyFile(source: File, target: File, tally: Tally) {
        val size = source.length()
        val viaChannel = runCatching { channelCopy(source, target, size, tally) }
        val carried = viaChannel.getOrDefault(0L)
        val failure = viaChannel.exceptionOrNull()
        // A cancelled copy is not a failed one and must not be retried by hand.
        if (failure is CancellationException) throw failure
        if (failure != null || carried < size) {
            tally.rollback(carried)
            streamCopy(source, target, tally)
        }
    }

    /** Returns how many bytes the kernel actually carried, which may be short of [size]. */
    private suspend fun channelCopy(source: File, target: File, size: Long, tally: Tally): Long {
        if (size == 0L) {
            // transferTo has nothing to do, but the file still has to come into being.
            FileOutputStream(target).use { }
            return 0L
        }
        var done = 0L
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val from = input.channel
                val to = output.channel
                while (done < size) {
                    currentCoroutineContext().ensureActive()
                    val moved = from.transferTo(done, minOf(TRANSFER_SLICE, size - done), to)
                    if (moved <= 0L) break
                    done += moved
                    tally.add(moved)
                }
            }
        }
        return done
    }

    /**
     * The fallback: a heap buffer, sized against the file and against what the heap can
     * actually spare, so a large copy on a small device does not trade one failure for
     * another.
     */
    private suspend fun streamCopy(source: File, target: File, tally: Tally) {
        val buffer = ByteArray(chunkFor(source.length()))
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    tally.add(read.toLong())
                }
                output.flush()
            }
        }
    }

    /**
     * Buffer size for one copy.
     *
     * Big enough that a large file is not read a page at a time, small enough that eight
     * of them would still fit in what the heap has left. A device with room gets the fast
     * buffer; one already close to its limit gets the modest one, rather than an
     * OutOfMemoryError in the middle of someone's files.
     */
    private fun chunkFor(size: Long): Int {
        val runtime = java.lang.Runtime.getRuntime()
        val headroom = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val ceiling = (headroom / 8).coerceIn(MIN_CHUNK.toLong(), MAX_CHUNK.toLong())
        return size.coerceIn(MIN_CHUNK.toLong(), ceiling).toInt()
    }

    private suspend fun measureIn(file: File): Long {
        currentCoroutineContext().ensureActive()
        if (!file.isDirectory) return file.length()
        val children = file.listFiles() ?: return 0L
        var sum = 0L
        for (child in children) sum += measureIn(child)
        return sum
    }

    /**
     * Refuses a copy that would not fit.
     *
     * `usableSpace` reports 0 for volumes that decline to answer, and that is not the
     * same as a full disk, so an unknown figure lets the copy go ahead and fail honestly
     * on the write instead.
     */
    private fun requireRoom(destDir: File, needed: Long) {
        val room = runCatching { destDir.usableSpace }.getOrDefault(0L)
        if (room > 0L && needed > room) {
            throw IOException(
                "Not enough room: " + needed / (1024 * 1024) + " MB needed, " +
                    room / (1024 * 1024) + " MB free"
            )
        }
    }

    /** `runCatching` catches everything, and a cancelled transfer must stay cancelled. */
    private fun <T> Result<T>.rethrowCancellation(): Result<T> = also {
        val e = exceptionOrNull()
        if (e is CancellationException) throw e
    }

    /** One transfer's worth of shared state: what has been carried, and what was decided. */
    private class Session(
        val tally: Tally,
        private val resolve: (suspend (Conflict) -> Decision)?,
    ) {
        /** Set once "apply to all" is ticked, and answers every clash after that. */
        private var standing: Resolution? = null

        suspend fun decide(conflict: Conflict): Resolution {
            standing?.let { if (it == Resolution.CANCEL) throw TransferAborted() else return it }
            // Nobody to ask means nothing should be destroyed on our own initiative.
            val resolver = resolve ?: return Resolution.KEEP_BOTH
            val decision = resolver(conflict)
            if (decision.all) standing = decision.resolution
            if (decision.resolution == Resolution.CANCEL) throw TransferAborted()
            return decision.resolution
        }
    }

    /**
     * The running count behind a transfer.
     *
     * Throttled on the way out: a per-buffer callback would ask the UI to redraw a few
     * hundred times a second and spend more time on the progress than on the copy.
     */
    private class Tally(private val total: Long, private val report: ((Progress) -> Unit)?) {
        private var done = 0L
        private var lastAt = 0L

        fun add(n: Long) {
            done += n
            val report = report ?: return
            val now = System.currentTimeMillis()
            if (now - lastAt < REPORT_EVERY_MS) return
            lastAt = now
            report(Progress(done, total))
        }

        /** Un-counts a route that was abandoned, so the retry does not count twice. */
        fun rollback(n: Long) {
            done -= n
        }

        /** The last word, sent whether or not the throttle would have allowed it. */
        fun flush() {
            report?.invoke(Progress(done, total))
        }
    }
}
