# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep OpenCV
-keep class org.opencv.** { *; }

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Keep model entities and JNI data holders
-keep class com.docwarp.scanner.core.model.** { *; }
-keep class com.docwarp.scanner.core.cv.** { *; }
