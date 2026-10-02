package com.docwarp.scanner.core.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.docwarp.scanner.core.cv.NativeCvEngine
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

/**
 * ONNX Runtime Manager for deep-learning document rectification and LaMa inpainting.
 * Auto-falls back to native C++ OpenCV algorithms if weights are not packaged.
 */
class OnnxModelManager(private val context: Context) {

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var docScannerSession: OrtSession? = null
    private var lamaSession: OrtSession? = null

    private var hasDocScanner = false
    private var hasLama = false

    init {
        initializeSessions()
    }

    private fun initializeSessions() {
        val docFile = resolveModelFile("docscanner.onnx")
        if (docFile != null) {
            try {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    try {
                        addNnapi() // Qualcomm Hexagon / MediaTek APU / Tensor TPU Silicon Execution
                        Log.d(TAG, "NNAPI Silicon Hardware Acceleration enabled for DocScanner.")
                    } catch (e: Exception) {
                        Log.i(TAG, "NNAPI not supported on this device, using ARM Neon CPU.")
                    }
                }
                docScannerSession = ortEnvironment.createSession(docFile.absolutePath, opts)
                hasDocScanner = true
                Log.d(TAG, "DocScanner ONNX session initialized.")
            } catch (e: Exception) {
                Log.w(TAG, "DocScanner ONNX init failed: ${e.message}")
            }
        }

