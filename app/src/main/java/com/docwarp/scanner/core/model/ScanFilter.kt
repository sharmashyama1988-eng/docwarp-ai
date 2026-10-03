package com.docwarp.scanner.core.model

enum class ScanFilter(
    val id: Int,
    val displayName: String,
    val description: String,
    val isBeta: Boolean = false
) {
    NO_SHADOW(
        id = 9,
        displayName = "No shadow",
        description = "Removes deep shadows & uneven lighting",
        isBeta = true
    ),
    ORIGINAL(
        id = 0,
        displayName = "Original",
        description = "Untouched high-dynamic RGB scan"
    ),
    LIGHTEN(
        id = 8,
        displayName = "Lighten",
        description = "Whitens paper & brightens document"
    ),
    MAGIC_COLOR(
        id = 3,
        displayName = "Magic Color",
        description = "Vibrant colors with shadow removal"
    ),
    EBOOK_CLEAN(
        id = 2,
        displayName = "Clean eBook",
        description = "Laser-sharp ink on pure zero-noise paper"
    ),
    GRAYSCALE_SMOOTH(
        id = 7,
        displayName = "Grayscale",
        description = "Smooth 8-bit monochromatic document"
    ),
    BW(
        id = 4,
        displayName = "B&W",
        description = "High-contrast clean black & white"
    ),
    ECO(
        id = 6,
        displayName = "Eco",
        description = "Laser ink darkening for faded scans"
    ),
    AUTO_BEST(
        id = 1,
        displayName = "⚡ Auto Smart",
        description = "Smart automatic scene & paper enhancement"
    ),
    SHARP_DOCUMENT(
        id = 5,
        displayName = "Sharp Doc",
        description = "Micro-edge unsharp clarity"
    ),
    BLUEPRINT(
        id = 10,
        displayName = "Blueprint",
        description = "Inverted schematic / blueprint mode"
    );

    companion object {
        // Complete backward-compatibility aliases
        val ORIGINAL_COLOR: ScanFilter get() = ORIGINAL
        val SAUVOLA_BINARIZED: ScanFilter get() = EBOOK_CLEAN
        val SAUVOLA_BW: ScanFilter get() = BW
        val CRISP_GRAYSCALE: ScanFilter get() = GRAYSCALE_SMOOTH
        val SUPER_CONTRAST: ScanFilter get() = ECO
        val YELLOW_REMOVER: ScanFilter get() = LIGHTEN
        val SHADOW_KILLER: ScanFilter get() = NO_SHADOW

        // Recommended default filter for camera & studio
        val DEFAULT: ScanFilter get() = EBOOK_CLEAN

        fun fromId(id: Int): ScanFilter {
            return entries.find { it.id == id } ?: EBOOK_CLEAN
        }
    }
}
