package com.printplace.app.importer.stl

import com.printplace.app.importer.DEFAULT_MAX_TRIANGLES
import com.printplace.app.importer.GeometryImportResult
import com.printplace.app.importer.GrowableFloatBuffer
import com.printplace.app.importer.tooComplexFailure
import com.printplace.app.mesh.BoundingBox
import com.printplace.app.mesh.Mesh
import com.printplace.app.mesh.Vec3
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses both binary and ASCII STL into an internal [Mesh]. Pure JVM code --
 * no Android types -- so it can be exercised with plain JUnit tests and
 * synthetic fixtures (see architecture doc section 23).
 *
 * STL carries no unit information; per the project's unit strategy, values
 * are read as-is and the caller is responsible for labelling them millimetres.
 */
object StlParser {

    private const val BINARY_HEADER_SIZE = 80
    private const val BINARY_TRIANGLE_RECORD_SIZE = 50 // 12 (normal) + 36 (3 vertices) + 2 (attribute byte count)
    private const val BINARY_PREFIX_SIZE = BINARY_HEADER_SIZE + 4

    /**
     * [maxTriangles] defaults to the real safety cap; tests override it to a
     * small value so the "too complex" path can be exercised without having
     * to construct multi-hundred-megabyte fixtures.
     */
    fun parse(file: File, maxTriangles: Int = DEFAULT_MAX_TRIANGLES): GeometryImportResult {
        val fileSize = file.length()
        if (fileSize == 0L) {
            return GeometryImportResult.Failure("No printable geometry was found in this file.")
        }

        if (fileSize >= BINARY_PREFIX_SIZE) {
            val declaredCount = readDeclaredTriangleCount(file)
            // A declared count only means anything once the file's actual size confirms
            // it's shaped like a binary STL with that many triangles -- checking the
            // count in isolation would misfire on almost any ASCII file >= 84 bytes,
            // since its arbitrary bytes 80-83 decode to a essentially-random "count".
            if (declaredCount != null && declaredCount >= 0) {
                val expectedSize = BINARY_PREFIX_SIZE + declaredCount * BINARY_TRIANGLE_RECORD_SIZE
                if (expectedSize == fileSize) {
                    if (declaredCount > maxTriangles) return tooComplexFailure(declaredCount)
                    return parseBinary(file, declaredCount.toInt())
                }
            }
        }

        return try {
            BufferedInputStream(file.inputStream()).use { parseAscii(it, maxTriangles) }
        } catch (e: IOException) {
            GeometryImportResult.Failure("This file could not be recognised as a valid STL model.")
        }
    }

    /** Reads just the 84-byte binary prefix to extract the declared triangle count, or null if unreadable. */
    private fun readDeclaredTriangleCount(file: File): Long? {
        val prefix = file.inputStream().use { it.readFully(BINARY_PREFIX_SIZE) } ?: return null
        return ByteBuffer.wrap(prefix, BINARY_HEADER_SIZE, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
    }

    private fun parseBinary(file: File, triangleCount: Int): GeometryImportResult {
        if (triangleCount == 0) {
            return GeometryImportResult.Failure("No printable geometry was found in this file.")
        }

        val vertices = FloatArray(triangleCount * 9)
        val normals = FloatArray(triangleCount * 3)
        var boundingBox: BoundingBox? = null
        val recordBuffer = ByteArray(BINARY_TRIANGLE_RECORD_SIZE)

        try {
            BufferedInputStream(file.inputStream()).use { stream ->
                if (stream.readFully(BINARY_PREFIX_SIZE) == null) {
                    return GeometryImportResult.Failure("This file could not be recognised as a valid STL model.")
                }
                for (t in 0 until triangleCount) {
                    val record = stream.readFully(BINARY_TRIANGLE_RECORD_SIZE, recordBuffer)
                        ?: return GeometryImportResult.Failure("This file could not be recognised as a valid STL model.")
                    val buf = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)

                    normals[t * 3] = buf.float
                    normals[t * 3 + 1] = buf.float
                    normals[t * 3 + 2] = buf.float

                    for (v in 0 until 3) {
                        val x = buf.float
                        val y = buf.float
                        val z = buf.float
                        val base = t * 9 + v * 3
                        vertices[base] = x
                        vertices[base + 1] = y
                        vertices[base + 2] = z
                        val point = Vec3(x, y, z)
                        boundingBox = boundingBox?.expandedBy(point) ?: BoundingBox.startingAt(point)
                    }
                    // Trailing 2-byte attribute count is intentionally ignored (non-standard
                    // colour extensions aren't supported -- see architecture doc section 7/19).
                }
            }
        } catch (e: IOException) {
            return GeometryImportResult.Failure("This file could not be recognised as a valid STL model.")
        }

        val mesh = Mesh(vertices = vertices, normals = normals, triangleCount = triangleCount, boundingBox = requireNotNull(boundingBox))
        return GeometryImportResult.Success(mesh, mesh.boundingBox.asMillimeterDimensions())
    }

