package com.docwarp.scanner

import android.app.Application
import android.content.ComponentCallbacks2
import com.docwarp.scanner.core.ml.MediaPipeHandMasker
import com.docwarp.scanner.core.ml.OnnxModelManager
import com.docwarp.scanner.core.pipeline.DocumentPipelineWorker
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DocWarpApplication : Application() {

    val mediaPipeHandMasker: MediaPipeHandMasker by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MediaPipeHandMasker(this)
    }

    val onnxModelManager: OnnxModelManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OnnxModelManager(this)
    }

    val documentScanRepository: DocumentScanRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DocumentScanRepository(this, mediaPipeHandMasker, onnxModelManager)
    }

    val documentPipelineWorker: DocumentPipelineWorker by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DocumentPipelineWorker(this, documentScanRepository, mediaPipeHandMasker, onnxModelManager)
    }

    val googleMlKitScanner: com.docwarp.scanner.core.ml.GoogleMlKitScanner by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.docwarp.scanner.core.ml.GoogleMlKitScanner(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Ultra-low latency cold start: 0ms blocking on the main UI looper
        // Singletons are lazily created when first accessed, and warmed up asynchronously
        CoroutineScope(Dispatchers.Default).launch {
            // Background pre-warming of native dependencies
            documentPipelineWorker
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            onnxModelManager.close()
            mediaPipeHandMasker.close()
            googleMlKitScanner.close()
        }
    }
}
