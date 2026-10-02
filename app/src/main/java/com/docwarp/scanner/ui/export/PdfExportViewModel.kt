package com.docwarp.scanner.ui.export

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.core.model.CompressionQuality
import com.docwarp.scanner.core.model.PageSizeOption
import com.docwarp.scanner.core.model.ScanDocument
import com.docwarp.scanner.core.pdf.PdfDocumentCompiler
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import com.docwarp.scanner.ui.theme.HapticFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfExportViewModel(
    application: Application,
    private val repository: DocumentScanRepository
) : AndroidViewModel(application) {

    private val compiler = PdfDocumentCompiler(application)
    private val haptics = HapticFeedbackManager(application)

    val documentState: StateFlow<ScanDocument> = repository.documentState

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportedUri = MutableStateFlow<Uri?>(null)
    val exportedUri: StateFlow<Uri?> = _exportedUri.asStateFlow()

    fun updateTitle(newTitle: String) {
        repository.updateDocumentMetadata(title = newTitle)
    }

    fun updatePageSize(size: PageSizeOption) {
        haptics.modeToggle()
        repository.updateDocumentMetadata(pageSize = size)
    }

    fun updateCompression(quality: CompressionQuality) {
        haptics.modeToggle()
        repository.updateDocumentMetadata(compression = quality)
    }

    fun exportToStorage(onSuccess: (Uri) -> Unit) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            haptics.snap()

            try {
                val uri = compiler.compileDocumentToPdf(
                    document = repository.documentState.value,
                    onProgress = { _exportProgress.value = it }
                )
                if (uri != null) {
                    _exportedUri.value = uri
                    haptics.capture()
                    onSuccess(uri)
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun sharePdf(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Document via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(chooser)
    }
}
