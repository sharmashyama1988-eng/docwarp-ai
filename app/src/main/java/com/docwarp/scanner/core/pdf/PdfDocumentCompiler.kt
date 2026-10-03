package com.docwarp.scanner.core.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.docwarp.scanner.core.model.CompressionQuality
import com.docwarp.scanner.core.model.PageSizeOption
import com.docwarp.scanner.core.model.ScanDocument
import com.docwarp.scanner.core.model.ScannedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * High-performance PDF Document Compiler supporting:
 * - Page standard scaling (A4, US Letter, Fit to Image)
 * - Multi-tier compression profiles (Low, Medium, Maximum Archival)
 * - Android Scoped Storage MediaStore exports for clean file distribution
 */
class PdfDocumentCompiler(private val context: Context) {

    suspend fun compileDocumentToPdf(
        document: ScanDocument,
        onProgress: (Float) -> Unit = {}
    ): Uri? = withContext(Dispatchers.IO) {
        if (document.pages.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()
        val totalPages = document.pages.size

        try {
            for ((index, page) in document.pages.withIndex()) {
                val pageFile = if (page.processedImageFile.exists()) {
                    page.processedImageFile
                } else {
                    page.rawImageFile
                }

                if (!pageFile.exists()) continue

                // Decode bitmap with downsampling if memory requires
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                var sourceBmp = BitmapFactory.decodeFile(pageFile.absolutePath, options) ?: continue

                // Apply rotation if modified in gallery
                if (page.rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(sourceBmp, 0, 0, sourceBmp.width, sourceBmp.height, matrix, true)
                    if (rotated !== sourceBmp) {
                        sourceBmp.recycle()
                        sourceBmp = rotated
                    }
                }

                // Compress according to profile
                val compressedBmp = applyCompressionProfile(sourceBmp, document.compression)
                if (compressedBmp !== sourceBmp) {
                    sourceBmp.recycle()
                    sourceBmp = compressedBmp
                }

                // Page dimension calculation
                val (pageW, pageH) = determinePageDimensions(sourceBmp, document.pageSize)
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = pdfPage.canvas

                // Fill clean white background
                canvas.drawColor(Color.WHITE)

                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }

                // Margin of 18 points (0.25 inch) for standard sheets
                val margin = if (document.pageSize == PageSizeOption.FIT_TO_IMAGE) 0 else 18
                val destW = (pageW - margin * 2).coerceAtLeast(1)
                val destH = (pageH - margin * 2).coerceAtLeast(1)

                val srcAspect = sourceBmp.width.toFloat() / sourceBmp.height.toFloat()
                val destAspect = destW.toFloat() / destH.toFloat()

                val renderW: Float
                val renderH: Float
                if (srcAspect > destAspect) {
                    renderW = destW.toFloat()
                    renderH = destW / srcAspect
                } else {
                    renderH = destH.toFloat()
                    renderW = destH * srcAspect
                }

                val left = margin + (destW - renderW) / 2.0f
                val top = margin + (destH - renderH) / 2.0f
                val destRect = Rect(
                    left.toInt(),
                    top.toInt(),
                    (left + renderW).toInt(),
                    (top + renderH).toInt()
                )

                canvas.drawBitmap(sourceBmp, null, destRect, paint)
                pdfDocument.finishPage(pdfPage)
                sourceBmp.recycle()

                onProgress((index + 1).toFloat() / totalPages)
            }

            writePdfToStorage(pdfDocument, document.title, document.folder)
        } catch (e: Exception) {
            Log.e(TAG, "Error compiling PDF: ${e.message}", e)
            null
        } finally {
            pdfDocument.close()
        }
    }

    private fun determinePageDimensions(bitmap: Bitmap, sizeOption: PageSizeOption): Pair<Int, Int> {
        return when (sizeOption) {
            PageSizeOption.A4 -> Pair(595, 842)
            PageSizeOption.US_LETTER -> Pair(612, 792)
            PageSizeOption.FIT_TO_IMAGE -> {
                // Scale down 300DPI to 72DPI points representation
                val factor = 72.0f / 300.0f
                val pw = (bitmap.width * factor).toInt().coerceAtLeast(100)
                val ph = (bitmap.height * factor).toInt().coerceAtLeast(100)
                Pair(pw, ph)
            }
        }
    }

    private fun applyCompressionProfile(bitmap: Bitmap, profile: CompressionQuality): Bitmap {
        if (profile == CompressionQuality.MAXIMUM) return bitmap

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, profile.jpegQuality, baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
    }

    private fun writePdfToStorage(pdfDocument: PdfDocument, title: String, folder: String): Uri? {
        val sanitizedFolder = folder.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "${title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.pdf"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/DocWarp/$sanitizedFolder"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                Log.d(TAG, "PDF saved to Scoped Storage in $relativePath: $uri")
                uri
            } else {
                null
            }
        } else {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DocWarp/$sanitizedFolder").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(targetDir, fileName)
            FileOutputStream(targetFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            Log.d(TAG, "PDF saved to File: ${targetFile.absolutePath}")
            Uri.fromFile(targetFile)
        }
    }

    companion object {
        private const val TAG = "PdfDocumentCompiler"
    }
}
