package com.docwarp.scanner.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.core.model.ScanFilter
import com.docwarp.scanner.core.model.ScannedPage
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import com.docwarp.scanner.ui.theme.HapticFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilterStudioViewModel(
    application: Application,
    private val repository: DocumentScanRepository,
    private val pageId: String
) : AndroidViewModel(application) {

    private val haptics = HapticFeedbackManager(application)

    private val _page = MutableStateFlow<ScannedPage?>(null)
    val page: StateFlow<ScannedPage?> = _page.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _filteredBitmap = MutableStateFlow<Bitmap?>(null)
    val filteredBitmap: StateFlow<Bitmap?> = _filteredBitmap.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScanFilter.SAUVOLA_BINARIZED)
    val selectedFilter: StateFlow<ScanFilter> = _selectedFilter.asStateFlow()

    private val _eraseFingers = MutableStateFlow(true)
    val eraseFingers: StateFlow<Boolean> = _eraseFingers.asStateFlow()

    private val _flattenCurvature = MutableStateFlow(true)
    val flattenCurvature: StateFlow<Boolean> = _flattenCurvature.asStateFlow()

    private val _sliderPosition = MutableStateFlow(0.5f) // 0.0 (all before) to 1.0 (all after)
    val sliderPosition: StateFlow<Float> = _sliderPosition.asStateFlow()

    private val _isGeneratingPreview = MutableStateFlow(false)
    val isGeneratingPreview: StateFlow<Boolean> = _isGeneratingPreview.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val p = repository.getPage(pageId)
            _page.value = p
            if (p != null) {
                _selectedFilter.value = p.filter
                _eraseFingers.value = p.eraseFingers
                _flattenCurvature.value = p.flattenCurvature

                val orig = withContext(Dispatchers.IO) {
                    val file = if (p.rawImageFile.exists()) p.rawImageFile else p.processedImageFile
                    BitmapFactory.decodeFile(file.absolutePath)
                }
                _originalBitmap.value = orig

                generatePreview()
            }
        }
    }

    fun selectFilter(filter: ScanFilter) {
        if (_selectedFilter.value == filter) return
        haptics.modeToggle()
        _selectedFilter.value = filter
        generatePreview()
    }

    fun setEraseFingers(enabled: Boolean) {
        haptics.modeToggle()
        _eraseFingers.value = enabled
        generatePreview()
    }

    fun setFlattenCurvature(enabled: Boolean) {
        haptics.modeToggle()
        _flattenCurvature.value = enabled
        generatePreview()
    }

    fun updateSliderPosition(pos: Float) {
        _sliderPosition.value = pos.coerceIn(0.05f, 0.95f)
    }

    private fun generatePreview() {
        val orig = _originalBitmap.value ?: return
        viewModelScope.launch {
            _isGeneratingPreview.value = true
            try {
                // Downsample for instant interactive filter preview
                val previewW = 720
                val previewH = (previewW * (orig.height.toFloat() / orig.width)).toInt()
                val downsampled = withContext(Dispatchers.Default) {
                    Bitmap.createScaledBitmap(orig, previewW, previewH, true)
                }

                val preview = repository.applyFilterToBitmap(
                    bitmap = downsampled,
                    filter = _selectedFilter.value,
                    eraseFingers = _eraseFingers.value,
                    flattenCurvature = _flattenCurvature.value
                )

                _filteredBitmap.value = preview
            } finally {
                _isGeneratingPreview.value = false
            }
        }
    }

    fun applyToCurrentPage(onDone: () -> Unit) {
        haptics.capture()
        repository.updatePageFilter(
            pageId = pageId,
            newFilter = _selectedFilter.value,
            eraseFingers = _eraseFingers.value,
            flattenCurvature = _flattenCurvature.value
        )
        onDone()
    }

    fun applyToAllPages(onDone: () -> Unit) {
        haptics.capture()
        repository.applyFilterToAll(
            newFilter = _selectedFilter.value,
            eraseFingers = _eraseFingers.value,
            flattenCurvature = _flattenCurvature.value
        )
        onDone()
    }

    override fun onCleared() {
        super.onCleared()
        _originalBitmap.value?.recycle()
        _filteredBitmap.value?.recycle()
    }
}
