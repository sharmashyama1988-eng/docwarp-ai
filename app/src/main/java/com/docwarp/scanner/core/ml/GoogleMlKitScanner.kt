package com.docwarp.scanner.core.ml

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Enterprise Google ML Kit Integration:
 * 1. Google Play Services Document Scanner (Hardware-accelerated edge detection, dewarping, and filter engine)
 * 2. High-Accuracy On-Device Latin OCR Text Recognition for "To Text" mode
 */
class GoogleMlKitScanner(private val context: Context) {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Builds and launches the official Google ML Kit Document Scanner Intent.
     * Allows full multi-page scanning with built-in Google neural filters,
     * gallery import, auto-crop, and PDF export.
     */
    fun getScannerClient(pageLimit: Int = 100): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        return GmsDocumentScanning.getClient(options)
    }

    /**
     * High-speed on-device OCR: Extracts full text, paragraphs, and blocks from a scanned page.
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit OCR failed: ${e.message}", e)
                    continuation.resume("")
                }
        } catch (e: Exception) {
            Log.e(TAG, "OCR exception: ${e.message}", e)
            continuation.resume("")
        }
    }

    fun close() {
        try {
            textRecognizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing text recognizer: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GoogleMlKitScanner"
    }
}
