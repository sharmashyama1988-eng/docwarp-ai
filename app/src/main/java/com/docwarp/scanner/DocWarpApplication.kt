package com.docwarp.scanner

import android.app.Application
import com.docwarp.scanner.core.ml.MediaPipeHandMasker
import com.docwarp.scanner.core.ml.OnnxModelManager
import com.docwarp.scanner.core.pipeline.DocumentPipelineWorker
import com.docwarp.scanner.core.pipeline.DocumentScanRepository

class DocWarpApplication : Application() {

    lateinit var mediaPipeHandMasker: MediaPipeHandMasker
        private set

    lateinit var onnxModelManager: OnnxModelManager
        private set

    lateinit var documentScanRepository: DocumentScanRepository
        private set

    lateinit var documentPipelineWorker: DocumentPipelineWorker
        private set

    override fun onCreate() {
        super.onCreate()

        mediaPipeHandMasker = MediaPipeHandMasker(this)
        onnxModelManager = OnnxModelManager(this)
        documentScanRepository = DocumentScanRepository(this, mediaPipeHandMasker, onnxModelManager)
        documentPipelineWorker = DocumentPipelineWorker(this, documentScanRepository, mediaPipeHandMasker, onnxModelManager)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            onnxModelManager.close()
            mediaPipeHandMasker.close()
        }
    }
}
