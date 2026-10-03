package com.docwarp.scanner.core.model

import android.util.Size

/**
 * Camera Capture Resolution Profiles matching high-end document scanners (e.g. OKEN Scanner).
 * Unit: pixel
 */
data class CameraResolutionOption(
    val title: String,      // e.g. "8M (3264x2448)"
    val tag: String,        // e.g. "8M"
    val size: Size,
    val isDefault: Boolean = false
) {
    companion object {
        val ALL_OPTIONS = listOf(
            CameraResolutionOption("12M (4000x3000)", "12M", Size(4000, 3000)),
            CameraResolutionOption("8M (3264x2448)", "8M", Size(3264, 2448), isDefault = true),
            CameraResolutionOption("6M (3264x1840)", "6M", Size(3264, 1840)),
            CameraResolutionOption("5M (2560x1920)", "5M", Size(2560, 1920)),
            CameraResolutionOption("4M (2304x1728)", "4M", Size(2304, 1728)),
            CameraResolutionOption("4M (2560x1440)", "4M", Size(2560, 1440)),
            CameraResolutionOption("3M (1920x1440)", "3M", Size(1920, 1440)),
            CameraResolutionOption("2M (1920x1088)", "2M", Size(1920, 1088)),
            CameraResolutionOption("2M (1920x1080)", "2M", Size(1920, 1080)),
            CameraResolutionOption("2M (1600x1200)", "2M", Size(1600, 1200)),
            CameraResolutionOption("2M (1440x1088)", "2M", Size(1440, 1088)),
            CameraResolutionOption("2M (1440x1080)", "2M", Size(1440, 1080)),
            CameraResolutionOption("1M (1600x900)", "1M", Size(1600, 900)),
            CameraResolutionOption("1M (1280x960)", "1M", Size(1280, 960))
        )

        val DEFAULT = ALL_OPTIONS[0] // 12M full sensor resolution
    }
}
