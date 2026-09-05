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
