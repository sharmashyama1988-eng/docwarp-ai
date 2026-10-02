package com.docwarp.scanner.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max

/**
 * Hand landmark extractor using Google MediaPipe Tasks Vision.
 * Detects thumbs and fingertips occluding document margins and produces an 8-bit binary mask
 * (0 = page, 255 = finger) for inpainting.
 */
class MediaPipeHandMasker(private val context: Context) {

    private var handLandmarker: HandLandmarker? = null
    private var isInitialized = false

    init {
        initMediaPipe()
    }

    private fun initMediaPipe() {
        try {
            val modelPath = findModelAssetPath()
            if (modelPath != null) {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelPath)
                    .build()

                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()

                handLandmarker = HandLandmarker.createFromOptions(context, options)
                isInitialized = true
                Log.d(TAG, "MediaPipe HandLandmarker initialized successfully using $modelPath.")
            } else {
                Log.w(TAG, "hand_landmarker.task not found in assets. Operating in bypass mode.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe: ${e.message}")
        }
    }

    private fun findModelAssetPath(): String? {
        val rootAssets = context.assets.list("") ?: emptyArray()
        if (rootAssets.contains("hand_landmarker.task")) return "hand_landmarker.task"

        val modelsAssets = try { context.assets.list("models") ?: emptyArray() } catch (e: Exception) { emptyArray() }
        if (modelsAssets.contains("hand_landmarker.task")) return "models/hand_landmarker.task"

        return null
    }

    fun generateFingerMask(inputBitmap: Bitmap): Bitmap? {
        val landmarker = handLandmarker ?: return null
        if (!isInitialized) return null

        try {
            val mpImage: MPImage = BitmapImageBuilder(inputBitmap).build()
            val result: HandLandmarkerResult = landmarker.detect(mpImage)

            val landmarks = result.landmarks()
            if (landmarks.isNullOrEmpty()) return null

            val width = inputBitmap.width
            val height = inputBitmap.height
            val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(maskBitmap)
            canvas.drawColor(Color.BLACK)

            val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            val baseRadius = max(20.0f, minOf(width, height) * 0.032f)

            for (hand in landmarks) {
                val thumbIndices = listOf(1, 2, 3, 4)
                val indexIndices = listOf(5, 6, 7, 8)
                val middleIndices = listOf(9, 10, 11, 12)

                drawPhalangeCapsule(canvas, hand, thumbIndices, width, height, baseRadius * 1.3f, fingerPaint)
                drawPhalangeCapsule(canvas, hand, indexIndices, width, height, baseRadius, fingerPaint)
                drawPhalangeCapsule(canvas, hand, middleIndices, width, height, baseRadius, fingerPaint)

                // Extra safety circle on fingertips
                val thumbTip = hand[4]
                canvas.drawCircle(thumbTip.x() * width, thumbTip.y() * height, baseRadius * 1.5f, fingerPaint)

                val indexTip = hand[8]
                canvas.drawCircle(indexTip.x() * width, indexTip.y() * height, baseRadius * 1.3f, fingerPaint)
            }

            return maskBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error generating finger mask: ${e.message}")
            return null
        }
    }

    private fun drawPhalangeCapsule(
        canvas: Canvas,
        hand: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        indices: List<Int>,
        width: Int,
        height: Int,
        radius: Float,
        paint: Paint
    ) {
        val strokePaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path()
        for (i in 0 until indices.size - 1) {
            val p1 = hand[indices[i]]
            val p2 = hand[indices[i + 1]]

            path.reset()
            path.moveTo(p1.x() * width, p1.y() * height)
            path.lineTo(p2.x() * width, p2.y() * height)
            canvas.drawPath(path, strokePaint)
            canvas.drawCircle(p2.x() * width, p2.y() * height, radius, paint)
        }
    }

    fun close() {
        try {
            handLandmarker?.close()
            handLandmarker = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MediaPipe: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MediaPipeHandMasker"
    }
}
