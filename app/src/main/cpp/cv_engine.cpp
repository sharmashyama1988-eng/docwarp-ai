#include "cv_engine.h"
#include <algorithm>
#include <cmath>
#include <chrono>
#include <sstream>

#ifdef _OPENMP
#include <omp.h>
#endif

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>

// Silicon-level 8-pixel per vector cycle fixed-point RGB/RGBA to Grayscale
static void fastRgbaToGrayNeon(const uint8_t* __restrict src, uint8_t* __restrict dst, int pixelCount) {
    int i = 0;
    for (; i <= pixelCount - 8; i += 8) {
        uint8x8x4_t rgba = vld4_u8(src + (i * 4));
        uint16x8_t r = vmull_u8(rgba.val[0], vdup_n_u8(77));
        uint16x8_t g = vmlal_u8(r, rgba.val[1], vdup_n_u8(150));
        uint16x8_t gray = vmlal_u8(g, rgba.val[2], vdup_n_u8(29));
        vst1_u8(dst + i, vshrn_n_u16(gray, 8));
    }
    for (; i < pixelCount; ++i) {
        int idx = i * 4;
        dst[i] = static_cast<uint8_t>((77 * src[idx] + 150 * src[idx + 1] + 29 * src[idx + 2]) >> 8);
    }
}

static int fastMotionNeon(const uint8_t* __restrict curr, const uint8_t* __restrict prev, int count, uint8_t thresh) {
    int motion = 0;
    int i = 0;
    uint8x16_t vThresh = vdupq_n_u8(thresh);
    for (; i <= count - 16; i += 16) {
        uint8x16_t vCurr = vld1q_u8(curr + i);
        uint8x16_t vPrev = vld1q_u8(prev + i);
        uint8x16_t vDiff = vabdq_u8(vCurr, vPrev);
        uint8x16_t mask = vcgtq_u8(vDiff, vThresh);
        uint8_t tmp[16];
        vst1q_u8(tmp, mask);
        for (int k = 0; k < 16; ++k) {
            if (tmp[k]) motion++;
        }
    }
    for (; i < count; ++i) {
        if (std::abs(static_cast<int>(curr[i]) - static_cast<int>(prev[i])) > thresh) {
            motion++;
        }
    }
    return motion;
}
#endif

