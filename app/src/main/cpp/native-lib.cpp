#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "cv_engine.h"

#define TAG "DocWarpNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

using namespace vflat;

// Helper: Convert Android Bitmap to cv::Mat
static bool bitmapToMat(JNIEnv* env, jobject bitmap, cv::Mat& dstMat) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("bitmapToMat: Failed to get Bitmap info");
        return false;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_A_8) {
        LOGE("bitmapToMat: Unsupported format: %d", info.format);
        return false;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        LOGE("bitmapToMat: Failed to lock pixels");
        return false;
    }

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        dstMat = tmp.clone();
    } else if (info.format == ANDROID_BITMAP_FORMAT_A_8) {
        cv::Mat tmp(info.height, info.width, CV_8UC1, pixels);
        dstMat = tmp.clone();
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// Helper: Create new Android Bitmap and copy cv::Mat into it
static jobject matToBitmap(JNIEnv* env, const cv::Mat& srcMat) {
    if (srcMat.empty()) return nullptr;

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    if (!bitmapClass) {
        LOGE("matToBitmap: Class android/graphics/Bitmap not found");
        return nullptr;
    }

    jmethodID createBitmapMethod = env->GetStaticMethodID(
        bitmapClass,
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );

    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(
        configClass,
        "ARGB_8888",
        "Landroid/graphics/Bitmap$Config;"
    );
    jobject argb8888Config = env->GetStaticObjectField(configClass, argb8888FieldID);

    jobject bitmap = env->CallStaticObjectMethod(
        bitmapClass,
        createBitmapMethod,
        srcMat.cols,
        srcMat.rows,
        argb8888Config
    );

    if (!bitmap) {
        LOGE("matToBitmap: Failed to instantiate Bitmap object");
        return nullptr;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("matToBitmap: Failed to get created Bitmap info");
        return nullptr;
    }

    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &dstPixels) < 0 || !dstPixels) {
        LOGE("matToBitmap: Failed to lock target bitmap pixels");
        return nullptr;
    }

    cv::Mat targetRgba;
    if (srcMat.type() == CV_8UC4) {
        targetRgba = srcMat;
    } else if (srcMat.type() == CV_8UC3) {
        cv::cvtColor(srcMat, targetRgba, cv::COLOR_RGB2RGBA);
    } else if (srcMat.type() == CV_8UC1) {
        cv::cvtColor(srcMat, targetRgba, cv::COLOR_GRAY2RGBA);
    } else {
        LOGE("matToBitmap: Unsupported srcMat type: %d", srcMat.type());
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    cv::Mat dstWrapper(info.height, info.width, CV_8UC4, dstPixels);
    targetRgba.copyTo(dstWrapper);

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

extern "C" {

// 1. detectDocumentEdges
JNIEXPORT jfloatArray JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_detectDocumentEdges(
    JNIEnv* env,
    jobject /* thiz */,
    jobject bitmap,
    jfloat minAreaRatio
) {
    if (!bitmap) return nullptr;
    cv::Mat src;
    if (!bitmapToMat(env, bitmap, src)) return nullptr;

    DocumentQuad quad;
    bool found = CvEngine::detectDocumentEdges(src, quad, minAreaRatio);
    if (!found) return nullptr;

    jfloatArray result = env->NewFloatArray(8);
    if (!result) return nullptr;

    jfloat coords[8] = {
        quad.topLeft.x, quad.topLeft.y,
        quad.topRight.x, quad.topRight.y,
        quad.bottomRight.x, quad.bottomRight.y,
        quad.bottomLeft.x, quad.bottomLeft.y
    };
    env->SetFloatArrayRegion(result, 0, 8, coords);
    return result;
}

// 2. cropAndWarp
JNIEXPORT jobject JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_cropAndWarp(
    JNIEnv* env,
    jobject /* thiz */,
    jobject srcBitmap,
    jfloatArray quadCoords
) {
    if (!srcBitmap || !quadCoords) return nullptr;
    jsize len = env->GetArrayLength(quadCoords);
    if (len < 8) return nullptr;

    jfloat* coords = env->GetFloatArrayElements(quadCoords, nullptr);
    DocumentQuad quad;
    quad.topLeft = Point2f(coords[0], coords[1]);
    quad.topRight = Point2f(coords[2], coords[3]);
    quad.bottomRight = Point2f(coords[4], coords[5]);
    quad.bottomLeft = Point2f(coords[6], coords[7]);
    env->ReleaseFloatArrayElements(quadCoords, coords, JNI_ABORT);

    cv::Mat src;
    if (!bitmapToMat(env, srcBitmap, src)) return nullptr;

    cv::Mat warped = CvEngine::cropAndWarp(src, quad);
    return matToBitmap(env, warped);
}

// 3. dewarpPageZucker
JNIEXPORT jobject JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_dewarpPageZucker(
    JNIEnv* env,
    jobject /* thiz */,
    jobject srcBitmap
) {
    if (!srcBitmap) return nullptr;
    cv::Mat src;
    if (!bitmapToMat(env, srcBitmap, src)) return nullptr;

    cv::Mat dewarped = CvEngine::dewarpPageZucker(src);
    return matToBitmap(env, dewarped);
}

// 4. detectSpineX
JNIEXPORT jint JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_detectSpineX(
    JNIEnv* env,
    jobject /* thiz */,
    jobject srcBitmap
) {
    if (!srcBitmap) return -1;
    cv::Mat src;
    if (!bitmapToMat(env, srcBitmap, src)) return -1;
    return CvEngine::detectSpineX(src);
}

// 5. splitPages
JNIEXPORT jobjectArray JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_splitPages(
    JNIEnv* env,
    jobject /* thiz */,
    jobject srcBitmap,
    jint splitX
) {
    if (!srcBitmap || splitX <= 0) return nullptr;
    cv::Mat src;
    if (!bitmapToMat(env, srcBitmap, src)) return nullptr;

    auto [leftMat, rightMat] = CvEngine::splitPages(src, splitX);
    if (leftMat.empty() || rightMat.empty()) return nullptr;

    jobject leftBitmap = matToBitmap(env, leftMat);
    jobject rightBitmap = matToBitmap(env, rightMat);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jobjectArray result = env->NewObjectArray(2, bitmapClass, nullptr);
    env->SetObjectArrayElement(result, 0, leftBitmap);
    env->SetObjectArrayElement(result, 1, rightBitmap);
    return result;
}

// 6. sauvolaBinarize
JNIEXPORT jobject JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_sauvolaBinarize(
    JNIEnv* env,
    jobject /* thiz */,
    jobject srcBitmap,
    jdouble k,
    jint windowSize
) {
    if (!srcBitmap) return nullptr;
    cv::Mat src;
    if (!bitmapToMat(env, srcBitmap, src)) return nullptr;

    cv::Mat binarized = CvEngine::sauvolaBinarize(src, k, windowSize);
    return matToBitmap(env, binarized);
}

// 7. inpaintTelea
JNIEXPORT jobject JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_inpaintTelea(
    JNIEnv* env,
    jobject /* thiz */,
    jobject srcBitmap,
    jobject maskBitmap,
    jdouble radius
) {
    if (!srcBitmap || !maskBitmap) return nullptr;
    cv::Mat src, mask;
    if (!bitmapToMat(env, srcBitmap, src)) return nullptr;
    if (!bitmapToMat(env, maskBitmap, mask)) return nullptr;

    cv::Mat inpainted = CvEngine::inpaintTelea(src, mask, radius);
    return matToBitmap(env, inpainted);
}

// 8. computeFrameMotion
JNIEXPORT jfloat JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_computeFrameMotion(
    JNIEnv* env,
    jobject /* thiz */,
    jobject currBitmap,
    jobject prevBitmap,
    jint thresholdVal
) {
    if (!currBitmap || !prevBitmap) return 1.0f;
    cv::Mat curr, prev;
    if (!bitmapToMat(env, currBitmap, curr)) return 1.0f;
    if (!bitmapToMat(env, prevBitmap, prev)) return 1.0f;

    cv::Mat currGray, prevGray;
    if (curr.channels() == 4) cv::cvtColor(curr, currGray, cv::COLOR_RGBA2GRAY);
    else currGray = curr;

    if (prev.channels() == 4) cv::cvtColor(prev, prevGray, cv::COLOR_RGBA2GRAY);
    else prevGray = prev;

    return CvEngine::computeFrameMotion(currGray, prevGray, thresholdVal);
}

// 9. processDocumentFileNative (OOM-Proof pipeline)
JNIEXPORT jobjectArray JNICALL
Java_com_docwarp_scanner_core_cv_NativeCvEngine_processDocumentFileNative(
    JNIEnv* env,
    jobject /* thiz */,
    jstring inputFilePath,
    jstring outputDir,
    jfloatArray normalizedQuadCoords,
    jboolean doDewarp,
    jboolean doDualSplit,
    jboolean doBinarize,
    jdouble sauvolaK,
    jint sauvolaWindow
) {
    if (!inputFilePath || !outputDir) return nullptr;

    const char* inPathChars = env->GetStringUTFChars(inputFilePath, nullptr);
    const char* outDirChars = env->GetStringUTFChars(outputDir, nullptr);
    std::string inPath(inPathChars);
    std::string outDir(outDirChars);
    env->ReleaseStringUTFChars(inputFilePath, inPathChars);
    env->ReleaseStringUTFChars(outputDir, outDirChars);

    DocumentQuad normQuad;
    if (normalizedQuadCoords != nullptr) {
        jsize len = env->GetArrayLength(normalizedQuadCoords);
        if (len >= 8) {
            jfloat* coords = env->GetFloatArrayElements(normalizedQuadCoords, nullptr);
            normQuad.topLeft = Point2f(coords[0], coords[1]);
            normQuad.topRight = Point2f(coords[2], coords[3]);
            normQuad.bottomRight = Point2f(coords[4], coords[5]);
            normQuad.bottomLeft = Point2f(coords[6], coords[7]);
            env->ReleaseFloatArrayElements(normalizedQuadCoords, coords, JNI_ABORT);
        }
    }

    auto results = CvEngine::processDocumentFileNative(
        inPath,
        outDir,
        normQuad,
        doDewarp,
        doDualSplit,
        doBinarize,
        sauvolaK,
        sauvolaWindow
    );

    if (results.empty()) return nullptr;

    jclass resultClass = env->FindClass("com/docwarp/scanner/core/cv/NativePageResult");
    if (!resultClass) {
        LOGE("NativePageResult class not found: com/docwarp/scanner/core/cv/NativePageResult");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(
        resultClass,
        "<init>",
        "(Ljava/lang/String;Landroid/graphics/Bitmap;II)V"
    );

    jobjectArray array = env->NewObjectArray(results.size(), resultClass, nullptr);

    for (size_t i = 0; i < results.size(); ++i) {
        jstring pathStr = env->NewStringUTF(results[i].outputPath.c_str());
        jobject thumbBmp = matToBitmap(env, results[i].thumbnailRgba);

        jobject item = env->NewObject(
            resultClass,
            ctor,
            pathStr,
            thumbBmp,
            results[i].fullWidth,
            results[i].fullHeight
        );

        env->SetObjectArrayElement(array, i, item);
    }

    return array;
}

} // extern "C"
