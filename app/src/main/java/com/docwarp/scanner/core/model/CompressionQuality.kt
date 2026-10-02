package com.docwarp.scanner.core.model

enum class CompressionQuality(
    val title: String,
    val subtitle: String,
    val jpegQuality: Int
) {
    LOW(
        title = "Low (Small Size, Fast Share)",
        subtitle = "Optimized for WhatsApp & quick email attachments",
        jpegQuality = 55
    ),
    MEDIUM(
        title = "Medium (Balanced)",
        subtitle = "Optimal balance between crisp clarity and file size",
        jpegQuality = 80
    ),
    MAXIMUM(
        title = "Maximum (Archive Quality)",
        subtitle = "Lossless archival clarity for contracts & formal documents",
        jpegQuality = 98
    )
}