namespace vflat {

double CvEngine::distance(const cv::Point2f& p1, const cv::Point2f& p2) {
    double dx = p1.x - p2.x;
    double dy = p1.y - p2.y;
    return std::sqrt(dx * dx + dy * dy);
}

void CvEngine::orderCorners(const std::vector<cv::Point2f>& corners, DocumentQuad& quad) {
    if (corners.size() != 4) return;

    // Corner ordering heuristic:
    // Top-Left: min (x + y)
    // Bottom-Right: max (x + y)
    // Top-Right: min (y - x)
    // Bottom-Left: max (y - x)

    std::vector<float> sum(4);
    std::vector<float> diff(4);

    for (int i = 0; i < 4; ++i) {
        sum[i] = corners[i].x + corners[i].y;
        diff[i] = corners[i].y - corners[i].x;
    }

    int tlIdx = std::min_element(sum.begin(), sum.end()) - sum.begin();
    int brIdx = std::max_element(sum.begin(), sum.end()) - sum.begin();
    int trIdx = std::min_element(diff.begin(), diff.end()) - diff.begin();
    int blIdx = std::max_element(diff.begin(), diff.end()) - diff.begin();

    quad.topLeft = Point2f(corners[tlIdx].x, corners[tlIdx].y);
    quad.topRight = Point2f(corners[trIdx].x, corners[trIdx].y);
    quad.bottomRight = Point2f(corners[brIdx].x, corners[brIdx].y);
    quad.bottomLeft = Point2f(corners[blIdx].x, corners[blIdx].y);
}

bool CvEngine::detectDocumentEdges(const cv::Mat& srcRgba, DocumentQuad& outQuad, float minAreaRatio) {
    if (srcRgba.empty()) return false;

    int origW = srcRgba.cols;
    int origH = srcRgba.rows;

    // Downscale for real-time speed (target ~500px width/height)
    float maxDim = static_cast<float>(std::max(origW, origH));
    float targetDim = 500.0f;
    float scale = (maxDim > targetDim) ? (targetDim / maxDim) : 1.0f;

    cv::Mat small;
    if (scale < 1.0f) {
        cv::resize(srcRgba, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = srcRgba;
    }

    cv::Mat gray;
    if (small.channels() == 4) {
        cv::cvtColor(small, gray, cv::COLOR_RGBA2GRAY);
    } else if (small.channels() == 3) {
        cv::cvtColor(small, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = small;
    }

    // Filter noise
    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 1.2);

    // Multi-scale Canny edge detection
    cv::Mat edges;
    cv::Canny(blurred, edges, 50, 150);

    // Morphological close to bridge edge discontinuities
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3));
    cv::dilate(edges, edges, kernel, cv::Point(-1, -1), 2);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(edges, contours, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    // Sort contours by area in descending order
    std::sort(contours.begin(), contours.end(), [](const std::vector<cv::Point>& a, const std::vector<cv::Point>& b) {
        return cv::contourArea(a, false) > cv::contourArea(b, false);
    });

    double totalSmallArea = static_cast<double>(small.cols * small.rows);
    double minArea = totalSmallArea * minAreaRatio;

    for (const auto& c : contours) {
        double area = cv::contourArea(c, false);
        if (area < minArea) break;

        double peri = cv::arcLength(c, true);
        std::vector<cv::Point> approx;
        cv::approxPolyDP(c, approx, 0.02 * peri, true);

        if (approx.size() == 4 && cv::isContourConvex(approx)) {
            std::vector<cv::Point2f> corners;
            for (const auto& pt : approx) {
                // Scale back to original resolution
                corners.emplace_back(pt.x / scale, pt.y / scale);
            }

            orderCorners(corners, outQuad);
            return true;
        }
    }

    // Fallback: document borders with 5% margin
    float marginX = origW * 0.05f;
    float marginY = origH * 0.05f;
    outQuad.topLeft = Point2f(marginX, marginY);
    outQuad.topRight = Point2f(origW - marginX, marginY);
    outQuad.bottomRight = Point2f(origW - marginX, origH - marginY);
    outQuad.bottomLeft = Point2f(marginX, origH - marginY);

    return false;
}

cv::Mat CvEngine::cropAndWarp(const cv::Mat& srcRgba, const DocumentQuad& quad) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Point2f tl(quad.topLeft.x, quad.topLeft.y);
    cv::Point2f tr(quad.topRight.x, quad.topRight.y);
    cv::Point2f br(quad.bottomRight.x, quad.bottomRight.y);
    cv::Point2f bl(quad.bottomLeft.x, quad.bottomLeft.y);

    double widthTop = distance(tl, tr);
    double widthBottom = distance(bl, br);
    int targetW = static_cast<int>(std::round(std::max(widthTop, widthBottom)));

    double heightLeft = distance(tl, bl);
    double heightRight = distance(tr, br);
    int targetH = static_cast<int>(std::round(std::max(heightLeft, heightRight)));

    if (targetW <= 0 || targetH <= 0) return srcRgba.clone();

    std::vector<cv::Point2f> srcPts = { tl, tr, br, bl };
    std::vector<cv::Point2f> dstPts = {
        cv::Point2f(0.0f, 0.0f),
        cv::Point2f(static_cast<float>(targetW - 1), 0.0f),
        cv::Point2f(static_cast<float>(targetW - 1), static_cast<float>(targetH - 1)),
        cv::Point2f(0.0f, static_cast<float>(targetH - 1))
    };