    private fun parseAscii(stream: InputStream, maxTriangles: Int): GeometryImportResult {
        val vertexBuffer = GrowableFloatBuffer()
        val normalBuffer = GrowableFloatBuffer()
        var boundingBox: BoundingBox? = null
        var triangleCount = 0
        var sawSolid = false
        var pendingNormal: Triple<Float, Float, Float>? = null

        stream.bufferedReader(Charsets.US_ASCII).useLines { lines ->
            for (rawLine in lines) {
                val tokens = rawLine.trim().split(Regex("\\s+"))
                if (tokens.isEmpty() || tokens[0].isEmpty()) continue
                when (tokens[0].lowercase()) {
                    "solid" -> sawSolid = true
                    "facet" -> {
                        if (tokens.size >= 5 && tokens[1].equals("normal", ignoreCase = true)) {
                            val n = parseFloats(tokens, 2, 3) ?: return GeometryImportResult.Failure(
                                "This file could not be recognised as a valid STL model."
                            )
                            pendingNormal = Triple(n[0], n[1], n[2])
                        }
                    }
                    "vertex" -> {
                        val v = parseFloats(tokens, 1, 3) ?: return GeometryImportResult.Failure(
                            "This file could not be recognised as a valid STL model."
                        )
                        vertexBuffer.add(v[0]); vertexBuffer.add(v[1]); vertexBuffer.add(v[2])
                        val point = Vec3(v[0], v[1], v[2])
                        boundingBox = boundingBox?.expandedBy(point) ?: BoundingBox.startingAt(point)
                    }
                    "endfacet" -> {
                        val normal = pendingNormal ?: Triple(0f, 0f, 0f)
                        normalBuffer.add(normal.first); normalBuffer.add(normal.second); normalBuffer.add(normal.third)
                        triangleCount++
                        pendingNormal = null
                        if (triangleCount > maxTriangles) return tooComplexFailure(triangleCount.toLong())
                    }
                }
            }
        }

        if (!sawSolid) {
            return GeometryImportResult.Failure("This file could not be recognised as a valid STL model.")
        }
        if (triangleCount == 0) {
            return GeometryImportResult.Failure("No printable geometry was found in this file.")
        }

        val mesh = Mesh(
            vertices = vertexBuffer.toFloatArray(),
            normals = normalBuffer.toFloatArray(),
            triangleCount = triangleCount,
            boundingBox = requireNotNull(boundingBox),
        )
        return GeometryImportResult.Success(mesh, mesh.boundingBox.asMillimeterDimensions())
    }

    private fun parseFloats(tokens: List<String>, startIndex: Int, count: Int): FloatArray? {
        if (tokens.size < startIndex + count) return null
        val result = FloatArray(count)
        for (i in 0 until count) {
            result[i] = tokens[startIndex + i].toFloatOrNull() ?: return null
        }
        return result
    }

    private fun InputStream.readFully(size: Int, buffer: ByteArray = ByteArray(size)): ByteArray? {
        var read = 0
        while (read < size) {
            val n = this.read(buffer, read, size - read)
            if (n <= 0) return null
            read += n
        }
        return buffer
    }
}
