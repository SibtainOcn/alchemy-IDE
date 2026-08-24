package dev.hazel.code.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

    /** Above this size a file opens read-only; a TextField cannot carry it comfortably. */
    const val EDIT_LIMIT_BYTES = 2L * 1024 * 1024

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
            val tmp = File(file.parentFile, "." + file.name + ".hazel-tmp")
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
}
