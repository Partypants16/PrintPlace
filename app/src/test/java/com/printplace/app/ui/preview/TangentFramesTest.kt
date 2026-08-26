package com.printplace.app.ui.preview

import com.printplace.app.mesh.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class TangentFramesTest {

    @Test
    fun `basis is normalized orthogonal and right handed for arbitrary normal`() {
        val frame = tangentFrameFor(Vec3(1f, 2f, 3f))
        assertEquals(1f, frame.tangent.lengthSquared(), 0.0001f)
        assertEquals(1f, frame.bitangent.lengthSquared(), 0.0001f)
        assertEquals(1f, frame.normal.lengthSquared(), 0.0001f)
        assertEquals(0f, frame.tangent.dot(frame.bitangent), 0.0001f)
        assertEquals(0f, frame.tangent.dot(frame.normal), 0.0001f)
        assertEquals(0f, frame.bitangent.dot(frame.normal), 0.0001f)
        assertEquals(1f, frame.tangent.cross(frame.bitangent).dot(frame.normal), 0.0001f)
    }

    @Test
    fun `zero normal falls back to positive z`() {
        val frame = tangentFrameFor(Vec3(0f, 0f, 0f))
        assertEquals(Vec3(0f, 0f, 1f), frame.normal)
        assertEquals(1f, frame.tangent.cross(frame.bitangent).dot(frame.normal), 0.0001f)
    }
}
