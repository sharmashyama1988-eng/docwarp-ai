package com.docwarp.scanner.ui.gallery

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.core.model.DocumentFolder
import com.docwarp.scanner.core.model.ScanDocument
import com.docwarp.scanner.core.model.ScannedPage
import com.docwarp.scanner.core.pipeline.DocumentPipelineWorker
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import com.docwarp.scanner.ui.theme.HapticFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentGalleryViewModel(
    application: Application,
    private val repository: DocumentScanRepository,
    private val pipelineWorker: DocumentPipelineWorker? = null
) : AndroidViewModel(application) {

    private val haptics = HapticFeedbackManager(application)

    val documentState: StateFlow<ScanDocument> = repository.documentState

    // Folder Management
    private val _availableFolders = MutableStateFlow(DocumentFolder.DEFAULT_FOLDERS)
    val availableFolders: StateFlow<List<DocumentFolder>> = _availableFolders.asStateFlow()

    private val _selectedFolder = MutableStateFlow(DocumentFolder.DEFAULT)
    val selectedFolder: StateFlow<DocumentFolder> = _selectedFolder.asStateFlow()

    // Multi-select batch mode
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedPageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPageIds: StateFlow<Set<String>> = _selectedPageIds.asStateFlow()

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

    fun selectFolder(folder: DocumentFolder) {
        haptics.modeToggle()
        _selectedFolder.value = folder
        repository.updateDocumentMetadata(folder = folder.name)
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        val newFolder = DocumentFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            iconEmoji = "📁"
        )
        _availableFolders.value = _availableFolders.value + newFolder
        selectFolder(newFolder)
    }

    fun toggleSelectionMode() {
        haptics.modeToggle()
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedPageIds.value = emptySet()
        }
    }

    fun togglePageSelection(pageId: String) {
        haptics.snap()
        val current = _selectedPageIds.value.toMutableSet()
        if (current.contains(pageId)) {
            current.remove(pageId)
        } else {
            current.add(pageId)
        }
        _selectedPageIds.value = current
        if (!_isSelectionMode.value && current.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    fun selectAll() {
        haptics.snap()
        val allIds = documentState.value.pages.map { it.id }.toSet()
        _selectedPageIds.value = allIds
    }

    fun clearSelection() {
        haptics.modeToggle()
        _selectedPageIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelected() {
        haptics.delete()
        val toDelete = _selectedPageIds.value
        for (id in toDelete) {
            repository.removePage(id)
        }
        clearSelection()
    }

    fun rotateSelected() {
        haptics.modeToggle()
        val toRotate = _selectedPageIds.value
        for (id in toRotate) {
            repository.rotatePage(id, 90)
        }
    }

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

    /**
     * Direct import of JPG/PNG/PDF files from device into gallery
     */
    fun importImagesFromUris(uris: List<Uri>) {
        val worker = pipelineWorker ?: return
        if (uris.isEmpty()) return
        haptics.capture()

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val cacheDir = File(context.cacheDir, "imported_scans").apply { if (!exists()) mkdirs() }

            for (uri in uris) {
                try {
                    val rawFile = File(cacheDir, "gallery_import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(rawFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (rawFile.exists() && rawFile.length() > 0) {
                        worker.enqueueCapture(
                            rawCaptureFile = rawFile,
                            detectedNormalizedQuad = null,
                            isDualPage = false,
                            filter = com.docwarp.scanner.core.model.ScanFilter.EBOOK_CLEAN
                        )
                    }
                } catch (e: Exception) {
                    Log.e("DocumentGalleryVM", "Error importing URI $uri: ${e.message}", e)
                }
            }
        }
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
