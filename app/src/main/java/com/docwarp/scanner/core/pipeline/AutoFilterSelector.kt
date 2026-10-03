package com.docwarp.scanner.core.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import com.docwarp.scanner.core.model.ScanFilter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Intelligent Scene & Illumination Analyzer.
 * Inspects captured document bitmap via fast pixel sampling (step = 10-16)
 * to automatically determine the optimal scan enhancement filter:
 * - High-chroma documents (charts, stamps, colored text) -> MAGIC_COLOR
 * - Yellowed or aged paper -> YELLOW_REMOVER (Paper Bright)
 * - Heavy uneven shadows or fold gradients -> SHADOW_KILLER
 * - Low-contrast faint text / pencil -> SUPER_CONTRAST (Deep Ink)
 * - Standard printed document -> EBOOK_CLEAN (Crisp eBook White)
 */
object AutoFilterSelector {

    fun determineOptimalFilter(bitmap: Bitmap): ScanFilter {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return ScanFilter.EBOOK_CLEAN

        val step = max(8, max(width, height) / 80)
        var totalLuminance = 0.0
        var totalSaturation = 0.0
        var totalRed = 0.0
        var totalGreen = 0.0
        var totalBlue = 0.0
        var sampleCount = 0

        // Quadrant luminance tracking to detect shadows
        var q1Lum = 0.0; var q1Count = 0
        var q2Lum = 0.0; var q2Count = 0
        var q3Lum = 0.0; var q3Count = 0
        var q4Lum = 0.0; var q4Count = 0

        val midX = width / 2
        val midY = height / 2

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Perceived luminance (ITU-R BT.601)
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = (maxC - minC).toDouble()

                totalLuminance += lum
                totalSaturation += sat
                totalRed += r
                totalGreen += g
                totalBlue += b
                sampleCount++

                if (x < midX && y < midY) {
                    q1Lum += lum; q1Count++
                } else if (x >= midX && y < midY) {
                    q2Lum += lum; q2Count++
                } else if (x < midX && y >= midY) {
                    q3Lum += lum; q3Count++
                } else {
                    q4Lum += lum; q4Count++
                }
            }
        }

        if (sampleCount == 0) return ScanFilter.EBOOK_CLEAN

        val avgLum = totalLuminance / sampleCount
        val avgSat = totalSaturation / sampleCount
        val avgR = totalRed / sampleCount
        val avgG = totalGreen / sampleCount
        val avgB = totalBlue / sampleCount

        // 1. Color Document Check: If noticeable color variance exists, retain color
        if (avgSat > 25.0) {
            return ScanFilter.MAGIC_COLOR
        }

        // 2. Yellow/Aged Paper Check: Warm cast where Red & Green significantly exceed Blue
        val yellowRatio = if (avgB > 10.0) ((avgR + avgG) / (2.0 * avgB)) else 1.0
        if (yellowRatio > 1.18 && avgLum > 110.0) {
            return ScanFilter.YELLOW_REMOVER
        }

        // 3. Shadow / Uneven Illumination Check: High quadrant variance
        val q1Avg = if (q1Count > 0) q1Lum / q1Count else avgLum
        val q2Avg = if (q2Count > 0) q2Lum / q2Count else avgLum
        val q3Avg = if (q3Count > 0) q3Lum / q3Count else avgLum
        val q4Avg = if (q4Count > 0) q4Lum / q4Count else avgLum

        val quadLums = doubleArrayOf(q1Avg, q2Avg, q3Avg, q4Avg)
        val quadMax = quadLums.maxOrNull() ?: avgLum
        val quadMin = quadLums.minOrNull() ?: avgLum
        val quadDelta = quadMax - quadMin

        if (quadDelta > 45.0) {
            return ScanFilter.SHADOW_KILLER
        }

        // 4. Low-contrast / Faded ink check
        if (avgLum < 100.0) {
            return ScanFilter.SUPER_CONTRAST
        }

        // Default: Pure book white background with ink-sharp text
        return ScanFilter.EBOOK_CLEAN
    }
}