    cv::Mat transformMatrix = cv::getPerspectiveTransform(srcPts, dstPts);
    cv::Mat warped;
    cv::warpPerspective(
        srcRgba,
        warped,
        transformMatrix,
        cv::Size(targetW, targetH),
        cv::INTER_CUBIC,
        cv::BORDER_REPLICATE
    );

    return warped;
}

cv::Mat CvEngine::dewarpPageZucker(const cv::Mat& srcRgba) {
    if (srcRgba.empty() || srcRgba.cols < 64 || srcRgba.rows < 64) {
        return srcRgba.clone();
    }

    int W = srcRgba.cols;
    int H = srcRgba.rows;

    cv::Mat gray;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba;
    }

    // Downscale for fast contour and baseline extraction
    float scale = 400.0f / static_cast<float>(std::max(W, H));
    cv::Mat smallGray;
    cv::resize(gray, smallGray, cv::Size(), scale, scale, cv::INTER_AREA);

    int sW = smallGray.cols;
    int sH = smallGray.rows;

    // Detect horizontal text baselines via Sobel Y gradient
    cv::Mat gradY;
    cv::Sobel(smallGray, gradY, CV_32F, 0, 1, 3);
    cv::convertScaleAbs(gradY, gradY);

    // Compute vertical slices to measure boundary displacement
    const int numSlices = 32;
    std::vector<float> sliceX(numSlices);
    std::vector<float> topY(numSlices);
    std::vector<float> botY(numSlices);

    int sliceWidth = sW / numSlices;
    if (sliceWidth <= 0) return srcRgba.clone();

    for (int i = 0; i < numSlices; ++i) {
        int xStart = i * sliceWidth;
        int xEnd = std::min(sW, (i + 1) * sliceWidth);
        sliceX[i] = (xStart + xEnd) * 0.5f;

        cv::Rect roi(xStart, 0, xEnd - xStart, sH);
        cv::Mat sliceGrad = gradY(roi);

        // Project horizontally: average row intensities in slice
        std::vector<float> rowProfile(sH, 0.0f);
        for (int r = 0; r < sH; ++r) {
            float rowSum = 0.0f;
            const uint8_t* ptr = sliceGrad.ptr<uint8_t>(r);
            for (int c = 0; c < roi.width; ++c) {
                rowSum += ptr[c];
            }
            rowProfile[r] = rowSum / roi.width;
        }

        // Top edge: peak in upper region
        int bestTop = static_cast<int>(sH * 0.05f);
        float maxTopVal = 0.0f;
        int limitTop = static_cast<int>(sH * 0.35f);
        for (int r = static_cast<int>(sH * 0.02f); r < limitTop; ++r) {
            if (rowProfile[r] > maxTopVal) {
                maxTopVal = rowProfile[r];
                bestTop = r;
            }
        }
        topY[i] = static_cast<float>(bestTop);

        // Bottom edge: peak in lower region
        int bestBot = static_cast<int>(sH * 0.95f);
        float maxBotVal = 0.0f;
        int startBot = static_cast<int>(sH * 0.65f);
        for (int r = startBot; r < static_cast<int>(sH * 0.98f); ++r) {
            if (rowProfile[r] > maxBotVal) {
                maxBotVal = rowProfile[r];
                bestBot = r;
            }
        }
        botY[i] = static_cast<float>(bestBot);
    }

    // Scale profile coordinates back to original full resolution
    for (int i = 0; i < numSlices; ++i) {
        sliceX[i] /= scale;
        topY[i] /= scale;
        botY[i] /= scale;
    }

    // Fit cubic polynomial curves: y(x) = c0*x^3 + c1*x^2 + c2*x + c3
    cv::Mat A(numSlices, 4, CV_32F);
    cv::Mat bTop(numSlices, 1, CV_32F);
    cv::Mat bBot(numSlices, 1, CV_32F);

    for (int i = 0; i < numSlices; ++i) {
        float x = sliceX[i];
        A.at<float>(i, 0) = x * x * x;
        A.at<float>(i, 1) = x * x;
        A.at<float>(i, 2) = x;
        A.at<float>(i, 3) = 1.0f;

        bTop.at<float>(i, 0) = topY[i];
        bBot.at<float>(i, 0) = botY[i];
    }

    cv::Mat topCoeffs, botCoeffs;
    if (!cv::solve(A, bTop, topCoeffs, cv::DECOMP_SVD) ||
        !cv::solve(A, bBot, botCoeffs, cv::DECOMP_SVD)) {
        return srcRgba.clone();
    }

    auto evalCubic = [](const cv::Mat& c, float x) -> float {
        return c.at<float>(0, 0) * x * x * x +
               c.at<float>(1, 0) * x * x +
               c.at<float>(2, 0) * x +
               c.at<float>(3, 0);
    };

    auto evalCubicDeriv = [](const cv::Mat& c, float x) -> float {
        return 3.0f * c.at<float>(0, 0) * x * x +
               2.0f * c.at<float>(1, 0) * x +
               c.at<float>(2, 0);
    };

    // Cylindrical surface unrolling: cumulative arc length
    std::vector<float> arcLength(W, 0.0f);
    arcLength[0] = 0.0f;
    for (int x = 1; x < W; ++x) {
        float dy = evalCubicDeriv(topCoeffs, static_cast<float>(x));
        float ds = std::sqrt(1.0f + dy * dy);
        arcLength[x] = arcLength[x - 1] + ds;
    }

    float totalArcLen = arcLength[W - 1];
    int outW = static_cast<int>(std::round(totalArcLen));
    outW = std::clamp(outW, static_cast<int>(W * 0.8), static_cast<int>(W * 1.3));

    // Average page height across spans
    float avgHeight = 0.0f;
    for (int x = 0; x < W; ++x) {
        float yt = evalCubic(topCoeffs, static_cast<float>(x));
        float yb = evalCubic(botCoeffs, static_cast<float>(x));
        avgHeight += (yb - yt);
    }
    avgHeight /= W;
    int outH = static_cast<int>(std::round(avgHeight));
    outH = std::clamp(outH, static_cast<int>(H * 0.7), H);

    // Build dense coordinate displacement map
    cv::Mat mapX(outH, outW, CV_32FC1);
    cv::Mat mapY(outH, outW, CV_32FC1);

    std::vector<float> sToX(outW);
    int currentSrcX = 0;
    for (int u = 0; u < outW; ++u) {
        float targetS = (static_cast<float>(u) / (outW - 1)) * totalArcLen;
        while (currentSrcX < W - 1 && arcLength[currentSrcX] < targetS) {
            currentSrcX++;
        }
        sToX[u] = static_cast<float>(currentSrcX);
    }

