package com.docwarp.scanner.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Multi-page document wrapper containing ordered pages and export preferences.
 */
data class ScanDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Scan_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}",
    val pages: List<ScannedPage> = emptyList(),
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val compression: CompressionQuality = CompressionQuality.MEDIUM,
    val folder: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)
