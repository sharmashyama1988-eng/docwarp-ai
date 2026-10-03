package com.docwarp.scanner.core.model

/**
 * Organization folders for scanned documents and PDF archives.
 */
data class DocumentFolder(
    val id: String,
    val name: String,
    val iconEmoji: String = "📁"
) {
    companion object {
        val DEFAULT_FOLDERS = listOf(
            DocumentFolder("general", "General", "📄"),
            DocumentFolder("work", "Work & Office", "💼"),
            DocumentFolder("personal", "Personal", "🔒"),
            DocumentFolder("receipts", "Receipts & Bills", "🧾"),
            DocumentFolder("books", "Books & Study", "📚"),
            DocumentFolder("id_cards", "ID Cards & Passes", "🪪")
        )

        val DEFAULT = DEFAULT_FOLDERS[0]
    }
}
