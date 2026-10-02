package com.docwarp.scanner.core.model

import java.io.File
import java.util.UUID

/**
 * Represents a single captured and processed document page.
 */
data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val pageNumber: Int,
    val rawImageFile: File,
    val processedImageFile: File,
    val cropQuad: DocumentQuad? = null,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.SAUVOLA_BINARIZED,
    val eraseFingers: Boolean = true,
    val flattenCurvature: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