#ifdef _OPENMP
#pragma omp parallel for collapse(2) schedule(static)
#endif
    for (int v = 0; v < outH; ++v) {
        for (int u = 0; u < outW; ++u) {
            float srcX = sToX[u];
            float yt = evalCubic(topCoeffs, srcX);
            float yb = evalCubic(botCoeffs, srcX);

            float t = static_cast<float>(v) / (outH - 1);
            float srcY = yt + t * (yb - yt);

            mapX.at<float>(v, u) = srcX;
            mapY.at<float>(v, u) = srcY;
        }
    }

    cv::Mat dewarped;
    cv::remap(
        srcRgba,
        dewarped,
        mapX,
        mapY,
        cv::INTER_CUBIC,
        cv::BORDER_REPLICATE
    );

    return dewarped;
}

int CvEngine::detectSpineX(const cv::Mat& srcRgba) {
    if (srcRgba.empty() || srcRgba.cols < 100) return -1;

    int W = srcRgba.cols;
    int H = srcRgba.rows;

    cv::Mat gray;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba;
    }

    // Downscale for fast vertical projection analysis
    int smallH = 256;
    float scale = static_cast<float>(smallH) / H;
    int smallW = static_cast<int>(W * scale);

    cv::Mat small;
    cv::resize(gray, small, cv::Size(smallW, smallH), 0, 0, cv::INTER_AREA);

    // Compute Sobel horizontal gradient (Sobel X)
    cv::Mat gradX;
    cv::Sobel(small, gradX, CV_32F, 1, 0, 3);
    cv::convertScaleAbs(gradX, gradX);

    // Analyze middle 35% to 65% width range for spine valley (trench shadow)
    int startCol = static_cast<int>(smallW * 0.35f);
    int endCol = static_cast<int>(smallW * 0.65f);

    std::vector<float> projIntensity(smallW, 0.0f);
    std::vector<float> projEdge(smallW, 0.0f);

    for (int x = startCol; x < endCol; ++x) {
        float sumI = 0.0f;
        float sumE = 0.0f;
        for (int y = 0; y < smallH; ++y) {
            sumI += small.at<uint8_t>(y, x);
            sumE += gradX.at<uint8_t>(y, x);
        }
        projIntensity[x] = sumI;
        projEdge[x] = sumE;
    }

    // Smooth intensity projection to eliminate text-line noise
    std::vector<float> smoothI = projIntensity;
    for (int x = startCol + 2; x < endCol - 2; ++x) {
        smoothI[x] = 0.06f * projIntensity[x - 2] +
                     0.24f * projIntensity[x - 1] +
                     0.40f * projIntensity[x] +
                     0.24f * projIntensity[x + 1] +
                     0.06f * projIntensity[x + 2];
    }

    // Find deepest local minimum (valley trench shadow) regularized by edge peak
    int bestX = (startCol + endCol) / 2;
    float minVal = 1e9f;

    for (int x = startCol + 1; x < endCol - 1; ++x) {
        // Spine cost: low intensity (dark trench) balanced with edge boundary
        float cost = smoothI[x] - (projEdge[x] * 0.15f);
        if (cost < minVal) {
            minVal = cost;
            bestX = x;
        }
    }

    int finalSpineX = static_cast<int>(bestX / scale);
    return finalSpineX;
}

