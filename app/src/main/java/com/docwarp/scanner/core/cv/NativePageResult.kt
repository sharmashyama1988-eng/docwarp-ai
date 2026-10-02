package com.docwarp.scanner.core.cv

import android.graphics.Bitmap

/**
 * Result returned directly from C++ native pipeline execution without heap pressure.
 */
data class NativePageResult(
    val outputPath: String,
    val thumbnailBitmap: Bitmap,
    val width: Int,
    val height: Int
)
