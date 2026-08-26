package com.printplace.app.ui.preview

import com.printplace.app.mesh.Vec3
import kotlin.math.abs

data class TangentFrame(val tangent: Vec3, val bitangent: Vec3, val normal: Vec3)

/** Builds a stable right-handed tangent basis for a triangle normal. */
fun tangentFrameFor(normal: Vec3): TangentFrame {
    val n = normal.normalizedOr(Vec3(0f, 0f, 1f))
    val reference = if (abs(n.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)
    val tangent = reference.cross(n).normalizedOr(Vec3(1f, 0f, 0f))
    val bitangent = n.cross(tangent).normalizedOr(Vec3(0f, 1f, 0f))
    return TangentFrame(tangent, bitangent, n)
}
