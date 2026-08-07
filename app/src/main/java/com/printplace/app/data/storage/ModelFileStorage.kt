package com.printplace.app.data.storage

import android.content.Context
import android.net.Uri
import com.printplace.app.model.ModelFormat
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Owns the on-disk copies of imported model files. Once a file has been
 * copied here, PrintPlace no longer depends on the originating SAF Uri or
 * its (often single-shot) read permission -- the app can keep working with
 * the model even if the user deletes the original from Drive/Downloads.
 */
class ModelFileStorage(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    /** Copies the picked document into app storage, returning the new file's absolute path. */
    @Throws(IOException::class)
    fun copyIntoStorage(sourceUri: Uri, modelId: String, format: ModelFormat): String {
        val destination = File(modelsDir, "$modelId.${format.extension}")
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open an input stream for $sourceUri")
        input.use { source ->
            destination.outputStream().use { dest -> source.copyTo(dest) }
        }
        return destination.absolutePath
    }

    fun delete(storedFilePath: String) {
        File(storedFilePath).delete()
    }

    fun newModelId(): String = UUID.randomUUID().toString()
}
