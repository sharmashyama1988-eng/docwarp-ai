#ifndef CV_ENGINE_H
#define CV_ENGINE_H

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/imgcodecs.hpp>
#include <vector>
#include <utility>
#include <string>

namespace vflat {

struct Point2f {
    float x;
    float y;

    Point2f() : x(0.0f), y(0.0f) {}
    Point2f(float _x, float _y) : x(_x), y(_y) {}
};

struct DocumentQuad {
    Point2f topLeft;
    Point2f topRight;
    Point2f bottomRight;
    Point2f bottomLeft;

    bool isValid() const {
        return (topLeft.x != topRight.x || topLeft.y != bottomLeft.y);
    }
};

struct ProcessedPageOutput {
    std::string outputPath;
    cv::Mat thumbnailRgba; // Downsampled ~512px thumbnail for Java/Kotlin UI
    int fullWidth;
    int fullHeight;
};

class CvEngine {
public:
    // Edge & 4-corner document detection (adapted from jhansireddy/AndroidScannerDemo)
    static bool detectDocumentEdges(
        const cv::Mat& srcRgba,
        DocumentQuad& outQuad,
        float minAreaRatio = 0.10f
    );

    // Perspective transformation and crop using ordered quad corners
    static cv::Mat cropAndWarp(
        const cv::Mat& srcRgba,
        const DocumentQuad& quad
    );

    // Mathematical 3D cylindrical developable-surface page unrolling (Tukey-IRLS SVD + band remap)
    static cv::Mat dewarpBookPageCylindrical(
        const cv::Mat& srcRgba
    );

    // Zucker dewarp backward-compatible alias (routes to 3D cylindrical unroller)
    static cv::Mat dewarpPageZucker(
        const cv::Mat& srcRgba
    );

    // Book center spine detection via vertical projection profile
    static int detectSpineX(
        const cv::Mat& srcRgba
    );

    // Slice image into left and right book pages
    static std::pair<cv::Mat, cv::Mat> splitPages(
        const cv::Mat& srcRgba,
        int splitX
    );

    // High-performance integral-image Sauvola binarization
    static cv::Mat sauvolaBinarize(
        const cv::Mat& srcRgba,
        double k = 0.20,
        int windowSize = 0
    );

    // Telea inpainting fallback for erasing finger regions
    static cv::Mat inpaintTelea(
        const cv::Mat& srcRgba,
        const cv::Mat& mask8U,
        double inpaintRadius = 3.0
    );

    // Adaptive paper texture blending & text-contrast enhancement for inpainted finger regions
    // Seamlessly blends erased finger patches with surrounding paper grain, tone, and ink sharpness
    static cv::Mat blendInpaintedTexture(
        const cv::Mat& originalRgba,
        const cv::Mat& inpaintedRgba,
        const cv::Mat& mask8U
    );

    // Motion detection between preview frames using downsampled absdiff
    static float computeFrameMotion(
        const cv::Mat& currGray,
        const cv::Mat& prevGray,
        int thresholdVal = 15
    );

    // 11 Production Scan Filters
    static cv::Mat applyEBookClean(const cv::Mat& srcRgba);
    static cv::Mat applyMagicColor(const cv::Mat& srcRgba);
    static cv::Mat applySharpDocument(const cv::Mat& srcRgba);
    static cv::Mat applyDeepInk(const cv::Mat& srcRgba);
    static cv::Mat applyGrayscaleSmooth(const cv::Mat& srcRgba);
    static cv::Mat applyPaperBrightener(const cv::Mat& srcRgba);
    static cv::Mat applyShadowErase(const cv::Mat& srcRgba);
    static cv::Mat applyBlueprint(const cv::Mat& srcRgba);
    static cv::Mat applyFilterById(const cv::Mat& srcRgba, int filterId);

    // OOM-PROOF PIPELINE: Process raw capture file directly in C++ native memory
    // Decodes JPEG directly into cv::Mat, executes crop, dewarp, dual-split, and Sauvola,
    // writes full-res result directly to disk, and returns only 512px thumbnails to Java/Kotlin.
    static std::vector<ProcessedPageOutput> processDocumentFileNative(
        const std::string& inputFilePath,
        const std::string& outputDir,
        const DocumentQuad& normalizedQuad,
        bool doDewarp,
        bool doDualSplit,
        bool doBinarize,
        double sauvolaK = 0.20,
        int sauvolaWindow = 35
    );

private:
    static void orderCorners(
        const std::vector<cv::Point2f>& corners,
        DocumentQuad& quad
    );

    static double distance(const cv::Point2f& p1, const cv::Point2f& p2);
};

} // namespace vflat

#endif // CV_ENGINE_H
