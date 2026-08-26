package com.printplace.app.importer.stl

import com.printplace.app.importer.GeometryImportResult
import com.printplace.app.model.DimensionUnit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StlParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fileOf(bytes: ByteArray, name: String = "model.stl"): File =
        tempFolder.newFile(name).apply { writeBytes(bytes) }

    private fun fileOf(text: String, name: String = "model.stl"): File =
        tempFolder.newFile(name).apply { writeText(text, Charsets.US_ASCII) }

    // --- Binary STL ---------------------------------------------------

    @Test
    fun `parses a binary cube with correct triangle count and dimensions`() {
        val file = fileOf(StlFixtures.binaryBytes(StlFixtures.cube(20f)))
        val result = StlParser.parse(file)

        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(12, success.mesh.triangleCount)
        assertEquals(20.0, success.dimensions.widthMm, 1e-4)
        assertEquals(20.0, success.dimensions.heightMm, 1e-4)
        assertEquals(20.0, success.dimensions.depthMm, 1e-4)
        assertEquals(DimensionUnit.MILLIMETER, success.dimensions.sourceUnit)
    }

    @Test
    fun `parses a binary box preserving distinct width, depth and height`() {
        val file = fileOf(StlFixtures.binaryBytes(StlFixtures.box(width = 142f, depth = 76f, height = 48f)))
        val result = StlParser.parse(file) as? GeometryImportResult.Success ?: error("expected Success")

        assertEquals(142.0, result.dimensions.widthMm, 1e-4)
        assertEquals(76.0, result.dimensions.depthMm, 1e-4)
        assertEquals(48.0, result.dimensions.heightMm, 1e-4)
    }

    @Test
    fun `treats a file as binary by size consistency even if its header text says solid`() {
        // Binary STL headers are 80 bytes of arbitrary text -- some exporters put "solid" there.
        // Detection must key off the declared-count-vs-file-size match, not the keyword.
        val file = fileOf(StlFixtures.binaryBytes(StlFixtures.cube(20f), headerText = "solid pretend-ascii"))
        val result = StlParser.parse(file)
        assertTrue(result is GeometryImportResult.Success)
    }

    @Test
    fun `rejects a binary file declaring zero triangles`() {
        val file = fileOf(StlFixtures.binaryBytes(emptyList()))
        val result = StlParser.parse(file)
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("No printable geometry"))
    }

    @Test
    fun `rejects a binary file whose triangle count exceeds the safety cap`() {
        // A genuinely huge fixture (millions of triangles) isn't practical to build in a unit
        // test, so the cap is injected here rather than relying on the real 5M default.
        val file = fileOf(StlFixtures.binaryBytes(StlFixtures.cube(20f))) // 12 triangles
        val result = StlParser.parse(file, maxTriangles = 10)
        val failure = result as? GeometryImportResult.Failure ?: error("expected Failure, got $result")
        assertTrue(failure.reason.contains("too complex", ignoreCase = true))
        assertTrue(failure.reason.contains("12"))
    }

    @Test
    fun `does not misinterpret an ascii file's bytes as a binary triangle count`() {
        // Regression test: bytes 80-83 of any file >= 84 bytes were briefly (mis)read as a
        // binary triangle count before the size-consistency check ran, rejecting nearly every
        // real ASCII STL as "too complex" for an essentially random reason.
        val longAsciiText = StlFixtures.asciiText(StlFixtures.cube(20f) + StlFixtures.cube(5f))
        assertTrue(longAsciiText.length > 84)
        val result = StlParser.parse(fileOf(longAsciiText))
        assertTrue(result is GeometryImportResult.Success)
    }

    // --- ASCII STL ------------------------------------------------------

    @Test
    fun `parses an ascii cube with correct triangle count and dimensions`() {
        val file = fileOf(StlFixtures.asciiText(StlFixtures.cube(20f)))
        val result = StlParser.parse(file)

        val success = result as? GeometryImportResult.Success ?: error("expected Success, got $result")
        assertEquals(12, success.mesh.triangleCount)
        assertEquals(20.0, success.dimensions.widthMm, 1e-4)
        assertEquals(20.0, success.dimensions.heightMm, 1e-4)
        assertEquals(20.0, success.dimensions.depthMm, 1e-4)
    }

    @Test
    fun `parses an ascii box preserving distinct width, depth and height`() {
        val file = fileOf(StlFixtures.asciiText(StlFixtures.box(width = 142f, depth = 76f, height = 48f)))
        val result = StlParser.parse(file) as? GeometryImportResult.Success ?: error("expected Success")

        assertEquals(142.0, result.dimensions.widthMm, 1e-4)
        assertEquals(76.0, result.dimensions.depthMm, 1e-4)
        assertEquals(48.0, result.dimensions.heightMm, 1e-4)
    }

    @Test
    fun `rejects an ascii file with zero facets`() {
        val file = fileOf("solid empty\nendsolid empty\n")
        val result = StlParser.parse(file)
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("No printable geometry"))
    }

    @Test
    fun `rejects text that does not start with solid`() {
        val file = fileOf("this is not an stl file at all\njust some text\n")
        val result = StlParser.parse(file)
        assertTrue(result is GeometryImportResult.Failure)
    }

    // --- Shared edge cases ------------------------------------------------

    @Test
    fun `rejects an empty file`() {
        val file = fileOf(ByteArray(0))
        val result = StlParser.parse(file)
        assertTrue(result is GeometryImportResult.Failure)
        assertTrue((result as GeometryImportResult.Failure).reason.contains("No printable geometry"))
    }

    @Test
    fun `rejects a truncated file that matches neither binary nor ascii shape`() {
        val file = fileOf(ByteArray(50) { it.toByte() })
        val result = StlParser.parse(file)
        assertTrue(result is GeometryImportResult.Failure)
    }
}
