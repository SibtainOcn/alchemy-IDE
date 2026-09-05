package com.sibtainocn.alchemy.data

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A kind of file, as something to search for.
 *
 * Named after what somebody is looking for rather than after how the file is encoded:
 * "Documents" is a category a person has, `application/vnd.oasis.opendocument.text` is not.
 * The three at the end are the ones this app exists for, which is why a `.py` filter sits
 * beside the general ones rather than inside Code.
 */
enum class SearchKind(val label: String) {
    FOLDERS("Folders"),
    CODE("Code"),
    PYTHON(".py"),
    MARKDOWN(".md"),
    PDF(".pdf"),
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    AUDIO("Audio"),
    VIDEO("Video"),
    ARCHIVES("Archives"),
    APPS("APK");

    /** Whether [entry] belongs to this kind. Nothing here reads the file. */
    fun matches(entry: Entry): Boolean {
        if (this == FOLDERS) return entry.isDir
        if (entry.isDir) return false
        val extension = entry.name.substringAfterLast('.', "").lowercase()
        return when (this) {
            FOLDERS -> false
            PYTHON -> extension == "py" || extension == "pyw" || extension == "pyi"
            MARKDOWN -> extension == "md" || extension == "markdown" || extension == "mdx"
            PDF -> entry.kind == FileKind.PDF
            CODE -> entry.kind == FileKind.CODE
            DOCUMENTS -> entry.kind == FileKind.TEXT || entry.kind == FileKind.PDF
            IMAGES -> entry.kind == FileKind.IMAGE
            AUDIO -> entry.kind == FileKind.AUDIO
            VIDEO -> entry.kind == FileKind.VIDEO
            ARCHIVES -> entry.kind == FileKind.ARCHIVE
            APPS -> entry.kind == FileKind.APP
        }
    }
}

/**
 * Searching a folder and everything under it.
 *
 * Three things make this usable on a phone rather than a way to lock one up.
 *
 * It is breadth first, over an explicit queue rather than the call stack, so what is
 * nearest the folder you are standing in arrives first and a tree deep enough to overflow a
 * stack cannot. It reports in batches as it goes, so a search of forty thousand files fills
 * the screen immediately instead of after the walk. And it checks for cancellation on every
 * directory, so a keystroke that supersedes it stops it inside a few milliseconds rather
 * than at the end.
 *
 * What it will not do is read the inside of any file. This matches names and kinds, which
 * is what a file manager's search is, and it is why walking a large tree costs a stat per
 * entry rather than a read.
 */
object Search {

    /** Results past this are not collected. Nobody scrolls to the four thousandth match. */
    const val MAX_RESULTS = 2_000

    /** How many hits are collected before the caller hears about them. */
    private const val BATCH = 24

    /** And how long a partial batch waits before it is sent anyway. */
    private const val BATCH_MS = 120L

    data class Result(
        val entries: List<Entry>,
        /** True when the cap was reached and the walk stopped short of the whole tree. */
        val truncated: Boolean,
    )

    /**
     * Walks [root], reporting whatever matches both [query] and [kind].
     *
     * An empty query with no kind matches nothing: a search screen that lists the entire
     * volume the moment it opens has answered a question nobody asked. Either one on its
     * own is a real search, which is what makes the chips work as one-tap searches.
     *
     * [onBatch] is called on the IO dispatcher with everything found so far.
     */
    suspend fun run(
        root: File,
        query: String,
        kind: SearchKind?,
        showHidden: Boolean,
        onBatch: (List<Entry>) -> Unit,
    ): Result = withContext(Dispatchers.IO + CoroutineName("search")) {
        val needle = query.trim()
        if (needle.isEmpty() && kind == null) return@withContext Result(emptyList(), false)

        val found = ArrayList<Entry>(64)
        val queue = ArrayDeque<File>()
        queue += root
        var sinceReport = 0
        var reportedAt = System.currentTimeMillis()
        var truncated = false

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = queue.removeFirst()
            // A folder that cannot be read is a fact about that folder, not a reason to
            // stop: half of shared storage is somebody else's app directory.
            val children = dir.listFiles() ?: continue

            for (child in children) {
                val name = child.name
                if (!showHidden && name.startsWith('.')) continue

                val isDir = child.isDirectory
                if (isDir) queue += child

                if (needle.isNotEmpty() && !name.contains(needle, ignoreCase = true)) continue
                val entry = Entry(
                    file = child,
                    isDir = isDir,
                    name = name,
                    sizeBytes = if (isDir) 0L else child.length(),
                    modified = child.lastModified(),
                    // Not counted: a search result is a row to tap, and asking every
                    // folder in the tree how many children it has doubles the walk.
                    childCount = if (isDir) 0 else 0,
                )
                if (kind != null && !kind.matches(entry)) continue

                found += entry
                sinceReport++
                if (found.size >= MAX_RESULTS) {
                    truncated = true
                    break
                }
            }

            val now = System.currentTimeMillis()
            if (sinceReport > 0 && (sinceReport >= BATCH || now - reportedAt >= BATCH_MS)) {
                onBatch(ArrayList(found))
                sinceReport = 0
                reportedAt = now
            }
            if (truncated) break
        }

        onBatch(ArrayList(found))
        Result(found, truncated)
    }
}
