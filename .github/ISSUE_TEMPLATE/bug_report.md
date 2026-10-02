---
name: Bug Report
about: Create a detailed bug report to help us fix issues in DocWarp AI
title: '[BUG] '
labels: 'bug'
assignees: ''
---

### 🐛 Problem Description
A clear and concise description of what the bug is.

### 📱 Device & Runtime Environment
- **Device Model:** [e.g., Google Pixel 8 Pro, Samsung Galaxy S24, Xiaomi 13]
- **Android Version:** [e.g., Android 14 (API 34), Android 15 (API 35)]
- **Target ABI:** [e.g., `arm64-v8a`, `armeabi-v7a`, `x86_64`]
- **Page Size:** [ ] 4KB standard  [ ] 16KB Android 15 compliant
- **DocWarp AI Version:** [e.g., 1.0.0 (commit hash)]

### ⚙️ Affected Subsystem
Please select the component where the issue occurs:
- [ ] **Camera Preview & Frame Analysis** (`CameraScannerScreen`, CameraX 1.3.2)
- [ ] **Real-time Quad Detection** (`cv_engine.cpp`, Canny / Contours / Polygon Approx)
- [ ] **Manual Crop & Loupe Magnifier** (`ManualCropScreen`, Touch Canvas)
- [ ] **Perspective Warp & C++ SIMD** (`cv_engine.cpp`, Homography / NEON / OpenMP)
- [ ] **Magic Hand / Finger Inpainting** (`MediaPipeHandMasker` + `OnnxModelManager` LaMa FP32)
- [ ] **Filter Studio** (Original, Magic Color, Crisp B&W, Grayscale, Threshold)
- [ ] **PDF Compiler & Export** (`PdfDocumentCompiler`, Page Sizing, Compression)
- [ ] **Document Gallery & Storage** (`DocumentGalleryScreen`, SQLite / Filesystem)

### 🔁 Steps to Reproduce
1. Launch DocWarp AI.
2. Navigate to screen '...'
3. Tap on '...'
4. Perform action '...'
5. Observe the error or unexpected behavior.

### 🎯 Expected Behavior
A concise description of what you expected to happen.

### 📸 Screenshots / Video
If applicable, attach screenshots or a screen recording to help explain the problem.

### 📜 Relevant Logs & Logcat Output
Run the following adb command to capture DocWarp native engine and UI logs:
```bash
adb logcat -v time -s DocWarpNative:V cv_engine:V OnnxModelManager:V MediaPipeHandMasker:V AndroidRuntime:E
```

```text
// Paste log output here
```

### 🔍 Additional Context
Add any other context about the problem (e.g., lighting conditions, document surface material, memory pressure, ambient orientation sensor readings).
