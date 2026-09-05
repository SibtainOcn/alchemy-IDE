package com.sibtainocn.alchemy.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a file to whichever app the device already uses for it.
 *
 * Alchemy edits text. A `.png`, a `.mp4` or a `.zip` has a handler installed already, and
 * reporting "not text" is a worse answer than opening the thing the user asked for.
 *
 * The URI comes from [FileProvider] rather than being a `file://` path: since API 24 the
 * platform throws `FileUriExposedException` when a `file://` URI crosses to another
 * process. The receiving app is granted read access for the life of its activity, and
 * nothing else on the device gains access to the path.
 */
object ExternalOpen {

    /** Matches the provider authority declared in the manifest. */
    private const val AUTHORITY_SUFFIX = ".files"

    /**
     * Extensions the platform's own MIME table gets wrong or does not know, limited to the
     * ones that decide whether a handler is offered at all.
     */
    private val extraTypes = mapOf(
        "apk" to "application/vnd.android.package-archive",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",
        "tgz" to "application/gzip",
        "zst" to "application/zstd",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "opus" to "audio/opus",
        "mkv" to "video/x-matroska",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "epub" to "application/epub+zip",
        "html" to "text/html",
        "htm" to "text/html",
    )

    /**
     * Names that read as documents but are not text to edit.
     *
     * The Office and OpenDocument formats are zip containers, so nothing useful comes of
     * loading one as characters. HTML is the exception that is deliberate rather than
     * technical: it is source, and it is also a page, and a tap on a page means read it.
     * Editing it is the "Open in editor" action on the entry's own menu.
     */
    private val documents = setOf(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "odt", "ods", "odp", "rtf", "epub",
        "html", "htm",
    )

    /** The type to offer the file as, falling back to a wildcard so a chooser still opens. */
    fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return extraTypes[ext]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "*/*"
    }

    /**
     * Opens [file] in another app. Returns false when nothing on the device handles it,
     * which is the caller's cue to say so rather than to fail silently.
     */
    fun open(context: Context, file: File): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        }.getOrNull() ?: return false

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Hands [files] to the system share sheet.
     *
     * The platform's own chooser, not a list of our own: what can receive a file is a
     * property of the device and changes with every app installed on it, and every one of
     * them already knows how to be shared to.
     *
     * One file goes as `ACTION_SEND`, several as `ACTION_SEND_MULTIPLE`, which is the
     * difference between "share this" and "share these" as far as every receiving app is
     * concerned. The type offered is the one type they have in common, narrowed as far as
     * it honestly can be: the exact type for a single picture, the image family for
     * several of different kinds, and the wildcard for a mixture. A chooser given a type
     * too narrow to be true hides apps that would have worked.
     *
     * Folders are dropped rather than refused: Android has no concept of sharing a
     * directory, and there is no reason for the caller to have to know that.
     */
    fun share(context: Context, files: List<File>): Boolean {
        val authority = context.packageName + AUTHORITY_SUFFIX
        val uris = ArrayList<android.net.Uri>()
        val types = mutableSetOf<String>()
        for (file in files) {
            if (file.isDirectory || !file.exists()) continue
            val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }
                .getOrNull() ?: continue
            uris += uri
            types += mimeOf(file.name)
        }
        if (uris.isEmpty()) return false

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, uris.first())
                .setType(types.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .setType(commonType(types))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        return try {
            context.startActivity(
                Intent.createChooser(intent, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // The chooser holds the grant on the receiver's behalf; without this
                    // the app the user picks is handed a URI it cannot read.
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** The narrowest type that is true of all of them. */
    private fun commonType(types: Set<String>): String {
        types.singleOrNull()?.let { return it }
        val families = types.map { it.substringBefore('/') }.toSet()
        return families.singleOrNull()?.let { "$it/*" } ?: "*/*"
    }

    /**
     * True when the file is something Alchemy has no business opening as text.
     *
     * Decided on the name, so the explorer can route a tap without reading the file first.
     * Content is still checked when the editor does open something, which is what catches
     * a `.docx` or a stray binary behind a text-shaped extension.
     */
    fun isForAnotherApp(name: String): Boolean {
        if (name.substringAfterLast('.', "").lowercase() in documents) return true
        return when (FileKind.of(name)) {
            FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO,
            FileKind.ARCHIVE, FileKind.PDF, FileKind.APP, FileKind.BINARY -> true
            FileKind.CODE, FileKind.TEXT -> false
        }
    }
}
