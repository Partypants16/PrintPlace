package com.printplace.app.data.repository

import android.net.Uri
import com.printplace.app.model.ImportedModel
import kotlinx.coroutines.flow.Flow

sealed interface ImportOutcome {
    data class Success(val model: ImportedModel) : ImportOutcome
    /** File was read but didn't pass [com.printplace.app.importer.FileValidator]. */
    data class Rejected(val reason: String) : ImportOutcome
    /** Unexpected I/O failure (provider hiccup, disk full, etc). */
    data class Failed(val reason: String) : ImportOutcome
}

/**
 * Single entry point the UI layer uses to read and mutate the model library.
 * Wraps the Room DAO, [com.printplace.app.data.storage.ModelFileStorage] and
 * [com.printplace.app.importer.FileValidator] so none of those are visible
 * to ViewModels directly.
 */
interface ModelRepository {

    fun observeModels(): Flow<List<ImportedModel>>

    suspend fun getModel(id: String): ImportedModel?

    suspend fun importModel(sourceUri: Uri): ImportOutcome

    /** Marks [id] as the sole [com.printplace.app.model.ImportedModel.isSelected] model. */
    suspend fun selectModel(id: String)

    suspend fun deleteModel(id: String)
}
