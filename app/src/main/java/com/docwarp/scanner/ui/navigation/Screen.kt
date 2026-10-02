package com.docwarp.scanner.ui.navigation

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Gallery : Screen("gallery")
    data object ManualCrop : Screen("manual_crop/{pageId}") {
        fun createRoute(pageId: String) = "manual_crop/$pageId"
    }
    data object FilterStudio : Screen("filter_studio/{pageId}") {
        fun createRoute(pageId: String) = "filter_studio/$pageId"
    }
    data object PdfExport : Screen("pdf_export")
}