std::pair<cv::Mat, cv::Mat> CvEngine::splitPages(const cv::Mat& srcRgba, int splitX) {
    if (srcRgba.empty() || splitX <= 0 || splitX >= srcRgba.cols) {
        return { srcRgba.clone(), cv::Mat() };
    }

    cv::Rect leftRect(0, 0, splitX, srcRgba.rows);
    cv::Rect rightRect(splitX, 0, srcRgba.cols - splitX, srcRgba.rows);

    cv::Mat leftPage = srcRgba(leftRect).clone();
    cv::Mat rightPage = srcRgba(rightRect).clone();

    return { leftPage, rightPage };
}

cv::Mat CvEngine::sauvolaBinarize(const cv::Mat& srcRgba, double k, int windowSize) {
    if (srcRgba.empty()) return cv::Mat();

    int W = srcRgba.cols;
    int H = srcRgba.rows;

    cv::Mat gray;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba;
    }

    if (windowSize <= 0) {
        windowSize = std::max(25, std::min(W, H) / 30);
        if ((windowSize % 2) == 0) windowSize += 1;
    }

    int radius = windowSize / 2;

    // Double precision integral images for fast O(1) variance computation
    cv::Mat sum, sqsum;
    cv::integral(gray, sum, sqsum, CV_64F);

    cv::Mat dstRgba(H, W, CV_8UC4);

    const double R = 128.0;

