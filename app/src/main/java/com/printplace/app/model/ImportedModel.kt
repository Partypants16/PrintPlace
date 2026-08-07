package com.printplace.app.model

import java.time.Instant

/**
 * UI/domain-facing representation of a model in the library. Deliberately
 * separate from the Room entity so the persistence schema can evolve without
 * every call site needing to know about `@Entity`/`@ColumnInfo`.
 */
data class ImportedModel(
    val id: String,
    val displayName: String,
    val format: ModelFormat,
    val storedFilePath: String,
    val importedAt: Instant,
    val fileSizeBytes: Long,
    val dimensions: ModelDimensions?,
    val processingStatus: ProcessingStatus,
    val isSelected: Boolean,
    val errorMessage: String?,
)
