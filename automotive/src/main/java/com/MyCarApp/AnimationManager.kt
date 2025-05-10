package com.MyCarApp

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.google.android.filament.utils.KTX1Loader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AnimationManager(private val context: Context, private val surfaceView: SurfaceView) {

    private lateinit var choreographer: Choreographer
    private lateinit var uiHelper: UiHelper
    private lateinit var modelViewer: ModelViewer
    private val frameScheduler = FrameCallback() // Declare frameScheduler

    init {
        initializeFilament() // Call it immediately instead of delaying it
    }


    private fun initializeFilament() {
        Log.d("AnimationManager", "⚡ Initializing Filament...")
    choreographer = Choreographer.getInstance()
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }

        val engine = Engine.create() // Ensure engine is created first
        modelViewer = ModelViewer(surfaceView, engine, uiHelper) // Fixed Engine reference

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            true
        }

        createRenderables()
        createIndirectLight()
        configureViewer()
    }


    private fun createRenderables() {
        val buffer = context.assets.open("CarWorkflow2.glb").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }

        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()
    }

    private fun configureViewer() {
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.renderer.clearOptions = modelViewer.renderer.clearOptions.apply {
            clear = true
        }
        modelViewer.view.apply {
            renderQuality = renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
            dynamicResolutionOptions = dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.MEDIUM
            }
            antiAliasing = View.AntiAliasing.FXAA
            ambientOcclusionOptions = ambientOcclusionOptions.apply {
                enabled = true
            }
            bloomOptions = bloomOptions.apply {
                enabled = true
            }
        }
    }

    private fun createIndirectLight() {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "venetian_crossroads_2k"
        readCompressedAsset("${ibl}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 30_000.0f
        }
        readCompressedAsset("${ibl}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = context.assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    fun playAnimation() {
        Log.d("AnimationManager", "🚀 Playing animation...")
        if (::modelViewer.isInitialized) {  // Ensure modelViewer is initialized
            choreographer.postFrameCallback(frameScheduler)
        } else {
            Log.e("AnimationManager", "🚨 modelViewer is not initialized! Skipping animation.")
        }
    }


    inner class FrameCallback : Choreographer.FrameCallback {
        private var animationStartTime = 0L

        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            val elapsedTimeSeconds = (frameTimeNanos - animationStartTime).toDouble() / 1_000_000_000

            modelViewer.animator?.apply {
                val totalAnimations = animationCount
                if (totalAnimations > 2) {
                    applyAnimation(0, elapsedTimeSeconds.toFloat()) // Human animation
                    updateBoneMatrices()
                    applyAnimation(1, elapsedTimeSeconds.toFloat()) // Door animation
                    applyAnimation(2, elapsedTimeSeconds.toFloat()) // Plane animation
                }
            }

            modelViewer.render(frameTimeNanos)
        }
    }

    fun stopAnimation() {
        choreographer.removeFrameCallback(frameScheduler)
    }
}