#ifdef _OPENMP
#pragma omp parallel for schedule(dynamic, 16)
#endif
    for (int y = 0; y < H; ++y) {
        int y1 = std::max(0, y - radius);
        int y2 = std::min(H, y + radius + 1);

        const uint8_t* grayRow = gray.ptr<uint8_t>(y);
        uint8_t* dstRow = dstRgba.ptr<uint8_t>(y);

        for (int x = 0; x < W; ++x) {
            int x1 = std::max(0, x - radius);
            int x2 = std::min(W, x + radius + 1);

            double count = static_cast<double>((x2 - x1) * (y2 - y1));

            // Sum in window
            double s = sum.at<double>(y2, x2) - sum.at<double>(y1, x2) -
                       sum.at<double>(y2, x1) + sum.at<double>(y1, x1);

            // Square sum in window
            double sq = sqsum.at<double>(y2, x2) - sqsum.at<double>(y1, x2) -
                        sqsum.at<double>(y2, x1) + sqsum.at<double>(y1, x1);

            double mean = s / count;
            double variance = std::max(0.0, (sq / count) - (mean * mean));
            double stdDev = std::sqrt(variance);

            // Sauvola threshold formula: T = mean * (1 + k * ((stdDev / 128) - 1))
            double thresh = mean * (1.0 + k * ((stdDev / R) - 1.0));

            uint8_t pixelVal = grayRow[x];
            uint8_t outVal = (pixelVal > thresh) ? 255 : 0;

            int dstIdx = x * 4;
            dstRow[dstIdx + 0] = outVal; // R
            dstRow[dstIdx + 1] = outVal; // G
            dstRow[dstIdx + 2] = outVal; // B
            dstRow[dstIdx + 3] = 255;    // A
        }
    }

    return dstRgba;
}

cv::Mat CvEngine::inpaintTelea(const cv::Mat& srcRgba, const cv::Mat& mask8U, double inpaintRadius) {
    if (srcRgba.empty() || mask8U.empty()) return srcRgba.clone();

    cv::Mat cleanMask;
    if (mask8U.channels() > 1) {
        cv::cvtColor(mask8U, cleanMask, cv::COLOR_RGBA2GRAY);
    } else {
        cleanMask = mask8U;
    }

    // Ensure 8-bit binary mask (0 = normal, 255 = inpaint area)
    cv::threshold(cleanMask, cleanMask, 127, 255, cv::THRESH_BINARY);

    cv::Mat rgb;
    cv::cvtColor(srcRgba, rgb, cv::COLOR_RGBA2RGB);

    cv::Mat inpaintedRgb;
    cv::inpaint(rgb, cleanMask, inpaintedRgb, inpaintRadius, cv::INPAINT_TELEA);

    cv::Mat inpaintedRgba;
    cv::cvtColor(inpaintedRgb, inpaintedRgba, cv::COLOR_RGB2RGBA);

    return inpaintedRgba;
}

float CvEngine::computeFrameMotion(const cv::Mat& currGray, const cv::Mat& prevGray, int thresholdVal) {
    if (currGray.empty() || prevGray.empty()) return 1.0f;
    if (currGray.size() != prevGray.size()) return 1.0f;

    int totalPixels = currGray.cols * currGray.rows;
    if (totalPixels <= 0) return 0.0f;

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    if (currGray.isContinuous() && prevGray.isContinuous()) {
        int motionPixels = fastMotionNeon(currGray.data, prevGray.data, totalPixels, static_cast<uint8_t>(thresholdVal));
        return static_cast<float>(motionPixels) / static_cast<float>(totalPixels);
    }
#endif

    cv::Mat diff;
    cv::absdiff(currGray, prevGray, diff);

    cv::Mat thresh;
    cv::threshold(diff, thresh, thresholdVal, 255, cv::THRESH_BINARY);

    int motionPixels = cv::countNonZero(thresh);
    return static_cast<float>(motionPixels) / static_cast<float>(totalPixels);
}

