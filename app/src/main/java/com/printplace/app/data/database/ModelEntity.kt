package com.printplace.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.printplace.app.model.DimensionUnit
import com.printplace.app.model.ImportedModel
import com.printplace.app.model.ModelDimensions
import com.printplace.app.model.ModelFormat
import com.printplace.app.model.ProcessingStatus
import java.time.Instant

/**
 * Room persists only metadata/paths -- never model bytes (see architecture
 * doc section 4). The actual STL/3MF file lives under app-controlled storage
 * at [storedFilePath]; this row just points at it.
 */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val format: ModelFormat,
    val storedFilePath: String,
    val importedAtEpochMillis: Long,
    val fileSizeBytes: Long,
    val widthMm: Double?,
    val heightMm: Double?,
    val depthMm: Double?,
    val dimensionSourceUnit: DimensionUnit?,
    val processingStatus: ProcessingStatus,
    val isSelected: Boolean,
    val errorMessage: String?,
)

fun ModelEntity.toDomain(): ImportedModel {
    val dimensions = if (widthMm != null && heightMm != null && depthMm != null && dimensionSourceUnit != null) {
        ModelDimensions(widthMm, heightMm, depthMm, dimensionSourceUnit)
    } else {
        null
    }
    return ImportedModel(
        id = id,
        displayName = displayName,
        format = format,
        storedFilePath = storedFilePath,
        importedAt = Instant.ofEpochMilli(importedAtEpochMillis),
        fileSizeBytes = fileSizeBytes,
        dimensions = dimensions,
        processingStatus = processingStatus,
        isSelected = isSelected,
        errorMessage = errorMessage,
    )
}
