package com.printplace.app.importer.threemf

import com.printplace.app.importer.GeometryImportResult
import com.printplace.app.model.DimensionUnit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThreeMfParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fileOf(bytes: ByteArray, name: String = "model.3mf"): File =
        tempFolder.newFile(name).apply { writeBytes(bytes) }

    private fun successOf(
        unit: String?,
        objects: List<ThreeMfFixtures.ObjectDef>,
        items: List<ThreeMfFixtures.ItemDef>,
        includeRels: Boolean = true,
        maxTriangles: Int = 5_000_000,
    ): GeometryImportResult {
        val xml = ThreeMfFixtures.modelXml(unit, objects, items)
        val file = fileOf(ThreeMfFixtures.zipBytes(xml, includeRels))
        return ThreeMfParser.parse(file, maxTriangles)
    }

    @Test
    fun `parses a single-object box with default millimeter unit`() {
        val result = successOf(
            unit = null,
            objects = listOf(ThreeMfFixtures.box("1", width = 142f, depth = 76f, height = 48f)),
            items = listOf(ThreeMfFixtures.ItemDef(objectId = "1")),
        )
        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(12, success.mesh.triangleCount)
        assertEquals(142.0, success.dimensions.widthMm, 1e-4)
        assertEquals(76.0, success.dimensions.depthMm, 1e-4)
        assertEquals(48.0, success.dimensions.heightMm, 1e-4)
        assertEquals(DimensionUnit.MILLIMETER, success.dimensions.sourceUnit)
    }

    @Test
    fun `converts centimeter units to millimetres`() {
        val result = successOf(
            unit = "centimeter",
            objects = listOf(ThreeMfFixtures.cube("1", size = 2f)),
            items = listOf(ThreeMfFixtures.ItemDef(objectId = "1")),
        )
        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(20.0, success.dimensions.widthMm, 1e-4)
        assertEquals(20.0, success.dimensions.heightMm, 1e-4)
        assertEquals(20.0, success.dimensions.depthMm, 1e-4)
        assertEquals(DimensionUnit.CENTIMETER, success.dimensions.sourceUnit)
    }

    @Test
    fun `converts inch units to millimetres`() {
        val result = successOf(
            unit = "inch",
            objects = listOf(ThreeMfFixtures.cube("1", size = 1f)),
            items = listOf(ThreeMfFixtures.ItemDef(objectId = "1")),
        )
        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(25.4, success.dimensions.widthMm, 1e-3)
        assertEquals(DimensionUnit.INCH, success.dimensions.sourceUnit)
    }

    @Test
    fun `applies a build item's scaling transform to the resolved dimensions`() {
        // Scale 2x uniformly: linear part is 2*identity, translation zero.
        val result = successOf(
            unit = null,
            objects = listOf(ThreeMfFixtures.cube("1", size = 10f)),
            items = listOf(ThreeMfFixtures.ItemDef(objectId = "1", transform = "2 0 0 0 2 0 0 0 2 0 0 0")),
        )
        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(20.0, success.dimensions.widthMm, 1e-4)
        assertEquals(20.0, success.dimensions.heightMm, 1e-4)
        assertEquals(20.0, success.dimensions.depthMm, 1e-4)
    }

    @Test
    fun `applies a build item's translation without changing dimensions`() {
        val result = successOf(
            unit = null,
            objects = listOf(ThreeMfFixtures.cube("1", size = 10f)),
            items = listOf(ThreeMfFixtures.ItemDef(objectId = "1", transform = "1 0 0 0 1 0 0 0 1 100 200 300")),
        )
        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(10.0, success.dimensions.widthMm, 1e-4)
        assertEquals(10.0, success.dimensions.heightMm, 1e-4)
        assertEquals(10.0, success.dimensions.depthMm, 1e-4)
    }

    @Test
    fun `resolves the model part via the rels part`() {
        val xml = ThreeMfFixtures.modelXml(null, listOf(ThreeMfFixtures.cube("1", 5f)), listOf(ThreeMfFixtures.ItemDef("1")))
        val file = fileOf(ThreeMfFixtures.zipBytes(xml, includeRels = true, modelPath = "3D/unusual-name.model"))
        val result = ThreeMfParser.parse(file)
        assertTrue(result is GeometryImportResult.Success)
    }

    @Test
    fun `falls back to the conventional model path when rels is missing`() {
        val xml = ThreeMfFixtures.modelXml(null, listOf(ThreeMfFixtures.cube("1", 5f)), listOf(ThreeMfFixtures.ItemDef("1")))
        val file = fileOf(ThreeMfFixtures.zipBytes(xml, includeRels = false))
        val result = ThreeMfParser.parse(file)
        assertTrue(result is GeometryImportResult.Success)
    }

    @Test
    fun `rejects an unrecognised unit rather than guessing`() {
        val result = successOf(
            unit = "banana",
            objects = listOf(ThreeMfFixtures.cube("1", 5f)),
            items = listOf(ThreeMfFixtures.ItemDef("1")),
        )
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("not currently supported"))
    }

    @Test
    fun `rejects a file with no objects`() {
        val result = successOf(unit = null, objects = emptyList(), items = emptyList())
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("No printable geometry"))
    }

    @Test
    fun `skips a build item referencing an unknown object and fails gracefully if nothing resolves`() {
        val result = successOf(
            unit = null,
            objects = listOf(ThreeMfFixtures.cube("1", 5f)),
            items = listOf(ThreeMfFixtures.ItemDef(objectId = "does-not-exist")),
        )
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("No printable geometry"))
    }

    @Test
    fun `treats a components-only object as unsupported and fails gracefully rather than crashing`() {
        val componentsObject = ThreeMfFixtures.ObjectDef(
            id = "1",
            rawContent = "<components><component objectid=\"2\"/></components>",
        )
        val xml = ThreeMfFixtures.modelXml(null, listOf(componentsObject), listOf(ThreeMfFixtures.ItemDef("1")))
        val result = ThreeMfParser.parse(fileOf(ThreeMfFixtures.zipBytes(xml)))
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("No printable geometry"))
    }

    @Test
    fun `rejects a file that is not a zip at all`() {
        val result = ThreeMfParser.parse(fileOf("not a zip file".toByteArray()))
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("valid 3MF package"))
    }

    @Test
    fun `rejects a triangle count exceeding the safety cap`() {
        // 3MF triangles are counted as they stream in (unlike STL binary, which knows the total
        // up front), so the cap trips as soon as the count exceeds the limit -- one triangle past
        // maxTriangles, not at the object's real total.
        val result = successOf(
            unit = null,
            objects = listOf(ThreeMfFixtures.cube("1", 5f)), // 12 triangles
            items = listOf(ThreeMfFixtures.ItemDef("1")),
            maxTriangles = 10,
        )
        val failure = result as? GeometryImportResult.Failure ?: error("expected Failure, got $result")
        assertTrue(failure.reason.contains("too complex", ignoreCase = true))
        assertTrue(failure.reason.contains("11"))
    }
}
