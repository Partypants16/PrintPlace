package com.printplace.app.importer

import com.printplace.app.model.ModelFormat
import java.io.InputStream

sealed interface FileValidationResult {
    data class Valid(val format: ModelFormat) : FileValidationResult
    data class Invalid(val reason: String) : FileValidationResult
}

/**
 * Cheap, format-agnostic sanity check run right after the user picks a file
 * and before it's copied into app storage. This is deliberately NOT a full
 * parse: it only confirms the extension is supported and that the file's
 * opening bytes are consistent with that extension, so obviously-wrong files
 * (a renamed .jpg, a truncated download, an empty file) are rejected before
 * we spend time copying them. Real geometry parsing/validation happens in
 * the Milestone 2/3 STL and 3MF importers.
 *
 * Never trusts the provider-reported MIME type -- SAF providers frequently
 * report generic or missing MIME types for uncommon extensions like .3mf.
 */
object FileValidator {

    private const val HEADER_PEEK_SIZE = 8
    private const val STL_ASCII_MAGIC = "solid"
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04" -- 3MF is a ZIP package
    private const val STL_BINARY_MIN_SIZE = 84L // 80-byte header + 4-byte triangle count

    fun validate(displayName: String, sizeBytes: Long?, openHeaderStream: () -> InputStream?): FileValidationResult {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        val format = ModelFormat.fromExtension(extension)
            ?: return FileValidationResult.Invalid(
                "Unsupported file type \".${extension.ifBlank { "?" }}\". PrintPlace supports .stl and .3mf files."
            )

        if (sizeBytes != null && sizeBytes <= 0L) {
            return FileValidationResult.Invalid("The selected file is empty.")
        }

        val header = openHeaderStream()?.use { it.readAtMost(HEADER_PEEK_SIZE) }
            ?: return FileValidationResult.Invalid("The selected file could not be opened.")

        return when (format) {
            ModelFormat.STL -> validateStl(header, sizeBytes, format)
            ModelFormat.THREE_MF -> validateThreeMf(header, format)
        }
    }

    private fun validateStl(header: ByteArray, sizeBytes: Long?, format: ModelFormat): FileValidationResult {
        val looksAscii = String(header, Charsets.US_ASCII)
            .trimStart()
            .startsWith(STL_ASCII_MAGIC, ignoreCase = true)
        if (looksAscii) return FileValidationResult.Valid(format)

        // Not ASCII -- must be plausible as binary STL (80-byte header + triangle count + data).
        if (sizeBytes != null && sizeBytes < STL_BINARY_MIN_SIZE) {
            return FileValidationResult.Invalid("This file could not be recognised as a valid STL model.")
        }
        return FileValidationResult.Valid(format)
    }

    private fun validateThreeMf(header: ByteArray, format: ModelFormat): FileValidationResult {
        val isZip = header.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { header[it] == ZIP_MAGIC[it] }
        return if (isZip) {
            FileValidationResult.Valid(format)
        } else {
            FileValidationResult.Invalid("This file does not appear to be a valid 3MF package.")
        }
    }

    private fun InputStream.readAtMost(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var totalRead = 0
        while (totalRead < count) {
            val read = read(buffer, totalRead, count - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        return if (totalRead == count) buffer else buffer.copyOf(totalRead)
    }
}
