package com.sibtainocn.alchemy.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Resolves an incoming VIEW, EDIT or SEND intent to a file on disk.
 *
 * Senders rarely provide a path. File managers send `content://` URIs backed by a
 * DocumentsProvider, the downloads UI sends its own row id, and share sheets send
 * MediaStore entries. Each authority encodes the location differently, so each is unwrapped
 * separately before falling back to the provider's `MediaStore.MediaColumns.DATA` column.
 *
 * Resolution targets a [File] rather than the URI's stream because saves are performed in
 * place on the original path: write to a sibling temporary file, then rename over it. A
 * buffer taken from a stream has no such path, so it would diverge from the sender's file
 * at save time. An unresolvable URI is therefore reported as not found.
 */
object IntentFiles {

    /** The file an incoming intent is asking for, or null when it is not asking for one. */
    fun resolve(context: Context, intent: Intent?): File? {
        val uri = intent?.data
            ?: intent?.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
            ?: return null
        return runCatching { fileFor(context, uri) }.getOrNull()?.takeIf { it.isFile }
    }

    private fun fileFor(context: Context, uri: Uri): File? = when {
        uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let(::File)

        DocumentsContract.isDocumentUri(context, uri) -> fromDocument(context, uri)

        uri.scheme.equals("content", ignoreCase = true) -> dataColumn(context, uri)

        else -> null
    }

    private fun fromDocument(context: Context, uri: Uri): File? {
        val id = DocumentsContract.getDocumentId(uri)
        return when (uri.authority) {
            // "primary:Download/notes.py", or "1A2B-3C4D:src/main.kt" on a card.
            "com.android.externalstorage.documents" -> {
                val (volume, relative) = id.split(":", limit = 2).takeIf { it.size == 2 }
                    ?: return null
                val base = if (volume.equals("primary", ignoreCase = true)) {
                    Environment.getExternalStorageDirectory()
                } else {
                    File("/storage/$volume")
                }
                File(base, relative)
            }

            // The downloads provider says "raw:/storage/..." when it can, and hands out an
            // opaque row id when it cannot. The id is worth one lookup before giving up.
            "com.android.providers.downloads.documents" -> when {
                id.startsWith("raw:") -> File(id.removePrefix("raw:"))
                id.toLongOrNull() != null -> dataColumn(
                    context,
                    ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        id.toLong(),
                    ),
                ) ?: dataColumn(context, uri)
                else -> dataColumn(context, uri)
            }

            // "document:1234" and friends: the media store knows where it put it.
            "com.android.providers.media.documents" -> {
                val (kind, rowId) = id.split(":", limit = 2).takeIf { it.size == 2 }
                    ?: return dataColumn(context, uri)
                val table = when (kind) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                dataColumn(context, table, "_id=?", arrayOf(rowId)) ?: dataColumn(context, uri)
            }

            else -> dataColumn(context, uri)
        }
    }

    /**
     * The provider's own record of where the file is.
     *
     * `_data` is deprecated and providers are free to leave it empty, which is why every
     * caller here treats null as "try the next thing" rather than as an error. When it is
     * populated it is exactly right, and it is populated by most of what people actually
     * have installed.
     */
    private fun dataColumn(
        context: Context,
        uri: Uri,
        selection: String? = null,
        args: Array<String>? = null,
    ): File? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            selection,
            args,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (column < 0) return@use null
            cursor.getString(column)?.takeIf { it.isNotBlank() }?.let(::File)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private inline fun <reified T : Any> Intent.getParcelableExtraCompat(name: String): T? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            getParcelableExtra(name) as? T
        }
}
