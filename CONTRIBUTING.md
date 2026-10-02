# Contributing to DocWarp AI

Thank you for your interest in contributing to **DocWarp AI**! 

DocWarp AI is a high-performance, real-time Android document scanner that marries cutting-edge native computer vision (OpenCV 4.10, ARM NEON SIMD, OpenMP) and on-device deep learning (LaMa inpainting via ONNX Runtime & MediaPipe Tasks) with modern, fluid Jetpack Compose UI.

To maintain production-grade performance, memory safety, and architectural elegance, please review the guidelines below before opening pull requests or contributing code.

---

## Table of Contents
1. [Development Environment & Setup](#1-development-environment--setup)
2. [Architecture Overview](#2-architecture-overview)
3. [Guidelines for Native C++ CV Algorithms](#3-guidelines-for-native-c-cv-algorithms)
4. [Guidelines for Jetpack Compose UI](#4-guidelines-for-jetpack-compose-ui)
5. [Git Workflow & Commit Standards](#5-git-workflow--commit-standards)
6. [Pull Request Checklist](#6-pull-request-checklist)
7. [Licensing & Attribution](#7-licensing--attribution)

---

## 1. Development Environment & Setup

### Prerequisites
- **IDE:** Android Studio Ladybug (2024.2.1+) or Koala Feature Drop
- **JDK:** Java 17 (Temurin or OpenJDK 17)
- **Android SDK:**
  - `compileSdk`: **35**
  - `targetSdk`: **35**
  - `minSdk`: **26** (Android 8.0 Oreo)
- **NDK:** `28.2.13676358`
- **CMake:** `3.22.1` or newer
- **Gradle:** `8.4` (using Gradle wrapper)

### Initial Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/sharmashyama1988-eng/docwarp-ai.git
   cd docwarp-ai
   ```
2. Download required on-device ML model weights:
   - On Linux / macOS:
     ```bash
     chmod +x download_models.sh
     ./download_models.sh
     ```
   - On Windows (PowerShell):
     ```powershell
     .\download_models.ps1
     ```
   *Note: Model binaries (`*.onnx`) exceed GitHub file limits and are managed via download scripts.*

3. Build the project locally:
   ```bash
   ./gradlew assembleDebug --stacktrace
   ```

---

## 2. Architecture Overview

DocWarp AI uses a layered modular architecture:

```
app/
├── src/main/cpp/                     # Native C++ Computer Vision & SIMD Engine
│   ├── CMakeLists.txt                # CMake build config with OpenCV Prefab & 16KB alignment
│   ├── cv_engine.h                   # OpenCV core algorithms & Quad homography headers
│   ├── cv_engine.cpp                 # C++ implementation (NEON SIMD, OpenMP, Bilateral, Canny)
│   └── native-lib.cpp                # JNI Bridge: Zero-copy direct buffers & Bitmap pixel locks
├── src/main/java/com/docwarp/scanner/
│   ├── core/
│   │   ├── cv/                       # NativeCvEngine Kotlin wrapper & StillnessDetector
│   │   ├── ml/                       # MediaPipeHandMasker & OnnxModelManager (LaMa inpainting)
│   │   ├── model/                    # Immutable domain models (DocumentQuad, ScannedPage, etc.)
│   │   ├── pdf/                      # PdfDocumentCompiler & compression engine
│   │   ├── pipeline/                 # Coroutine Workers & DocumentScanRepository
│   │   └── sensor/                   # Gyroscope & Accelerometer DeviceLeveler
│   └── ui/                           # Jetpack Compose UI Layer
│       ├── camera/                   # Real-time viewfinder, corner overlays, dock controls
│       ├── editor/                   # Manual crop, loupe magnifier, filter studio
│       ├── export/                   # PDF compilation settings, preview, share
│       ├── gallery/                  # Multi-page carousel, thumbnail grid, page organizer
│       ├── navigation/               # Type-safe Compose Navigation
│       └── theme/                    # Obsidian dark theme, typography, haptics
```

---

## 3. Guidelines for Native C++ CV Algorithms

All native code in `app/src/main/cpp/` executes in time-critical paths (camera frame processing at 30–60 FPS, high-res perspective warps, and real-time color filtering).

### 3.1 Memory Safety & Zero-Copy JNI
- **Direct Pixel Access:** Always use `AndroidBitmap_lockPixels` and `AndroidBitmap_unlockPixels` via RAII wrappers (`BitmapLocker` or `std::unique_ptr`). Never allocate intermediary full-size Java arrays for image passing.
- **Reference Counting & Avoid Clones:** Avoid calling `cv::Mat::clone()` unless you explicitly require independent data ownership. Use `cv::Mat(rows, cols, type, data, step)` to create header views over existing memory.
- **Leak-Free JNI:** Every JNI local reference created in tight loops must be explicitly cleared via `env->DeleteLocalRef(...)` if not returned to the JVM.

### 3.2 ARM NEON SIMD Vectorization
- Any custom pixel transformation (thresholding, color balance, grayscale conversion, contrast stretching) must implement vectorized ARM NEON intrinsics for `arm64-v8a` and `armeabi-v7a`.
- Always guard NEON routines with architecture preprocessor macros and provide a portable scalar fallback for `x86_64` (emulator support):
  ```cpp
  #if defined(__ARM_NEON)
  #include <arm_neon.h>
  void applyFilterNeon(uint8_t* src, uint8_t* dst, int length) {
      // 128-bit vector registers processing 16 bytes per cycle
  }
  #else
  void applyFilterScalar(uint8_t* src, uint8_t* dst, int length) {
      // Scalar fallback for x86 / x86_64
  }
  #endif
  ```

### 3.3 Multi-Threading with OpenMP
- Use `#pragma omp parallel for` for compute-heavy, row-independent processing (e.g., bilateral smoothing, edge maps, or high-resolution homography warps).
- Be mindful of mobile CPU topology (big.LITTLE / Prime cores). Avoid creating more threads than physical performance cores to prevent thermal throttling.

### 3.4 Android 15 16KB Page Size Alignment
- Google Play mandates 16KB ELF page size alignment for Android 15 (API 35+).
- In `CMakeLists.txt` and `app/build.gradle.kts`, the linker flag `-Wl,-z,max-page-size=16384` is strictly required. Do not remove or alter this configuration.

### 3.5 JNI Exceptions & Defensive Validation
- Never allow a C++ exception to propagate uncaught across the JNI boundary into the ART runtime. Catch all exceptions inside JNI entry points and propagate them gracefully using `env->ThrowNew(...)`.

---

## 4. Guidelines for Jetpack Compose UI

The UI layer is built entirely with **Jetpack Compose** and **Material 3**, utilizing an obsidian/dark aesthetic optimized for focus, readability, and hardware efficiency.

### 4.1 Unidirectional Data Flow (UDF)
- Every screen must be divided into:
  1. A stateful **Route Composable** (e.g., `CameraScannerRoute`) that collects `StateFlow` from ViewModel and maps events.
  2. A stateless **Screen Composable** (e.g., `CameraScannerScreen`) that receives pure state and emits lambdas.
- ViewModels expose immutable `StateFlow<ScreenUiState>` and one-off side effects via `SharedFlow<ScreenEvent>` or `Channel`.

### 4.2 Recomposition Performance
- Data classes used in UI state must be annotated with `@Immutable` or `@Stable` to facilitate smart recomposition skipping.
- Wrap complex calculations or transformations with `remember(key) { ... }` or `derivedStateOf { ... }`.
- Never perform I/O, native image decoding, or heavy filtering in the composition phase. Dispatch all intensive operations to `Dispatchers.Default` or `Dispatchers.IO`.

### 4.3 Design Tokens & Obsidian Aesthetics
- Adhere strictly to the color palette defined in `com.docwarp.scanner.ui.theme.Color.kt`:
  - Background: Deep obsidian (`#0D0E12`, `#161820`)
  - Accent / Primary: Electric Cobalt (`#387BFF`)
  - Text: High-contrast Slate & Off-White (`#F0F2F5`, `#A0A6B2`)
- Avoid hardcoded color literals or raw dimensions; use `MaterialTheme.colorScheme` and centralized typography scales.

### 4.4 Touch Gestures & Magnifier Canvas
- Custom canvas drawing (such as `ScannerOverlayCanvas` quad corners and `MagnifierLoupeOverlay`) must support smooth drag gestures with sub-pixel precision and haptic feedback via `HapticFeedbackManager`.

---

## 5. Git Workflow & Commit Standards

We follow the **GitHub Flow** branching model with **Conventional Commits**:

### Branch Naming Conventions
- `feature/<short-description>` (e.g., `feature/spine-curvature-correction`)
- `fix/<issue-number>-<short-description>` (e.g., `fix/102-neon-alignment-crash`)
- `perf/<module-optimized>` (e.g., `perf/homography-cache`)
- `docs/<topic>` (e.g., `docs/architecture-overview`)

### Commit Message Format
```text
<type>(<scope>): <short imperative summary>

[optional detailed body explaining why this change was made]

[optional issue reference: Fixes #123]
```

**Allowed Types:**
- `feat`: A new user-facing feature or native algorithm
- `fix`: A bug fix or crash resolution
- `perf`: A code change that improves CPU/GPU performance or reduces memory usage
- `refactor`: Code reorganization with no behavior change
- `docs`: Documentation updates or code comments
- `test`: Adding or modifying automated tests
- `ci`: Changes to GitHub Actions workflows or build scripts

---

## 6. Pull Request Checklist

Before submitting a Pull Request, verify:
- [ ] `./gradlew assembleDebug` builds without warnings or errors.
- [ ] Native code compiles across all supported ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
- [ ] No temporary files, `.cxx`, `build/`, or heavy `.onnx` binaries are staged.
- [ ] New Compose screens include `@Preview` configurations for light and dark modes.
- [ ] Commit history is clean, atomic, and rebased on the latest `origin/main`.
- [ ] All GitHub Actions CI checks pass.

---

## 7. Licensing & Attribution

DocWarp AI is licensed under the **Apache License 2.0**. By submitting a Pull Request, you certify that:
1. You have the right to submit the contribution under Apache License 2.0.
2. The code is your original work or originates from an appropriately licensed open-source project with clear attribution.
