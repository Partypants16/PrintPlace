package com.printplace.app.importer.threemf

import com.printplace.app.mesh.Vec3

/**
 * A 3MF affine transform: a 3x3 linear part plus a translation, applied to a
 * row vector as `v' = v * linear + translation`.
 *
 * Per the 3MF core spec, a `transform` attribute is 12 space-separated
 * numbers listing the linear part's three rows (m00 m01 m02, m10 m11 m12,
 * m20 m21 m22) followed by the translation (m30 m31 m32) -- i.e. a row-major
 * 4x3 matrix with translation in the last row, not the last column as in
 * the more common column-vector convention.
 */
internal class ThreeMfTransform private constructor(
    private val linear: FloatArray, // row-major: [0..2]=row0, [3..5]=row1, [6..8]=row2
    private val translation: Vec3,
) {
    fun apply(v: Vec3): Vec3 = Vec3(
        x = v.x * linear[0] + v.y * linear[3] + v.z * linear[6] + translation.x,
        y = v.x * linear[1] + v.y * linear[4] + v.z * linear[7] + translation.y,
        z = v.x * linear[2] + v.y * linear[5] + v.z * linear[8] + translation.z,
    )

    companion object {
        val IDENTITY = ThreeMfTransform(
            linear = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            translation = Vec3(0f, 0f, 0f),
        )

        /** Parses a `transform` attribute value. Blank/absent means identity; malformed returns null. */
        fun parse(raw: String): ThreeMfTransform? {
            if (raw.isBlank()) return IDENTITY
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size != 12) return null
            val values = FloatArray(12)
            for (i in 0 until 12) {
                values[i] = parts[i].toFloatOrNull() ?: return null
            }
            return ThreeMfTransform(
                linear = values.copyOfRange(0, 9),
                translation = Vec3(values[9], values[10], values[11]),
            )
        }
    }
}
