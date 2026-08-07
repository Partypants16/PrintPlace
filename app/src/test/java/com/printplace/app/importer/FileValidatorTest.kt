package com.printplace.app.importer

import com.printplace.app.model.ModelFormat
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileValidatorTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun `rejects unsupported extension`() {
        val result = FileValidator.validate("model.obj", 1_000) { ByteArrayInputStream(bytes(0, 0, 0, 0)) }
        assertTrue(result is FileValidationResult.Invalid)
    }

    @Test
    fun `rejects file with no extension`() {
        val result = FileValidator.validate("model", 1_000) { ByteArrayInputStream(bytes(0, 0, 0, 0)) }
        assertTrue(result is FileValidationResult.Invalid)
    }

    @Test
    fun `rejects empty file`() {
        val result = FileValidator.validate("cube.stl", 0) { ByteArrayInputStream(ByteArray(0)) }
        assertTrue(result is FileValidationResult.Invalid)
    }

    @Test
    fun `rejects unreadable file`() {
        val result = FileValidator.validate("cube.stl", 1_000) { null }
        assertTrue(result is FileValidationResult.Invalid)
    }

    @Test
    fun `accepts ascii stl header`() {
        val header = "solid cube\n".toByteArray(Charsets.US_ASCII)
        val result = FileValidator.validate("cube.stl", header.size.toLong()) { ByteArrayInputStream(header) }
        assertEquals(FileValidationResult.Valid(ModelFormat.STL), result)
    }

    @Test
    fun `accepts ascii stl header case-insensitively`() {
        val header = "SOLID CUBE\n".toByteArray(Charsets.US_ASCII)
        val result = FileValidator.validate("cube.STL", header.size.toLong()) { ByteArrayInputStream(header) }
        assertEquals(FileValidationResult.Valid(ModelFormat.STL), result)
    }

    @Test
    fun `accepts plausible binary stl by size`() {
        val header = ByteArray(80) { 0 } // binary header is opaque, doesn't start with "solid"
        val totalSize = 84L + 50 // header + count + one triangle record
        val result = FileValidator.validate("cube.stl", totalSize) { ByteArrayInputStream(header) }
        assertEquals(FileValidationResult.Valid(ModelFormat.STL), result)
    }

    @Test
    fun `rejects binary-looking stl that is too small to contain a triangle count`() {
        val header = ByteArray(10) { 0 }
        val result = FileValidator.validate("cube.stl", 10) { ByteArrayInputStream(header) }
        assertTrue(result is FileValidationResult.Invalid)
    }

    @Test
    fun `accepts 3mf with zip magic bytes`() {
        val header = bytes(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)
        val result = FileValidator.validate("mount.3mf", 5_000) { ByteArrayInputStream(header) }
        assertEquals(FileValidationResult.Valid(ModelFormat.THREE_MF), result)
    }

    @Test
    fun `rejects 3mf without zip magic bytes`() {
        val header = bytes(0, 0, 0, 0)
        val result = FileValidator.validate("mount.3mf", 5_000) { ByteArrayInputStream(header) }
        assertTrue(result is FileValidationResult.Invalid)
    }
}
