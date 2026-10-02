package com.docwarp.scanner.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.core.model.ScanDocument
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import com.docwarp.scanner.ui.theme.HapticFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DocumentGalleryViewModel(
    application: Application,
    private val repository: DocumentScanRepository
) : AndroidViewModel(application) {

    private val haptics = HapticFeedbackManager(application)

    val documentState: StateFlow<ScanDocument> = repository.documentState

    // Formatted estimated PDF file size
    val estimatedPdfSize: StateFlow<String> = repository.documentState.map { doc ->
        var totalBytes = 0L
        for (page in doc.pages) {
            val file = if (page.processedImageFile.exists()) page.processedImageFile else page.rawImageFile
            if (file.exists()) {
                totalBytes += file.length()
            }
        }
        val estimatedPdfBytes = (totalBytes * 0.85).toLong() // standard PDF compression ratio
        formatFileSize(estimatedPdfBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 KB")

    fun rotatePage(pageId: String) {
        haptics.modeToggle()
        repository.rotatePage(pageId, 90)
    }

    fun deletePage(pageId: String) {
        haptics.delete()
        repository.removePage(pageId)
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        haptics.snap()
        repository.reorderPages(fromIndex, toIndex)
    }

    fun updateTitle(newTitle: String) {
        repository.updateDocumentMetadata(title = newTitle)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            "%.1f MB".format(mb)
        } else {
            "%.0f KB".format(kb)
        }
    }
}