std::vector<ProcessedPageOutput> CvEngine::processDocumentFileNative(
    const std::string& inputFilePath,
    const std::string& outputDir,
    const DocumentQuad& normalizedQuad,
    bool doDewarp,
    bool doDualSplit,
    bool doBinarize,
    double sauvolaK,
    int sauvolaWindow
) {
    std::vector<ProcessedPageOutput> outputs;

    // Load full-resolution photo directly into native C++ RAM (bypassing Java heap)
    cv::Mat raw = cv::imread(inputFilePath, cv::IMREAD_COLOR);
    if (raw.empty()) return outputs;

    int origW = raw.cols;
    int origH = raw.rows;

    // Step 1: Crop and warp using normalized coordinates scaled to captured image size
    cv::Mat cropped;
    if (normalizedQuad.isValid()) {
        DocumentQuad fullQuad;
        fullQuad.topLeft = Point2f(normalizedQuad.topLeft.x * origW, normalizedQuad.topLeft.y * origH);
        fullQuad.topRight = Point2f(normalizedQuad.topRight.x * origW, normalizedQuad.topRight.y * origH);
        fullQuad.bottomRight = Point2f(normalizedQuad.bottomRight.x * origW, normalizedQuad.bottomRight.y * origH);
        fullQuad.bottomLeft = Point2f(normalizedQuad.bottomLeft.x * origW, normalizedQuad.bottomLeft.y * origH);
        cropped = cropAndWarp(raw, fullQuad);
    } else {
        // Detect quad automatically on full image if none provided
        DocumentQuad detected;
        if (detectDocumentEdges(raw, detected, 0.10f)) {
            cropped = cropAndWarp(raw, detected);
        } else {
            cropped = raw.clone();
        }
    }
    raw.release(); // Free 12MP/48MP raw buffer immediately

    // Step 2: Dewarp page curvature
    cv::Mat dewarped;
    if (doDewarp) {
        dewarped = dewarpPageZucker(cropped);
        cropped.release();
    } else {
        dewarped = cropped;
    }

    // Step 3: Dual page split
    std::vector<cv::Mat> pageList;
    if (doDualSplit) {
        int spineX = detectSpineX(dewarped);
        if (spineX > (dewarped.cols * 0.35f) && spineX < (dewarped.cols * 0.65f)) {
            auto [leftPage, rightPage] = splitPages(dewarped, spineX);
            dewarped.release();
            pageList.push_back(leftPage);
            pageList.push_back(rightPage);
        } else {
            pageList.push_back(dewarped);
        }
    } else {
        pageList.push_back(dewarped);
    }

    // Step 4: Sauvola Binarization & Direct Disk Serialization
    auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    for (size_t idx = 0; idx < pageList.size(); ++idx) {
        cv::Mat curPage = pageList[idx];
        cv::Mat processed;

        if (doBinarize) {
            processed = sauvolaBinarize(curPage, sauvolaK, sauvolaWindow);
            curPage.release();
        } else {
            processed = curPage;
        }

        // Construct unique output filename
        std::ostringstream ss;
        ss << outputDir << "/page_" << nowMs << "_" << idx << ".jpg";
        std::string finalPath = ss.str();

        // Write directly to disk from native C++
        std::vector<int> compressionParams = { cv::IMWRITE_JPEG_QUALITY, 92 };
        cv::imwrite(finalPath, processed, compressionParams);

        // Generate tiny 512px thumbnail for Java/Kotlin UI
        int thumbW = 512;
        int thumbH = static_cast<int>(thumbW * (static_cast<float>(processed.rows) / processed.cols));
        cv::Mat thumb;
        cv::resize(processed, thumb, cv::Size(thumbW, thumbH), 0, 0, cv::INTER_AREA);

        cv::Mat thumbRgba;
        if (thumb.channels() == 1) {
            cv::cvtColor(thumb, thumbRgba, cv::COLOR_GRAY2RGBA);
        } else if (thumb.channels() == 3) {
            cv::cvtColor(thumb, thumbRgba, cv::COLOR_BGR2RGBA);
        } else {
            thumbRgba = thumb;
        }

        ProcessedPageOutput out;
        out.outputPath = finalPath;
        out.thumbnailRgba = thumbRgba;
        out.fullWidth = processed.cols;
        out.fullHeight = processed.rows;
        outputs.push_back(out);

        processed.release();
    }

    return outputs;
}

} // namespace vflat
