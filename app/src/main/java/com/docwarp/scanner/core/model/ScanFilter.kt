package com.docwarp.scanner.core.model

enum class ScanFilter(val displayName: String, val description: String) {
    ORIGINAL_COLOR(
        displayName = "Original Color",
        description = "Untouched high-dynamic RGB scan"
    ),
    MAGIC_COLOR(
        displayName = "Magic Color",
        description = "Shadow-removed with enhanced contrast & vibrance"
    ),
    SAUVOLA_BINARIZED(
        displayName = "Sauvola B&W",
        description = "Pure crisp white paper with deep black text"
    ),
    CRISP_GRAYSCALE(
        displayName = "Crisp Grayscale",
        description = "Smooth 8-bit monochromatic document"
    )
}
