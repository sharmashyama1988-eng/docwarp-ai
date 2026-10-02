# DocWarp AI — High-Performance Mobile Document Scanner & Dewarp Engine

![Android 15 Ready](https://img.shields.io/badge/Android-15%2B%20(API%2035)-00E676?style=for-the-badge&logo=android)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose)
![NDK C++](https://img.shields.io/badge/Native-NDK%20C%2B%2B%20%2F%20OpenCV-00599C?style=for-the-badge&logo=c%2B%2B)
![ARM NEON](https://img.shields.io/badge/Hardware-ARM%20NEON%20SIMD-FF6F00?style=for-the-badge)
![ML & ONNX](https://img.shields.io/badge/Inference-ONNX%20%2B%20MediaPipe-FFC107?style=for-the-badge)

**DocWarp AI** is an enterprise-grade, 6-screen high-performance document scanner Android application inspired by vFlat. Engineered with a hardware-accelerated computer vision pipeline, native C++ memory management, and modern Jetpack Compose UI.

---

## 🌟 Key Architecture & Capabilities

### 1. Native Computer Vision Engine (`C++ / NDK`)
- **ARM NEON SIMD Vectorization:** Direct 64-bit/128-bit vector register execution (`vld3_u8`, `vmull_u8`, `vmlal_u8`, `vshrn_n_u16`) for sub-millisecond RGBA-to-Grayscale and real-time motion detection at 60 FPS.
- **OOM-Crash Proof Architecture:** Bypasses JVM garbage collection by decoding, transforming, dewarping, and writing full-resolution JPEGs entirely in native C++ memory buffers (`cv::Mat`). Returns lightweight 512px downsampled thumbnails to the UI layer.
- **Zucker Spline Cylindrical Dewarping:** Mathematical model flattening curved book pages and thick document bindings.
- **Automatic Spine Detection:** Scans vertical projection profiles $P(x) = \sum I(x, y)$ and Sobel-X gradient valleys to automatically split dual-page open books into distinct single-page scans.
- **Integral Image Sauvola Binarization:** OpenMP parallelized adaptive local thresholding ($k = 0.20$, window = 35) for crisp, ultra-clean black-and-white documents with even contrast.

### 2. Dual-Engine ML Pipeline
- **Google MediaPipe Hand Landmarker:** Detects user fingertips resting on book margins and generates dilated convex hull masks for occlusion removal.
- **LaMa Mobile Inpainting (ONNX Runtime):** Erases occluded finger regions seamlessly via deep neural inpainting with Google NNAPI / GPU hardware acceleration fallback.

### 3. 6 Complete Jetpack Compose Screens
1. **Camera Scanner Viewfinder (`CameraScannerScreen.kt`):** Full-screen CameraX stream with live dynamic quad reticle, L-brackets, gyroscope tilt leveler, stillness detection progress ring, and dual-page guide.
2. **Background Processing Pipeline (`DocumentPipelineWorker.kt`):** Non-blocking coroutine Channels processing high-res captures in the background without UI stutter.
3. **Document Gallery (`DocumentGalleryScreen.kt`):** Staggered multi-column grid with live page reordering, rotation, deletion, and file size estimation.
4. **Manual Quad & Crop Editor (`ManualCropScreen.kt`):** Interactive four-corner dragging with a 90dp 2x zoom magnifier loupe with crosshairs and magnetic edge snapping.
5. **Filter Studio (`FilterStudioScreen.kt`):** Interactive split-slider before/after comparison with 5 filter presets (Original, Auto Magic, Crisp B&W Sauvola, Grayscale, Color Boost).
6. **PDF Compiler & Export (`PdfExportScreen.kt`):** Multi-page Scoped Storage PDF generator (`MediaStore`) supporting A4, US Letter, and Fit-to-Page scaling with multi-tier JPEG compression.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio:** Hedgehog / Iguana / Jellyfish / Ladybug or newer.
- **Android SDK:** API 35 (Android 15) or API 36 (Android 16 preview).
- **NDK:** r28c (`28.2.13676358`).
- **CMake:** 3.22.1+.
- **JDK:** 17 or 21.

### 📥 Download Pre-trained ML Models
Run the download script to fetch the MediaPipe hand landmarker and LaMa ONNX model:

**Windows (PowerShell):**
```powershell
.\download_models.ps1
```

**macOS / Linux:**
```bash
chmod +x ./download_models.sh
./download_models.sh
```

### 🔨 Build & Run
```bash
./gradlew assembleDebug
```
Or open the project in **Android Studio** and click **Run 'app'** (`Shift + F10`).

---

## 📁 Project Structure

```
├── app/
│   ├── src/main/
│   │   ├── cpp/                     # Native C++ CV & Silicon Acceleration
│   │   │   ├── CMakeLists.txt       # CMake 3.22.1 native build
│   │   │   ├── cv_engine.h          # Core Computer Vision algorithms
│   │   │   ├── cv_engine.cpp        # Zucker dewarp, Sauvola, Spine, NEON SIMD
│   │   │   └── native-lib.cpp       # JNI bindings for Kotlin bridge
│   │   ├── java/com/docwarp/scanner/
│   │   │   ├── core/                # Models, CV, Sensors, ML, PDF Engine
│   │   │   ├── ui/                  # Jetpack Compose Screens & Design System
│   │   │   ├── MainActivity.kt      # Main edge-to-edge Activity
│   │   │   └── DocWarpApplication.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts             # 16KB page alignment flags & dependencies
├── download_models.ps1              # Resumable model downloader
├── download_models.sh
└── README.md
```

---

## 📜 License
Distributed under the Apache 2.0 License.