        val lamaFile = resolveModelFile("lama_fp16.onnx") ?: resolveModelFile("lama_fp32.onnx")
        if (lamaFile != null) {
            try {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    try {
                        addNnapi() // Qualcomm Hexagon / MediaTek APU / Tensor TPU Silicon Execution
                        Log.d(TAG, "NNAPI Silicon Hardware Acceleration enabled for LaMa.")
                    } catch (e: Exception) {
                        Log.i(TAG, "NNAPI not supported on this device, using ARM Neon CPU.")
                    }
                }
                lamaSession = ortEnvironment.createSession(lamaFile.absolutePath, opts)
                hasLama = true
                Log.d(TAG, "LaMa Inpainting ONNX session initialized.")
            } catch (e: Exception) {
                Log.w(TAG, "LaMa ONNX init failed: ${e.message}")
            }
        }
    }

    fun dewarp(inputBitmap: Bitmap): Bitmap {
        if (hasDocScanner && docScannerSession != null) {
            try {
                val dewarped = runDocScanner(inputBitmap)
                if (dewarped != null) return dewarped
            } catch (e: Exception) {
                Log.e(TAG, "DocScanner inference failed, falling back to Zucker C++: ${e.message}")
            }
        }
        return NativeCvEngine.dewarpPageZucker(inputBitmap) ?: inputBitmap
    }

    fun inpaint(inputBitmap: Bitmap, maskBitmap: Bitmap?): Bitmap {
        if (maskBitmap == null) return inputBitmap

        if (hasLama && lamaSession != null) {
            try {
                val inpainted = runLama(inputBitmap, maskBitmap)
                if (inpainted != null) return inpainted
            } catch (e: Exception) {
                Log.e(TAG, "LaMa inference failed, falling back to Telea C++: ${e.message}")
            }
        }
        return NativeCvEngine.inpaintTelea(inputBitmap, maskBitmap, 3.0) ?: inputBitmap
    }

    private fun runDocScanner(inputBitmap: Bitmap): Bitmap? {
        val session = docScannerSession ?: return null
        val targetSize = 256
        val scaled = Bitmap.createScaledBitmap(inputBitmap, targetSize, targetSize, true)

        val floatBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)
        val pixels = IntArray(targetSize * targetSize)
        scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

        val channelSize = targetSize * targetSize
        for (i in 0 until channelSize) {
            val c = pixels[i]
            floatBuffer.put(i, (((c shr 16) and 0xFF) / 127.5f) - 1.0f)
            floatBuffer.put(channelSize + i, (((c shr 8) and 0xFF) / 127.5f) - 1.0f)
            floatBuffer.put(2 * channelSize + i, ((c and 0xFF) / 127.5f) - 1.0f)
        }
        floatBuffer.rewind()

        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        val tensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape)

        return try {
            val output = session.run(mapOf(inputName to tensor))
            val rawOutput = output.get(0).value as Array<Array<Array<FloatArray>>>
            applyDisplacementGrid(inputBitmap, rawOutput[0], targetSize)
        } finally {
            tensor.close()
            scaled.recycle()
        }
    }

    private fun applyDisplacementGrid(src: Bitmap, grid: Array<Array<FloatArray>>, dim: Int): Bitmap {
        val origW = src.width
        val origH = src.height
        val outBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)

        val gridX = grid[0]
        val gridY = grid[1]

        val outPixels = IntArray(origW * origH)
        val srcPixels = IntArray(origW * origH)
        src.getPixels(srcPixels, 0, origW, 0, 0, origW, origH)

        val scaleX = (dim - 1).toFloat() / (origW - 1)
        val scaleY = (dim - 1).toFloat() / (origH - 1)

        for (y in 0 until origH) {
            val gy = (y * scaleY).toInt().coerceIn(0, dim - 1)
            for (x in 0 until origW) {
                val gx = (x * scaleX).toInt().coerceIn(0, dim - 1)

                val normX = gridX[gy][gx]
                val normY = gridY[gy][gx]

                val sampleX = (((normX + 1.0f) * 0.5f) * (origW - 1)).toInt().coerceIn(0, origW - 1)
                val sampleY = (((normY + 1.0f) * 0.5f) * (origH - 1)).toInt().coerceIn(0, origH - 1)

                outPixels[y * origW + x] = srcPixels[sampleY * origW + sampleX]
            }
        }

        outBitmap.setPixels(outPixels, 0, origW, 0, 0, origW, origH)
        return outBitmap
    }

    private fun runLama(inputBitmap: Bitmap, maskBitmap: Bitmap): Bitmap? {
        val session = lamaSession ?: return null
        val targetDim = 512

        val scaledImg = Bitmap.createScaledBitmap(inputBitmap, targetDim, targetDim, true)
        val scaledMask = Bitmap.createScaledBitmap(maskBitmap, targetDim, targetDim, true)

        val imgPixels = IntArray(targetDim * targetDim)
        val maskPixels = IntArray(targetDim * targetDim)
        scaledImg.getPixels(imgPixels, 0, targetDim, 0, 0, targetDim, targetDim)
        scaledMask.getPixels(maskPixels, 0, targetDim, 0, 0, targetDim, targetDim)

        val imgBuffer = FloatBuffer.allocate(1 * 3 * targetDim * targetDim)
        val maskBuffer = FloatBuffer.allocate(1 * 1 * targetDim * targetDim)

        val channelSize = targetDim * targetDim
        for (i in 0 until channelSize) {
            val c = imgPixels[i]
            imgBuffer.put(i, ((c shr 16) and 0xFF) / 255.0f)
            imgBuffer.put(channelSize + i, ((c shr 8) and 0xFF) / 255.0f)
            imgBuffer.put(2 * channelSize + i, (c and 0xFF) / 255.0f)

            val m = maskPixels[i]
            val maskVal = if (((m shr 16) and 0xFF) > 128) 1.0f else 0.0f
            maskBuffer.put(i, maskVal)
        }
        imgBuffer.rewind()
        maskBuffer.rewind()

        val imgTensor = OnnxTensor.createTensor(ortEnvironment, imgBuffer, longArrayOf(1, 3, targetDim.toLong(), targetDim.toLong()))
        val maskTensor = OnnxTensor.createTensor(ortEnvironment, maskBuffer, longArrayOf(1, 1, targetDim.toLong(), targetDim.toLong()))

        return try {
            val inputNames = session.inputNames.toList()
            val inputs = mapOf(inputNames[0] to imgTensor, inputNames[1] to maskTensor)
            val output = session.run(inputs)
            val outTensor = output.get(0).value as Array<Array<Array<FloatArray>>>

            val reconstructed = tensorToBitmap(outTensor[0], targetDim)
            val fullReconstructed = Bitmap.createScaledBitmap(reconstructed, inputBitmap.width, inputBitmap.height, true)

            compositeInpainted(inputBitmap, fullReconstructed, maskBitmap)
        } finally {
            imgTensor.close()
            maskTensor.close()
            scaledImg.recycle()
            scaledMask.recycle()
        }
    }

    private fun tensorToBitmap(channels: Array<Array<FloatArray>>, dim: Int): Bitmap {
        val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(dim * dim)
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                val r = (channels[0][y][x].coerceIn(0.0f, 1.0f) * 255).toInt()
                val g = (channels[1][y][x].coerceIn(0.0f, 1.0f) * 255).toInt()
                val b = (channels[2][y][x].coerceIn(0.0f, 1.0f) * 255).toInt()
                pixels[y * dim + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(pixels, 0, dim, 0, 0, dim, dim)
        return bmp
    }

    private fun compositeInpainted(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val maskPixels = IntArray(mask.width * mask.height)
        mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)

        val origPixels = IntArray(original.width * original.height)
        val inpaintPixels = IntArray(inpainted.width * inpainted.height)
        original.getPixels(origPixels, 0, original.width, 0, 0, original.width, original.height)
        inpainted.getPixels(inpaintPixels, 0, inpainted.width, 0, 0, inpainted.width, inpainted.height)

        for (i in origPixels.indices) {
            val m = (maskPixels[i] shr 16) and 0xFF
            if (m > 128) {
                origPixels[i] = inpaintPixels[i]
            }
        }

        result.setPixels(origPixels, 0, original.width, 0, 0, original.width, original.height)
        return result
    }

    private fun resolveModelFile(name: String): File? {
        val file = File(context.filesDir, name)
        if (file.exists() && file.length() > 0) return file

        val candidates = listOf(name, "models/$name")
        for (path in candidates) {
            try {
                context.assets.open(path).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                if (file.exists() && file.length() > 0) return file
            } catch (e: Exception) {
                // Continue searching
            }
        }
        return null
    }

    fun close() {
        try {
            docScannerSession?.close()
            lamaSession?.close()
            ortEnvironment.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "OnnxModelManager"
    }
}
