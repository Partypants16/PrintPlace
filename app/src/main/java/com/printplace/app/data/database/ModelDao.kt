package com.printplace.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM models ORDER BY importedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getById(id: String): ModelEntity?

    @Insert
    suspend fun insert(entity: ModelEntity)

    @Update
    suspend fun update(entity: ModelEntity)

    @Delete
    suspend fun delete(entity: ModelEntity)

    @Query("UPDATE models SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE models SET isSelected = 1 WHERE id = :id")
    suspend fun markSelected(id: String)

    /**
     * Enforces the "only one active design" rule from the architecture doc:
     * selecting a model always clears any previous selection first, in the
     * same transaction, so two rows can never both read isSelected = true.
     */
    @Transaction
    suspend fun selectExclusively(id: String) {
        clearSelection()
        markSelected(id)
    }
}
