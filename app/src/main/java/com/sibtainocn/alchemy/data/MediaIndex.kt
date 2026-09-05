package com.sibtainocn.alchemy.data

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tells Android that files have appeared, moved or gone.
 *
 * Writing to shared storage changes the disk; it does not change what the rest of the
 * device believes. The media index is a separate database, and until something updates it
 * a copied photo is missing from the gallery, a deleted one is still listed there, and a
 * moved file opens from a path that no longer exists. Every file manager on the platform
 * does this, and it is the difference between editing files and running a file manager.
 *
 * The scanner is advisory in both directions: it is told what changed and it decides what
 * to do about it, a path that no longer exists is how a row gets removed, and nothing here
 * fails a transfer that has already succeeded on disk. So every call is best effort and
 * every failure is swallowed.
 */
object MediaIndex {

    /**
     * How many paths one notification will carry.
     *
     * A folder of ten thousand files does not need ten thousand scan requests to make the
     * point; the index reconciles a directory it is told about, and the cap keeps a batch
     * on a large tree from turning into a second transfer's worth of work.
     */
    private const val MAX_PATHS = 512

    /**
     * Announces [paths], expanding a folder into the files under it.
     *
     * Runs off the caller's thread. Directories are walked to [MAX_PATHS] and the folder
     * itself is always included, so a move of a large tree still tells the index where the
     * tree went even when not every leaf inside it is named.
     */
    suspend fun refresh(context: Context, paths: Collection<File>) {
        if (paths.isEmpty()) return
        val app = context.applicationContext
        val expanded = withContext(Dispatchers.IO) { expand(paths) }
        if (expanded.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(app, expanded.toTypedArray(), null, null)
        }
    }

    /** Both ends of a transfer: what left, and what arrived. */
    suspend fun refresh(context: Context, vararg paths: File?) =
        refresh(context, paths.filterNotNull())

    private fun expand(paths: Collection<File>): List<String> {
        val out = LinkedHashSet<String>()
        for (path in paths) {
            if (out.size >= MAX_PATHS) break
            out += path.absolutePath
            // A deleted path cannot be walked, and does not need to be: naming it is what
            // tells the index to drop the row.
            if (!path.isDirectory) continue
            walk(path, out)
        }
        return out.toList()
    }

    private fun walk(dir: File, into: MutableSet<String>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (into.size >= MAX_PATHS) return
            into += child.absolutePath
            if (child.isDirectory) walk(child, into)
        }
    }
}
