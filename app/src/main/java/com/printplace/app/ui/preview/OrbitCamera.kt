package com.printplace.app.ui.preview

import com.printplace.app.mesh.BoundingBox
import com.printplace.app.mesh.Vec3
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class CameraPose(
    val eye: Vec3,
    val target: Vec3,
    val up: Vec3,
    val near: Double,
    val far: Double,
)

/** Z-up orbit camera whose state is independent of Android and Filament. */
class OrbitCamera(private val bounds: BoundingBox) {

    private val extent = max(max(bounds.width, bounds.depth), bounds.height).coerceAtLeast(MIN_EXTENT)
    private val radius = (sqrt(
        bounds.width * bounds.width + bounds.depth * bounds.depth + bounds.height * bounds.height,
    ) / 2f).coerceAtLeast(MIN_EXTENT)
    private val initialTarget = Vec3(
        (bounds.min.x + bounds.max.x) / 2f,
        (bounds.min.y + bounds.max.y) / 2f,
        (bounds.min.z + bounds.max.z) / 2f,
    )
    private val minimumDistance = extent * MIN_DISTANCE_MULTIPLIER
    private val maximumDistance = extent * MAX_DISTANCE_MULTIPLIER
    private var resetDistance = extent * DEFAULT_DISTANCE_MULTIPLIER

    private var target = initialTarget
    private var yaw = DEFAULT_YAW_RADIANS
    private var pitch = DEFAULT_PITCH_RADIANS
    private var distance = resetDistance

    fun pose(): CameraPose {
        val horizontalDistance = distance * cos(pitch)
        val eye = Vec3(
            target.x + horizontalDistance * cos(yaw),
            target.y + horizontalDistance * sin(yaw),
            target.z + distance * sin(pitch),
        )
        return CameraPose(
            eye = eye,
            target = target,
            up = WORLD_UP,
            near = max(distance * 0.001, extent * 0.0001),
            far = (distance + extent * 20.0f).toDouble(),
        )
    }

    fun orbit(deltaX: Float, deltaY: Float, viewportWidth: Int, viewportHeight: Int) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        yaw -= deltaX / viewportWidth * (2.0 * PI).toFloat()
        pitch = (pitch + deltaY / viewportHeight * PI.toFloat())
            .coerceIn(-MAX_PITCH_RADIANS, MAX_PITCH_RADIANS)
    }

    /** Fits the bounding sphere against the narrower of the vertical and horizontal fields of view. */
    fun fitToViewport(viewportWidth: Int, viewportHeight: Int) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val aspect = viewportWidth.toDouble() / viewportHeight.toDouble()
        val verticalHalfFov = VERTICAL_FOV_RADIANS / 2.0
        val horizontalHalfFov = atan(tan(verticalHalfFov) * aspect)
        val limitingHalfFov = min(verticalHalfFov, horizontalHalfFov)
        resetDistance = (radius / sin(limitingHalfFov) * FIT_MARGIN).toFloat()
            .coerceIn(minimumDistance, maximumDistance)
        distance = resetDistance
    }

    /** A scale above 1 represents a pinch-out and moves the camera closer. */
    fun zoom(scale: Float) {
        if (!scale.isFinite() || scale <= 0f) return
        distance = (distance / scale).coerceIn(minimumDistance, maximumDistance)
    }

    fun pan(deltaX: Float, deltaY: Float, viewportHeight: Int) {
        if (viewportHeight <= 0) return
        val current = pose()
        val forward = (current.target - current.eye).normalizedOr(Vec3(0f, 1f, 0f))
        val right = forward.cross(WORLD_UP).normalizedOr(Vec3(1f, 0f, 0f))
        val screenUp = right.cross(forward).normalizedOr(WORLD_UP)
        val worldPerPixel = (2.0 * distance * tan(VERTICAL_FOV_RADIANS / 2.0) / viewportHeight).toFloat()
        target += right * (-deltaX * worldPerPixel) + screenUp * (deltaY * worldPerPixel)
    }

    fun reset() {
        target = initialTarget
        yaw = DEFAULT_YAW_RADIANS
        pitch = DEFAULT_PITCH_RADIANS
        distance = resetDistance
    }

    companion object {
        const val VERTICAL_FOV_DEGREES = 45.0
        private val VERTICAL_FOV_RADIANS = Math.toRadians(VERTICAL_FOV_DEGREES)
        private const val MIN_EXTENT = 1e-4f
        private const val DEFAULT_DISTANCE_MULTIPLIER = 2.25f
        private const val FIT_MARGIN = 1.15
        private const val MIN_DISTANCE_MULTIPLIER = 0.05f
        private const val MAX_DISTANCE_MULTIPLIER = 100f
        private val DEFAULT_YAW_RADIANS = Math.toRadians(45.0).toFloat()
        private val DEFAULT_PITCH_RADIANS = Math.toRadians(30.0).toFloat()
        private val MAX_PITCH_RADIANS = Math.toRadians(85.0).toFloat()
        private val WORLD_UP = Vec3(0f, 0f, 1f)
    }
}

internal operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
internal operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
internal operator fun Vec3.times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
internal fun Vec3.dot(other: Vec3) = x * other.x + y * other.y + z * other.z
internal fun Vec3.cross(other: Vec3) = Vec3(
    y * other.z - z * other.y,
    z * other.x - x * other.z,
    x * other.y - y * other.x,
)
internal fun Vec3.lengthSquared() = dot(this)
internal fun Vec3.normalizedOr(fallback: Vec3): Vec3 {
    val squared = lengthSquared()
    if (!squared.isFinite() || squared <= 1e-12f) return fallback
    return this * (1f / kotlin.math.sqrt(squared))
}
