package com.printplace.app.data.database

import com.printplace.app.model.DimensionUnit
import com.printplace.app.model.ModelFormat
import com.printplace.app.model.ProcessingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelEntityMappingTest {

    private fun baseEntity() = ModelEntity(
        id = "abc-123",
        displayName = "phone_stand.stl",
        format = ModelFormat.STL,
        storedFilePath = "/data/models/abc-123.stl",
        importedAtEpochMillis = 1_700_000_000_000,
        fileSizeBytes = 4_096,
        widthMm = null,
        heightMm = null,
        depthMm = null,
        dimensionSourceUnit = null,
        processingStatus = ProcessingStatus.IMPORTED,
        isSelected = false,
        errorMessage = null,
    )

    @Test
    fun `entity without dimensions maps to null dimensions`() {
        val domain = baseEntity().toDomain()
        assertNull(domain.dimensions)
        assertEquals("phone_stand.stl", domain.displayName)
        assertEquals(ModelFormat.STL, domain.format)
    }

    @Test
    fun `entity with all dimension fields maps to a populated dimensions object`() {
        val entity = baseEntity().copy(
            widthMm = 82.0,
            heightMm = 96.0,
            depthMm = 121.0,
            dimensionSourceUnit = DimensionUnit.MILLIMETER,
        )
        val domain = entity.toDomain()
        requireNotNull(domain.dimensions)
        assertEquals(82.0, domain.dimensions.widthMm, 0.0)
        assertEquals(96.0, domain.dimensions.heightMm, 0.0)
        assertEquals(121.0, domain.dimensions.depthMm, 0.0)
        assertEquals(DimensionUnit.MILLIMETER, domain.dimensions.sourceUnit)
    }

    @Test
    fun `entity with partial dimension fields maps to null dimensions`() {
        val entity = baseEntity().copy(widthMm = 82.0, heightMm = 96.0)
        assertNull(entity.toDomain().dimensions)
    }
}
