package com.MyCarApp.modules.facial_recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.MyCarApp.core.OutputObject
import com.MyCarApp.core.PropertyResult
import com.MyCarApp.modules.BaseModule
import java.io.IOException

class JavaFacialRecognitionModule(
    moduleId: String,
    private val context: Context
) : BaseModule(moduleId) {

    private var faceEmbeddingModel: FaceEmbeddingModel? = null
    private var storedEmbedding: Array<FloatArray>? = null // Stores the reference face embedding
    private var lastMatchTriggered = false // Prevents multiple triggers

    init {
        try {
            faceEmbeddingModel = FaceEmbeddingModel(context)
            Log.d("FaceRecognition", "Model loaded successfully.")
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Model load failed.", e)
        }
    }

    override fun execute(input: OutputObject?): OutputObject {
        Log.d("FaceRecognition", "Executing Facial Recognition...")

        val imageName = (input?.additionalData?.get("imageName") as? PropertyResult.Success<String>)?.value
        if (imageName == null) {
            Log.e("FaceRecognition", "No image input provided.")
            return OutputObject(moduleId, "Error: No Image Provided", false, emptyMap())
        }

        return if (storedEmbedding == null) {
            Log.e("FaceRecognition", "Storing reference profile")
            storeReferenceFace(imageName)
            OutputObject(moduleId, "No Reference Face", false, emptyMap())
        } else {
            compareFace(imageName)
        }
    }

    // Process an image for facial recognition (Moved from IntegrationClass)
    fun processImage(imageName: String): OutputObject {
        return execute(
            OutputObject(
                moduleId = moduleId,
                result = "imageInput",
                status = false,
                additionalData = mapOf("imageName" to PropertyResult.Success(imageName))
            )
        )
    }

    // Compare new image with stored reference
    private fun compareFace(imageName: String): OutputObject {
        val newEmbedding = getEmbeddingFromBitmap(loadBitmapFromAssets(imageName))
        if (newEmbedding == null || storedEmbedding == null) {
            Log.e("FaceRecognition", "No valid input for comparison.")
            return OutputObject(moduleId, "NoMatch", false, emptyMap())
        }

        val distance = calculateEuclideanDistance(storedEmbedding!![0], newEmbedding)
        val isMatch = distance < 0.6
        Log.d("FaceRecognition", "Face Distance: $distance -> Match: $isMatch")

        return OutputObject(
            moduleId,
            if (isMatch) "MatchFound" else "NoMatch",
            isMatch,
            if (isMatch) mapOf("personName" to PropertyResult.Success("Authorized User")) else emptyMap()
        )
    }

    // Process live camera frames
    fun processLiveFrame(frame: Bitmap): OutputObject {
        val newEmbedding = getEmbeddingFromBitmap(frame)
        if (newEmbedding == null || storedEmbedding == null) {
            Log.e("FaceRecognition", "No valid face embedding.")
            return OutputObject(moduleId, "NoMatch", false, emptyMap())
        }

        val distance = calculateEuclideanDistance(storedEmbedding!![0], newEmbedding)
        return if (distance < 0.6) {
            OutputObject(moduleId, "MatchFound", true, mapOf("personName" to PropertyResult.Success("Authorized User")))
        } else {
            OutputObject(moduleId, "NoMatch", false, emptyMap())
        }
    }

    // Reset match trigger when stopping
    fun resetMatchTrigger() {
        lastMatchTriggered = false
        Log.d("FaceRecognition", "Match trigger reset.")
    }

    // Get face embedding from bitmap (Reusable logic)
    private fun getEmbeddingFromBitmap(bitmap: Bitmap?): FloatArray? {
        if (bitmap == null) return null
        val processedImage = ImageProcessor.preprocessImage(bitmap)
        return faceEmbeddingModel?.getFaceEmbedding(processedImage)?.get(0)
    }

    // Store a reference face
    private fun storeReferenceFace(imageName: String) {
        storedEmbedding = getEmbeddingFromBitmap(loadBitmapFromAssets(imageName))?.let { arrayOf(it) }
        Log.d("FaceRecognition", "Reference Face Stored -> ${storedEmbedding?.get(0)?.contentToString()}")
    }

    // Calculate Euclidean Distance
    private fun calculateEuclideanDistance(emb1: FloatArray, emb2: FloatArray): Float {
        var sum = 0f
        for (i in emb1.indices) {
            val diff = emb1[i] - emb2[i]
            sum += diff * diff
        }
        return kotlin.math.sqrt(sum)
    }

    // Load image from assets
    private fun loadBitmapFromAssets(imageName: String): Bitmap? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open(imageName)
            Log.d("FaceRecognition", "✅ Loaded image from assets: $imageName")
            BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            Log.e("FaceRecognition", "🚨 Failed to load image: $imageName", e)
            null
        }
    }
}
