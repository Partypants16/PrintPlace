package com.printplace.app.importer.stl

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds small synthetic STL byte streams so parser tests don't depend on real fixture files on disk. */
object StlFixtures {

    data class Triangle(
        val normal: Triple<Float, Float, Float>,
        val v1: Triple<Float, Float, Float>,
        val v2: Triple<Float, Float, Float>,
        val v3: Triple<Float, Float, Float>,
    )

    /** A `size` x `size` x `size` cube, corner at the origin, as 12 triangles (2 per face). */
    fun cube(size: Float): List<Triangle> = box(size, size, size)

    /** A `width` x `depth` x `height` box (x/y/z respectively), corner at the origin. */
    fun box(width: Float, depth: Float, height: Float): List<Triangle> {
        val v0 = Triple(0f, 0f, 0f)
        val v1 = Triple(width, 0f, 0f)
        val v2 = Triple(width, depth, 0f)
        val v3 = Triple(0f, depth, 0f)
        val v4 = Triple(0f, 0f, height)
        val v5 = Triple(width, 0f, height)
        val v6 = Triple(width, depth, height)
        val v7 = Triple(0f, depth, height)
        val n = Triple(0f, 0f, 0f) // exact normal direction doesn't matter for these tests

        return listOf(
            Triangle(n, v0, v1, v2), Triangle(n, v0, v2, v3), // bottom
            Triangle(n, v4, v6, v5), Triangle(n, v4, v7, v6), // top
            Triangle(n, v0, v5, v1), Triangle(n, v0, v4, v5), // front
            Triangle(n, v3, v2, v6), Triangle(n, v3, v6, v7), // back
            Triangle(n, v0, v3, v7), Triangle(n, v0, v7, v4), // left
            Triangle(n, v1, v6, v2), Triangle(n, v1, v5, v6), // right
        )
    }

    fun binaryBytes(triangles: List<Triangle>, headerText: String = "PrintPlace test fixture"): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteArray(80)
        headerText.toByteArray(Charsets.US_ASCII).copyInto(header, 0, 0, minOf(headerText.length, 80))
        out.write(header)
        out.write(littleEndianInt(triangles.size))
        for (t in triangles) {
            out.write(littleEndianFloats(t.normal))
            out.write(littleEndianFloats(t.v1))
            out.write(littleEndianFloats(t.v2))
            out.write(littleEndianFloats(t.v3))
            out.write(byteArrayOf(0, 0)) // attribute byte count
        }
        return out.toByteArray()
    }

    fun asciiText(triangles: List<Triangle>, solidName: String = "fixture"): String = buildString {
        appendLine("solid $solidName")
        for (t in triangles) {
            appendLine("  facet normal ${t.normal.first} ${t.normal.second} ${t.normal.third}")
            appendLine("    outer loop")
            appendLine("      vertex ${t.v1.first} ${t.v1.second} ${t.v1.third}")
            appendLine("      vertex ${t.v2.first} ${t.v2.second} ${t.v2.third}")
            appendLine("      vertex ${t.v3.first} ${t.v3.second} ${t.v3.third}")
            appendLine("    endloop")
            appendLine("  endfacet")
        }
        appendLine("endsolid $solidName")
    }

    private fun littleEndianInt(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun littleEndianFloats(triple: Triple<Float, Float, Float>): ByteArray =
        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(triple.first).putFloat(triple.second).putFloat(triple.third)
            .array()
}
