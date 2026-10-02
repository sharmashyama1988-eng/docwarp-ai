package com.docwarp.scanner.core.model

enum class PageSizeOption(
    val title: String,
    val widthPoints: Int,
    val heightPoints: Int
) {
    A4("A4 (210 x 297 mm)", 595, 842),
    US_LETTER("US Letter (8.5 x 11 in)", 612, 792),
    FIT_TO_IMAGE("Fit to Image Bounds", 0, 0)
}
