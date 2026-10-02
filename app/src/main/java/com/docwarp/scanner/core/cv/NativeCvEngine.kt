package com.docwarp.scanner.core.cv

import android.graphics.Bitmap
import android.util.Log
import com.docwarp.scanner.core.model.DocumentQuad

/**
 * JNI wrapper interface for high-performance OpenCV native C++ operations
 */
object NativeCvEngine {
    private const val TAG = "NativeCvEngine"

    init {
        try {
            System.loadLibrary("cv_engine")
            Log.d(TAG, "Native library 'cv_engine' successfully loaded.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load 'cv_engine' native library: ${e.message}")
        }
    }

    // Native JNI functions
    @JvmStatic
    external fun detectDocumentEdges(bitmap: Bitmap, minAreaRatio: Float): FloatArray?

    @JvmStatic
    external fun cropAndWarp(srcBitmap: Bitmap, quadCoords: FloatArray): Bitmap?

    @JvmStatic
    external fun dewarpPageZucker(srcBitmap: Bitmap): Bitmap?

    @JvmStatic
    external fun detectSpineX(srcBitmap: Bitmap): Int

    @JvmStatic
    external fun splitPages(srcBitmap: Bitmap, splitX: Int): Array<Bitmap>?

    @JvmStatic
    external fun sauvolaBinarize(srcBitmap: Bitmap, k: Double, windowSize: Int): Bitmap?

    @JvmStatic
    external fun inpaintTelea(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double): Bitmap?

    @JvmStatic
    external fun computeFrameMotion(currBitmap: Bitmap, prevBitmap: Bitmap, thresholdVal: Int): Float

    @JvmStatic
    external fun processDocumentFileNative(
        inputFilePath: String,
        outputDir: String,
        normalizedQuadCoords: FloatArray?,
        doDewarp: Boolean,
        doDualSplit: Boolean,
        doBinarize: Boolean,
        sauvolaK: Double,
        sauvolaWindow: Int
    ): Array<NativePageResult>?

    // High-level Kotlin helper extensions
    fun findDocumentQuad(bitmap: Bitmap, minAreaRatio: Float = 0.10f): DocumentQuad? {
        val coords = detectDocumentEdges(bitmap, minAreaRatio) ?: return null
        return if (coords.size >= 8) {
            DocumentQuad.fromFloatArray(coords)
        } else {
            null
        }
    }

    fun cropAndWarpQuad(srcBitmap: Bitmap, quad: DocumentQuad): Bitmap? {
        return cropAndWarp(srcBitmap, quad.toFloatArray())
    }
}
