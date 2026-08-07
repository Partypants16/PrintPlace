package com.printplace.app.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

/**
 * Filename and size as reported by the document provider that handed us
 * [uri] (local storage, Downloads, Drive, ...). Providers are not required to
 * populate every [OpenableColumns], so this falls back to [DocumentFile]
 * when the cursor comes back empty -- we never trust a single source blindly
 * per the "don't assume metadata is always correct" requirement.
 */
data class SafDocumentInfo(
    val displayName: String,
    val sizeBytes: Long?,
)

fun queryDocumentInfo(context: Context, uri: Uri): SafDocumentInfo? {
    var name: String? = null
    var size: Long? = null

    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

    if (name == null || size == null) {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (name == null) name = documentFile?.name
        if (size == null) documentFile?.length()?.let { if (it > 0) size = it }
    }

    val resolvedName = name ?: uri.lastPathSegment ?: return null
    return SafDocumentInfo(displayName = resolvedName, sizeBytes = size)
}
