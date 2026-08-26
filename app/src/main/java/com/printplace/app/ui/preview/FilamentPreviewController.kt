package com.printplace.app.ui.preview

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.printplace.app.mesh.Mesh
import java.nio.ByteBuffer

/** Owns all Filament objects for one preview and confines them to Android's main thread. */
class FilamentPreviewController(context: Context, mesh: Mesh) {

    private val appContext = context.applicationContext
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val orbitCamera = OrbitCamera(mesh.boundingBox)
    private val displayHelper = DisplayHelper(appContext)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer = Choreographer.getInstance()

    private val skybox: Skybox
    private val material: Material
    private val materialInstance: MaterialInstance
    private val meshResources: FilamentMeshResources
    private val lightEntity: Int

    private var swapChain: SwapChain? = null
    private var surfaceView: SurfaceView? = null
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var running = false
    private var destroyed = false
    private var loggedFirstFrame = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || destroyed) return
            if (uiHelper.isReadyToRender) {
                swapChain?.let { chain ->
                    if (renderer.beginFrame(chain, frameTimeNanos)) {
                        renderer.render(view)
                        renderer.endFrame()
                        if (!loggedFirstFrame) {
                            Log.d(TAG, "Rendered first preview frame at ${viewportWidth}x$viewportHeight")
                            loggedFirstFrame = true
                        }
                    }
                }
            }
            choreographer.postFrameCallback(this)
        }
    }

    private val surfaceCallback = object : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            Log.d(TAG, "Preview surface attached")
            swapChain?.let(engine::destroySwapChain)
            swapChain = engine.createSwapChain(surface)
            surfaceView?.display?.let { displayHelper.attach(renderer, it) }
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            Log.d(TAG, "Preview surface resized to ${width}x$height")
            viewportWidth = width.coerceAtLeast(1)
            viewportHeight = height.coerceAtLeast(1)
            view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
            orbitCamera.fitToViewport(viewportWidth, viewportHeight)
            updateCamera()
            FilamentHelper.synchronizePendingFrames(engine)
        }
    }

    init {
        uiHelper.renderCallback = surfaceCallback
        view.camera = camera
        view.scene = scene

        skybox = Skybox.Builder().color(0.12f, 0.15f, 0.2f, 1f).build(engine)
        scene.skybox = skybox

        val materialPayload = readAsset("materials/model.filamat")
        material = Material.Builder()
            .payload(materialPayload, materialPayload.remaining())
            .build(engine)
        materialInstance = material.createInstance().apply {
            setParameter("baseColor", Colors.RgbType.SRGB, 0.25f, 0.65f, 0.95f)
            setParameter("roughness", 0.55f)
            setParameter("metallic", 0.0f)
            setDoubleSided(true)
        }
        meshResources = FilamentMeshBuilder.build(engine, mesh, materialInstance)
        scene.addEntity(meshResources.entity)

        lightEntity = EntityManager.get().create()
        val (red, green, blue) = Colors.cct(5_500f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(red, green, blue)
            .intensity(120_000f)
            .direction(-0.4f, -0.6f, -1f)
            .castShadows(false)
            .build(engine, lightEntity)
        scene.addEntity(lightEntity)
        camera.setExposure(11f, 1f / 125f, 100f)
        updateCamera()
    }

    fun attach(surfaceView: SurfaceView) {
        check(!destroyed)
        this.surfaceView = surfaceView
        uiHelper.attachTo(surfaceView)
    }

    fun start() {
        if (destroyed || running) return
        running = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun orbit(deltaX: Float, deltaY: Float) {
        orbitCamera.orbit(deltaX, deltaY, viewportWidth, viewportHeight)
        updateCamera()
    }

    fun pan(deltaX: Float, deltaY: Float) {
        orbitCamera.pan(deltaX, deltaY, viewportHeight)
        updateCamera()
    }

    fun zoom(scale: Float) {
        orbitCamera.zoom(scale)
        updateCamera()
    }

    fun resetCamera() {
        orbitCamera.reset()
        updateCamera()
    }

    fun destroy() {
        if (destroyed) return
        stop()
        uiHelper.detach()
        scene.removeEntity(meshResources.entity)
        scene.removeEntity(lightEntity)
        meshResources.destroy(engine)
        engine.destroyEntity(lightEntity)
        EntityManager.get().destroy(lightEntity)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
        engine.destroySkybox(skybox)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
        surfaceView = null
        destroyed = true
    }

    private fun updateCamera() {
        val pose = orbitCamera.pose()
        val aspect = viewportWidth.toDouble() / viewportHeight.toDouble()
        camera.setProjection(
            OrbitCamera.VERTICAL_FOV_DEGREES,
            aspect,
            pose.near,
            pose.far,
            Camera.Fov.VERTICAL,
        )
        camera.lookAt(
            pose.eye.x.toDouble(), pose.eye.y.toDouble(), pose.eye.z.toDouble(),
            pose.target.x.toDouble(), pose.target.y.toDouble(), pose.target.z.toDouble(),
            pose.up.x.toDouble(), pose.up.y.toDouble(), pose.up.z.toDouble(),
        )
    }

    private fun readAsset(name: String): ByteBuffer {
        val bytes = appContext.assets.open(name).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
    }

    companion object {
        private const val TAG = "FilamentPreview"

        init {
            Filament.init()
        }
    }
}
