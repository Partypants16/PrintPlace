package com.printplace.app.ui.preview

import com.printplace.app.mesh.BoundingBox
import com.printplace.app.mesh.Vec3
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitCameraTest {

    private val bounds = BoundingBox(Vec3(-10f, -20f, 0f), Vec3(30f, 40f, 50f))

    @Test
    fun `initial pose targets bounding-box center and uses z-up`() {
        val pose = OrbitCamera(bounds).pose()
        assertVec(Vec3(10f, 10f, 25f), pose.target)
        assertVec(Vec3(0f, 0f, 1f), pose.up)
        assertTrue(pose.near > 0.0)
        assertTrue(pose.far > pose.near)
    }

    @Test
    fun `zoom moves closer and clamps away from target`() {
        val camera = OrbitCamera(bounds)
        val original = distance(camera.pose())
        camera.zoom(2f)
        assertTrue(distance(camera.pose()) < original)
        repeat(20) { camera.zoom(100f) }
        assertTrue(distance(camera.pose()) > 0f)
    }

    @Test
    fun `portrait viewport moves farther away to fit the horizontal field of view`() {
        val square = OrbitCamera(bounds).apply { fitToViewport(1000, 1000) }
        val portrait = OrbitCamera(bounds).apply { fitToViewport(500, 1000) }

        assertTrue(distance(portrait.pose()) > distance(square.pose()))
    }

    @Test
    fun `orbit never crosses the z-up poles`() {
        val camera = OrbitCamera(bounds)
        camera.orbit(0f, 100_000f, 1000, 1000)
        val high = camera.pose()
        camera.orbit(0f, -200_000f, 1000, 1000)
        val low = camera.pose()
        assertTrue((high.eye - high.target).z > 0f)
        assertTrue((low.eye - low.target).z < 0f)
        assertTrue(abs((high.eye - high.target).z) < distance(high))
        assertTrue(abs((low.eye - low.target).z) < distance(low))
    }

    @Test
    fun `reset restores the initial pose`() {
        val camera = OrbitCamera(bounds)
        val initial = camera.pose()
        camera.orbit(120f, -80f, 500, 500)
        camera.pan(50f, 20f, 500)
        camera.zoom(3f)
        camera.reset()
        assertVec(initial.eye, camera.pose().eye)
        assertVec(initial.target, camera.pose().target)
    }

    private fun distance(pose: CameraPose) = kotlin.math.sqrt((pose.eye - pose.target).lengthSquared())

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
        assertEquals(expected.z, actual.z, 0.0001f)
    }
}
