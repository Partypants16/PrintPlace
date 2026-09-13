package com.printplace.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.printplace.app.data.database.ModelDao
import com.printplace.app.data.database.ModelEntity
import com.printplace.app.data.database.toDomain
import com.printplace.app.data.storage.ModelFileStorage
import com.printplace.app.data.storage.queryDocumentInfo
import com.printplace.app.importer.FileValidationResult
import com.printplace.app.importer.FileValidator
import com.printplace.app.importer.GeometryImportResult
import com.printplace.app.importer.GeometryImporters
import com.printplace.app.model.ImportedModel
import com.printplace.app.model.ModelDimensions
import com.printplace.app.model.ModelFormat
import com.printplace.app.model.ProcessingStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "ModelRepository"

class ModelRepositoryImpl(
    private val appContext: Context,
    private val dao: ModelDao,
    private val storage: ModelFileStorage,
) : ModelRepository {

    override fun observeModels(): Flow<List<ImportedModel>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getModel(id: String): ImportedModel? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun importModel(sourceUri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val docInfo = queryDocumentInfo(appContext, sourceUri)
            ?: return@withContext ImportOutcome.Failed("The selected file could not be opened.")

        val validation = FileValidator.validate(docInfo.displayName, docInfo.sizeBytes) {
            appContext.contentResolver.openInputStream(sourceUri)
        }
        val format = when (validation) {
            is FileValidationResult.Invalid -> return@withContext ImportOutcome.Rejected(validation.reason)
            is FileValidationResult.Valid -> validation.format
        }

        val modelId = storage.newModelId()
        val storedPath = try {
            storage.copyIntoStorage(sourceUri, modelId, format)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy $sourceUri into app storage", e)
            return@withContext ImportOutcome.Failed("The selected file could not be opened.")
        }

        val actualSizeBytes = File(storedPath).length()
        val geometry = resolveGeometry(format, File(storedPath))
        val entity = ModelEntity(
            id = modelId,
            displayName = docInfo.displayName,
            format = format,
            storedFilePath = storedPath,
            importedAtEpochMillis = System.currentTimeMillis(),
            fileSizeBytes = actualSizeBytes,
            widthMm = geometry.dimensions?.widthMm,
            heightMm = geometry.dimensions?.heightMm,
            depthMm = geometry.dimensions?.depthMm,
            dimensionSourceUnit = geometry.dimensions?.sourceUnit,
            processingStatus = geometry.status,
            isSelected = false,
            errorMessage = geometry.errorMessage,
        )
        dao.insert(entity)
        ImportOutcome.Success(entity.toDomain())
    }

    /** A format with no registered geometry importer leaves dimensions pending, not failed. */
    private fun resolveGeometry(format: ModelFormat, file: File): GeometryResolution {
        val parse = GeometryImporters.forFormat(format)
            ?: return GeometryResolution(ProcessingStatus.IMPORTED, dimensions = null, errorMessage = null)
        return when (val result = parse(file)) {
            is GeometryImportResult.Success ->
                GeometryResolution(ProcessingStatus.READY, result.dimensions, errorMessage = null)
            is GeometryImportResult.Failure -> {
                Log.w(TAG, "Geometry import failed for ${file.absolutePath}: ${result.reason}")
                GeometryResolution(ProcessingStatus.FAILED, dimensions = null, result.reason)
            }
        }
    }

    private data class GeometryResolution(
        val status: ProcessingStatus,
        val dimensions: ModelDimensions?,
        val errorMessage: String?,
    )

    override suspend fun selectModel(id: String) = withContext(Dispatchers.IO) {
        dao.selectExclusively(id)
    }

    override suspend fun deleteModel(id: String) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        storage.delete(entity.storedFilePath)
        dao.delete(entity)
    }
}
