package com.printplace.app.mesh

/**
 * Axis-aligned bounding box of a mesh, in the file's native units (millimetres
 * for the formats PrintPlace supports). This is the source of truth for the
 * real-world dimensions shown in the library and, later, the mm-to-metre
 * conversion feeding ARCore.
 */
data class BoundingBox(val min: Vec3, val max: Vec3) {
    // STL/3MF files are authored Z-up (Z is the print's vertical/build axis),
    // so height maps to Z and depth (front-to-back) maps to Y -- not the other
    // way around.
    val width: Float get() = max.x - min.x
    val depth: Float get() = max.y - min.y
    val height: Float get() = max.z - min.z

    /** Accumulates [point] into a running bounding box; starting point is the box's own corners. */
    fun expandedBy(point: Vec3): BoundingBox = BoundingBox(
        min = Vec3(minOf(min.x, point.x), minOf(min.y, point.y), minOf(min.z, point.z)),
        max = Vec3(maxOf(max.x, point.x), maxOf(max.y, point.y), maxOf(max.z, point.z)),
    )

    companion object {
        fun startingAt(point: Vec3): BoundingBox = BoundingBox(point, point)
    }
}
