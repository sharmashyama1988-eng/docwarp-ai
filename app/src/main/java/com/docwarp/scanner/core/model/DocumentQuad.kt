package com.docwarp.scanner.core.model

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * 4-corner document quad representation in clockwise order:
 * Top-Left, Top-Right, Bottom-Right, Bottom-Left
 */
data class DocumentQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    fun getBoundingBox(): RectF {
        val minX = min(min(topLeft.x, topRight.x), min(bottomRight.x, bottomLeft.x))
        val maxX = max(max(topLeft.x, topRight.x), max(bottomRight.x, bottomLeft.x))
        val minY = min(min(topLeft.y, topRight.y), min(bottomRight.y, bottomLeft.y))
        val maxY = max(max(topLeft.y, topRight.y), max(bottomRight.y, bottomLeft.y))
        return RectF(minX, minY, maxX, maxY)
    }

    fun scale(factorX: Float, factorY: Float): DocumentQuad {
        return DocumentQuad(
            PointF(topLeft.x * factorX, topLeft.y * factorY),
            PointF(topRight.x * factorX, topRight.y * factorY),
            PointF(bottomRight.x * factorX, bottomRight.y * factorY),
            PointF(bottomLeft.x * factorX, bottomLeft.y * factorY)
        )
    }

    fun toNormalized(frameWidth: Int, frameHeight: Int): DocumentQuad {
        val fw = frameWidth.toFloat().coerceAtLeast(1.0f)
        val fh = frameHeight.toFloat().coerceAtLeast(1.0f)
        return DocumentQuad(
            PointF(topLeft.x / fw, topLeft.y / fh),
            PointF(topRight.x / fw, topRight.y / fh),
            PointF(bottomRight.x / fw, bottomRight.y / fh),
            PointF(bottomLeft.x / fw, bottomLeft.y / fh)
        )
    }

    fun toTargetResolution(targetWidth: Int, targetHeight: Int): DocumentQuad {
        val tw = targetWidth.toFloat()
        val th = targetHeight.toFloat()
        return DocumentQuad(
            PointF(topLeft.x * tw, topLeft.y * th),
            PointF(topRight.x * tw, topRight.y * th),
            PointF(bottomRight.x * tw, bottomRight.y * th),
            PointF(bottomLeft.x * tw, bottomLeft.y * th)
        )
    }

    companion object {
        fun fromFloatArray(coords: FloatArray): DocumentQuad {
            require(coords.size >= 8) { "DocumentQuad requires 8 coordinates" }
            return DocumentQuad(
                PointF(coords[0], coords[1]),
                PointF(coords[2], coords[3]),
                PointF(coords[4], coords[5]),
                PointF(coords[6], coords[7])
            )
        }

        fun defaultForDimensions(width: Int, height: Int, marginPercent: Float = 0.06f): DocumentQuad {
            val mx = width * marginPercent
            val my = height * marginPercent
            return DocumentQuad(
                PointF(mx, my),
                PointF(width - mx, my),
                PointF(width - mx, height - my),
                PointF(mx, height - my)
            )
        }
    }
}
