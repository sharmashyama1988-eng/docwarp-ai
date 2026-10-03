#include "cv_engine.h"
#include <algorithm>
#include <cmath>
#include <chrono>
#include <sstream>
#include <cstring>

#ifdef _OPENMP
#include <omp.h>
#endif

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>

// Silicon-level 16-pixel per vector cycle fixed-point RGBA to Grayscale
// Uses 128-bit Q registers with dual-lane accumulation: (77*R + 150*G + 29*B) >> 8
static void fastRgbaToGrayNeon(const uint8_t* __restrict src, uint8_t* __restrict dst, int pixelCount) {
    int i = 0;
    uint8x8_t vCoeffR = vdup_n_u8(77);
    uint8x8_t vCoeffG = vdup_n_u8(150);
    uint8x8_t vCoeffB = vdup_n_u8(29);

    for (; i <= pixelCount - 16; i += 16) {
        uint8x16x4_t rgba = vld4q_u8(src + (i * 4));

        // Low 8 pixels
        uint16x8_t rLow = vmull_u8(vget_low_u8(rgba.val[0]), vCoeffR);
        uint16x8_t gLow = vmlal_u8(rLow, vget_low_u8(rgba.val[1]), vCoeffG);
        uint16x8_t grayLow = vmlal_u8(gLow, vget_low_u8(rgba.val[2]), vCoeffB);
        uint8x8_t resLow = vshrn_n_u16(grayLow, 8);

        // High 8 pixels
        uint16x8_t rHigh = vmull_u8(vget_high_u8(rgba.val[0]), vCoeffR);
        uint16x8_t gHigh = vmlal_u8(rHigh, vget_high_u8(rgba.val[1]), vCoeffG);
        uint16x8_t grayHigh = vmlal_u8(gHigh, vget_high_u8(rgba.val[2]), vCoeffB);
        uint8x8_t resHigh = vshrn_n_u16(grayHigh, 8);

        vst1q_u8(dst + i, vcombine_u8(resLow, resHigh));
    }
    for (; i < pixelCount; ++i) {
        int idx = i * 4;
        dst[i] = static_cast<uint8_t>((77 * src[idx] + 150 * src[idx + 1] + 29 * src[idx + 2]) >> 8);
    }
}

// Silicon-level 16-pixel per vector cycle fixed-point RGB to Grayscale
static void fastRgbToGrayNeon(const uint8_t* __restrict src, uint8_t* __restrict dst, int pixelCount) {
    int i = 0;
    uint8x8_t vCoeffR = vdup_n_u8(77);
    uint8x8_t vCoeffG = vdup_n_u8(150);
    uint8x8_t vCoeffB = vdup_n_u8(29);

    for (; i <= pixelCount - 16; i += 16) {
        uint8x16x3_t rgb = vld3q_u8(src + (i * 3));

        uint16x8_t rLow = vmull_u8(vget_low_u8(rgb.val[0]), vCoeffR);
        uint16x8_t gLow = vmlal_u8(rLow, vget_low_u8(rgb.val[1]), vCoeffG);
        uint16x8_t grayLow = vmlal_u8(gLow, vget_low_u8(rgb.val[2]), vCoeffB);
        uint8x8_t resLow = vshrn_n_u16(grayLow, 8);

        uint16x8_t rHigh = vmull_u8(vget_high_u8(rgb.val[0]), vCoeffR);
        uint16x8_t gHigh = vmlal_u8(rHigh, vget_high_u8(rgb.val[1]), vCoeffG);
        uint16x8_t grayHigh = vmlal_u8(gHigh, vget_high_u8(rgb.val[2]), vCoeffB);
        uint8x8_t resHigh = vshrn_n_u16(grayHigh, 8);

        vst1q_u8(dst + i, vcombine_u8(resLow, resHigh));
    }
    for (; i < pixelCount; ++i) {
        int idx = i * 3;
        dst[i] = static_cast<uint8_t>((77 * src[idx] + 150 * src[idx + 1] + 29 * src[idx + 2]) >> 8);
    }
}

// Sub-millisecond motion diff: pure 128-bit vector register accumulation (zero memory round-trips)
static int fastMotionNeon(const uint8_t* __restrict curr, const uint8_t* __restrict prev, int count, uint8_t thresh) {
    int i = 0;
    uint8x16_t vThresh = vdupq_n_u8(thresh);
    uint32x4_t vAccum = vdupq_n_u32(0);

    for (; i <= count - 16; i += 16) {
        uint8x16_t vCurr = vld1q_u8(curr + i);
        uint8x16_t vPrev = vld1q_u8(prev + i);
        uint8x16_t vDiff = vabdq_u8(vCurr, vPrev);
        uint8x16_t mask = vcgtq_u8(vDiff, vThresh);
        uint8x16_t ones = vshrq_n_u8(mask, 7);
        uint16x8_t sum16 = vpaddlq_u8(ones);
        vAccum = vpadalq_u16(vAccum, sum16);
    }

    uint64x2_t sum64 = vpaddlq_u32(vAccum);
    int motion = static_cast<int>(vgetq_lane_u64(sum64, 0) + vgetq_lane_u64(sum64, 1));

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

    // Target ~600px dimension for optimal speed/precision ratio
    float maxDim = static_cast<float>(std::max(origW, origH));
    float targetDim = 600.0f;
    float scale = (maxDim > targetDim) ? (targetDim / maxDim) : 1.0f;

    cv::Mat small;
    if (scale < 1.0f) {
        cv::resize(srcRgba, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = srcRgba;
    }

    cv::Mat gray(small.rows, small.cols, CV_8UC1);
    if (small.channels() == 4) {
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        if (small.isContinuous() && gray.isContinuous()) {
            fastRgbaToGrayNeon(small.data, gray.data, small.cols * small.rows);
        } else {
            cv::cvtColor(small, gray, cv::COLOR_RGBA2GRAY);
        }
#else
        cv::cvtColor(small, gray, cv::COLOR_RGBA2GRAY);
#endif
    } else if (small.channels() == 3) {
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        if (small.isContinuous() && gray.isContinuous()) {
            fastRgbToGrayNeon(small.data, gray.data, small.cols * small.rows);
        } else {
            cv::cvtColor(small, gray, cv::COLOR_RGB2GRAY);
        }
#else
        cv::cvtColor(small, gray, cv::COLOR_RGB2GRAY);
#endif
    } else {
        gray = small;
    }

    // 1. Contrast enhancement via CLAHE to detect pages on faint/wooden/marble backgrounds
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.5, cv::Size(8, 8));
    cv::Mat enhancedGray;
    clahe->apply(gray, enhancedGray);

    // 2. Dual-channel filtering
    cv::Mat blurred;
    cv::GaussianBlur(enhancedGray, blurred, cv::Size(5, 5), 1.2);

    // Path A: Adaptive Canny Edge Detection with Otsu-guided threshold
    cv::Mat tmpDummy;
    double otsuThresh = cv::threshold(blurred, tmpDummy, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
    double lowerThresh = std::max(15.0, otsuThresh * 0.5);
    double upperThresh = std::min(240.0, otsuThresh * 1.0);
    cv::Mat edgesCanny;
    cv::Canny(blurred, edgesCanny, lowerThresh, upperThresh);

    // Path B: Morphological Gradient for solid color page borders
    cv::Mat kernel3 = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3));
    cv::Mat morphGrad;
    cv::morphologyEx(blurred, morphGrad, cv::MORPH_GRADIENT, kernel3);
    cv::Mat edgesMorph;
    cv::threshold(morphGrad, edgesMorph, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);

    // Merge both edge paths
    cv::Mat combinedEdges;
    cv::bitwise_or(edgesCanny, edgesMorph, combinedEdges);

    // Morphological close with 5x5 structuring element to seal broken page lines
    cv::Mat kernel5 = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5));
    cv::morphologyEx(combinedEdges, combinedEdges, cv::MORPH_CLOSE, kernel5);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(combinedEdges, contours, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    // Sort contours by area in descending order
    std::sort(contours.begin(), contours.end(), [](const std::vector<cv::Point>& a, const std::vector<cv::Point>& b) {
        return cv::contourArea(a, false) > cv::contourArea(b, false);
    });

    double totalSmallArea = static_cast<double>(small.cols * small.rows);
    double minArea = totalSmallArea * minAreaRatio;

    bool found = false;
    std::vector<cv::Point2f> bestCorners;
    double bestScore = 0.0;

    for (const auto& c : contours) {
        double area = cv::contourArea(c, false);
        if (area < minArea) break;

        // Convex hull to bridge slight corner indentations or thumb holds
        std::vector<cv::Point> hull;
        cv::convexHull(c, hull);
        double hullArea = cv::contourArea(hull, false);
        if (hullArea < minArea) continue;

        double peri = cv::arcLength(hull, true);

        // Progressive polygon approximation
        const float epsilons[] = { 0.015f, 0.020f, 0.025f, 0.030f, 0.035f, 0.045f, 0.060f };
        for (float eps : epsilons) {
            std::vector<cv::Point> approx;
            cv::approxPolyDP(hull, approx, eps * peri, true);

            if (approx.size() == 4 && cv::isContourConvex(approx)) {
                // Check edge angles to ensure quadrilateral is rectangular-like
                float maxCos = 0.0f;
                for (int j = 2; j < 6; ++j) {
                    cv::Point2f p0 = approx[j % 4];
                    cv::Point2f p1 = approx[(j - 1) % 4];
                    cv::Point2f p2 = approx[(j - 2) % 4];
                    cv::Point2f d1 = p0 - p1;
                    cv::Point2f d2 = p2 - p1;
                    float len1 = std::sqrt(d1.x * d1.x + d1.y * d1.y);
                    float len2 = std::sqrt(d2.x * d2.x + d2.y * d2.y);
                    if (len1 > 0 && len2 > 0) {
                        float cosA = std::abs((d1.x * d2.x + d1.y * d2.y) / (len1 * len2));
                        maxCos = std::max(maxCos, cosA);
                    }
                }

                // If angles are between ~65 and ~115 degrees
                if (maxCos < 0.42f) {
                    double score = area * (1.0f - maxCos);
                    if (score > bestScore) {
                        bestScore = score;
                        bestCorners.clear();
                        for (const auto& pt : approx) {
                            bestCorners.emplace_back(pt.x / scale, pt.y / scale);
                        }
                        found = true;
                        break;
                    }
                }
            }
        }

        if (found) break;

        // If no clean 4-point polygon matched, but large convex shape (>12% area), fit rotated rect bounding box
        if (!found && hullArea > (totalSmallArea * 0.12)) {
            cv::RotatedRect minRect = cv::minAreaRect(hull);
            cv::Point2f rectPts[4];
            minRect.points(rectPts);

            bestCorners.clear();
            for (int i = 0; i < 4; ++i) {
                bestCorners.emplace_back(rectPts[i].x / scale, rectPts[i].y / scale);
            }
            found = true;
            break;
        }
    }

    if (found && bestCorners.size() == 4) {
        orderCorners(bestCorners, outQuad);
        return true;
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
        cv::INTER_LANCZOS4,
        cv::BORDER_REPLICATE
    );

    return warped;
}

namespace docwarp_dewarp {

// ----------------------------- tunables --------------------------------------
namespace cfg {
constexpr int    kAnalysisLongSide  = 500;    // px, long side of analysis image
constexpr int    kNumStrips         = 48;     // vertical strips (32..64)
constexpr int    kMinValidStrips    = 12;     // need at least this many strips
constexpr double kMinXSpan          = 1.2;    // strip span in normalised x (max 2)
constexpr double kEdgeRelThresh     = 0.20;   // edge response vs. strip peak
constexpr double kEdgeAbsThresh     = 0.06;   // per-column edge response floor
constexpr double kMinInkFraction    = 0.004;  // min text coverage per strip
constexpr double kMinBlockHeightFrac= 0.15;   // text block height / image height
constexpr int    kIrlsIters         = 6;
constexpr double kTukeyC            = 4.685;
constexpr double kSigmaFloorFrac    = 0.004;  // robust sigma floor, fraction of H
constexpr double kMinCurvatureFrac  = 0.008;  // chord deviation / H below which the page counts as flat
constexpr double kMaxArcStretch     = 1.6;    // reject absurd unrolling ratios
constexpr double kMaxWidthGrow      = 1.5;    // output width cap vs. input width
constexpr int    kBandRows          = 128;    // remap band height (memory bound)
constexpr int    kProfileSamples    = 65;     // samples for sanity checks
}  // namespace cfg

// ------------------------- cubic polynomial ----------------------------------
// y(xn) = c0*xn^3 + c1*xn^2 + c2*xn + c3, with xn in [-1, 1] across the width.
struct Cubic {
    double c[4]{0.0, 0.0, 0.0, 0.0};
    inline double eval(double x)  const { return ((c[0] * x + c[1]) * x + c[2]) * x + c[3]; }
    inline double deriv(double x) const { return (3.0 * c[0] * x + 2.0 * c[1]) * x + c[2]; }
};

static double medianOf(std::vector<double>& v) {
    const size_t m = v.size() / 2;
    std::nth_element(v.begin(), v.begin() + static_cast<std::ptrdiff_t>(m), v.end());
    return v[m];
}

// Robust cubic least squares: Tukey-biweight IRLS, each step solved by SVD.
// Returns false if there are too few inliers or the fit is degenerate.
static bool fitCubicRobust(const std::vector<double>& xs, const std::vector<double>& ys,
                           double sigmaFloor, Cubic& out) {
    const int n = static_cast<int>(xs.size());
    if (n < 8 || ys.size() != xs.size()) return false;

    cv::Mat A(n, 4, CV_64F), b(n, 1, CV_64F), Aw(n, 4, CV_64F), bw(n, 1, CV_64F), coef;
    for (int i = 0; i < n; ++i) {
        const double x = xs[i];
        double* a = A.ptr<double>(i);
        a[0] = x * x * x; a[1] = x * x; a[2] = x; a[3] = 1.0;
        b.at<double>(i) = ys[i];
    }

    std::vector<double> w(n, 1.0), absRes(n), scratch(n);
    for (int it = 0; it < cfg::kIrlsIters; ++it) {
        // Row-scale by sqrt(w): minimises sum w_i * r_i^2.
        for (int i = 0; i < n; ++i) {
            const double sw = std::sqrt(w[i]);
            const double* a = A.ptr<double>(i);
            double* aw = Aw.ptr<double>(i);
            for (int j = 0; j < 4; ++j) aw[j] = a[j] * sw;
            bw.at<double>(i) = b.at<double>(i) * sw;
        }
        if (!cv::solve(Aw, bw, coef, cv::DECOMP_SVD)) return false;

        for (int i = 0; i < n; ++i) {
            const double* a = A.ptr<double>(i);
            double pred = 0.0;
            for (int j = 0; j < 4; ++j) pred += a[j] * coef.at<double>(j);
            absRes[i] = std::abs(b.at<double>(i) - pred);
        }
        scratch = absRes;
        const double sigma = std::max(1.4826 * medianOf(scratch), sigmaFloor);  // MAD
        const double cut = cfg::kTukeyC * sigma;
        for (int i = 0; i < n; ++i) {
            const double u = absRes[i] / cut;
            w[i] = (u < 1.0) ? (1.0 - u * u) * (1.0 - u * u) : 0.0;
        }
    }

    int inliers = 0;
    for (double wi : w) inliers += (wi > 1e-3);
    if (inliers < 8) return false;

    for (int j = 0; j < 4; ++j) {
        out.c[j] = coef.at<double>(j);
        if (!std::isfinite(out.c[j])) return false;
    }
    return true;
}

// Largest deviation of a curve from its own chord over xn in [-1, 1].
static double maxChordDeviation(const Cubic& c) {
    const double y0 = c.eval(-1.0), y1 = c.eval(1.0);
    double dev = 0.0;
    for (int i = 0; i < cfg::kProfileSamples; ++i) {
        const double xn = -1.0 + 2.0 * i / (cfg::kProfileSamples - 1);
        const double chord = y0 + (y1 - y0) * 0.5 * (xn + 1.0);
        dev = std::max(dev, std::abs(c.eval(xn) - chord));
    }
    return dev;
}

// Subpixel row position: centroid of the response over rows y-1..y+1.
static double centroidRow(const float* r, int y, int yLo, int yHi) {
    double sw = 0.0, sy = 0.0;
    for (int k = std::max(y - 1, yLo); k <= std::min(y + 1, yHi - 1); ++k) {
        sw += r[k]; sy += r[k] * k;
    }
    return sw > 1e-9 ? sy / sw : static_cast<double>(y);
}

// -------------------- curvature profile detection ----------------------------
// Finds y_top(x_i), y_bottom(x_i) per strip on the small gray image and fits the
// two cubics in FULL-RESOLUTION pixel units over normalised full-res x.
static bool estimateEnvelopes(const cv::Mat& gray, int W, int H, Cubic& top, Cubic& bot) {
    const int wS = gray.cols, hS = gray.rows;
    const double sx = static_cast<double>(W) / wS, sy = static_cast<double>(H) / hS;

    // Text blob mask: adaptive threshold copes with the shading near the spine.
    cv::Mat blur, bin;
    cv::GaussianBlur(gray, blur, cv::Size(3, 3), 0.0);
    const int block = std::max(15, (wS / 16) | 1);
    cv::adaptiveThreshold(blur, bin, 255, cv::ADAPTIVE_THRESH_MEAN_C,
                          cv::THRESH_BINARY_INV, block, 10);

    // Kill the border (book edge, table, fingers).
    const int bx = std::max(2, wS * 2 / 100), by = std::max(2, hS * 2 / 100);
    bin.rowRange(0, by).setTo(0);
    bin.rowRange(hS - by, hS).setTo(0);
    bin.colRange(0, bx).setTo(0);
    bin.colRange(wS - bx, wS).setTo(0);

    cv::medianBlur(bin, bin, 3);
    // Horizontal close merges glyphs and words into solid line blobs.
    const cv::Mat kern = cv::getStructuringElement(
        cv::MORPH_RECT, cv::Size(std::max(5, wS / 40) | 1, 3));
    cv::morphologyEx(bin, bin, cv::MORPH_CLOSE, kern);

    // Sobel G_y (scaled so a full 0->255 step gives 1.0). Positive = blob top
    // edge, negative = blob bottom edge.
    cv::Mat gy, pos, neg;
    cv::Sobel(bin, gy, CV_32F, 0, 1, 3, 1.0 / (4.0 * 255.0));
    cv::threshold(gy, pos, 0.0, 0.0, cv::THRESH_TOZERO);
    cv::Mat negG = -gy;
    cv::threshold(negG, neg, 0.0, 0.0, cv::THRESH_TOZERO);

    const int N = cfg::kNumStrips;
    std::vector<double> xsT, ysT, xsB, ysB;
    xsT.reserve(N); ysT.reserve(N); xsB.reserve(N); ysB.reserve(N);

    const int yMin = by, yMax = hS - by;
    cv::Mat rp, rn;
    for (int i = 0; i < N; ++i) {
        const int xa = i * wS / N, xb = (i + 1) * wS / N;
        const int sw = xb - xa;
        if (sw < 2) continue;
        const cv::Rect roi(xa, 0, sw, hS);

        if (cv::countNonZero(bin(roi)) < cfg::kMinInkFraction * sw * hS) continue;

        // Row-wise accumulation of edge energy across the strip.
        cv::reduce(pos(roi), rp, 1, cv::REDUCE_SUM, CV_32F);
        cv::reduce(neg(roi), rn, 1, cv::REDUCE_SUM, CV_32F);
        const float* p = rp.ptr<float>();
        const float* q = rn.ptr<float>();

        float pk = 0.f, nk = 0.f;
        for (int y = yMin; y < yMax; ++y) { pk = std::max(pk, p[y]); nk = std::max(nk, q[y]); }
        const float floorR = static_cast<float>(cfg::kEdgeAbsThresh * sw);
        if (pk < floorR || nk < floorR) continue;
        const float thrP = std::max(static_cast<float>(cfg::kEdgeRelThresh) * pk, floorR);
        const float thrN = std::max(static_cast<float>(cfg::kEdgeRelThresh) * nk, floorR);

        int yt = -1, yb = -1;
        for (int y = yMin; y < yMax; ++y)      if (p[y] >= thrP) { yt = y; break; }
        for (int y = yMax - 1; y >= yMin; --y) if (q[y] >= thrN) { yb = y; break; }
        if (yt < 0 || yb <= yt || (yb - yt) < cfg::kMinBlockHeightFrac * hS) continue;

        const double yTopS = centroidRow(p, yt, yMin, yMax);
        const double yBotS = centroidRow(q, yb, yMin, yMax);

        // Small-image index space -> full-res index space -> normalised x.
        const double xcS = 0.5 * (xa + xb - 1);
        const double xFull = (xcS + 0.5) * sx - 0.5;
        const double xn = 2.0 * xFull / (W - 1) - 1.0;
        xsT.push_back(xn); ysT.push_back((yTopS + 0.5) * sy - 0.5);
        xsB.push_back(xn); ysB.push_back((yBotS + 0.5) * sy - 0.5);
    }

    if (static_cast<int>(xsT.size()) < cfg::kMinValidStrips) return false;
    const auto mm = std::minmax_element(xsT.begin(), xsT.end());
    if (*mm.second - *mm.first < cfg::kMinXSpan) return false;

    const double sigmaFloor = cfg::kSigmaFloorFrac * H;
    return fitCubicRobust(xsT, ysT, sigmaFloor, top) &&
           fitCubicRobust(xsB, ysB, sigmaFloor, bot);
}

} // namespace docwarp_dewarp

cv::Mat CvEngine::dewarpBookPageCylindrical(const cv::Mat& srcRgba) {
    using namespace docwarp_dewarp;

    // ---- input validation / graceful fallback --------------------------------
    if (srcRgba.empty() || srcRgba.depth() != CV_8U ||
        srcRgba.cols < 64 || srcRgba.rows < 64 ||
        (srcRgba.channels() != 4 && srcRgba.channels() != 3 && srcRgba.channels() != 1)) {
        return srcRgba.clone();
    }
    const int W = srcRgba.cols, H = srcRgba.rows;

    // ---- 1. analysis image (~500 px) ------------------------------------------
    const double s = std::min(1.0, static_cast<double>(cfg::kAnalysisLongSide) / std::max(W, H));
    const int wS = std::max(64, cvRound(W * s)), hS = std::max(64, cvRound(H * s));
    cv::Mat small, gray;
    cv::resize(srcRgba, small, cv::Size(wS, hS), 0.0, 0.0, cv::INTER_AREA);
    switch (small.channels()) {
        case 4:  cv::cvtColor(small, gray, cv::COLOR_RGBA2GRAY); break;
        case 3:  cv::cvtColor(small, gray, cv::COLOR_RGB2GRAY);  break;
        default: gray = small;                                   break;
    }

    // ---- 2./3. envelopes + robust cubic surface fit ----------------------------
    Cubic top, bot;
    if (!estimateEnvelopes(gray, W, H, top, bot)) return srcRgba.clone();

    // Flat-sheet test: both envelopes are (nearly) straight lines.
    if (std::max(maxChordDeviation(top), maxChordDeviation(bot)) < cfg::kMinCurvatureFrac * H)
        return srcRgba.clone();

    // Sanity: envelopes stay on-canvas and keep a sensible, positive block height.
    double hbMean = 0.0, tMean = 0.0, hbMin = 1e30;
    for (int i = 0; i < cfg::kProfileSamples; ++i) {
        const double xn = -1.0 + 2.0 * i / (cfg::kProfileSamples - 1);
        const double T = top.eval(xn), B = bot.eval(xn);
        if (!std::isfinite(T) || !std::isfinite(B) ||
            T < -0.5 * H || B > 1.5 * H) return srcRgba.clone();
        hbMean += (B - T); tMean += T; hbMin = std::min(hbMin, B - T);
    }
    hbMean /= cfg::kProfileSamples;
    tMean  /= cfg::kProfileSamples;
    if (hbMean < 0.10 * H || hbMin < 0.25 * hbMean) return srcRgba.clone();

    // ---- 4. cumulative arc length S(x) ----------------------------------------
    // Subtract the chord slope so a merely tilted flat page isn't stretched;
    // only true curvature contributes to the unrolling.
    const double invWm1 = 1.0 / (W - 1);
    const double chordSlopeXn = 0.5 * (top.eval(1.0) - top.eval(-1.0));  // dy per unit xn
    std::vector<double> S(static_cast<size_t>(W), 0.0);
    for (int i = 1; i < W; ++i) {
        const double xn = 2.0 * (i - 0.5) * invWm1 - 1.0;                // midpoint rule
        const double d = (top.deriv(xn) - chordSlopeXn) * 2.0 * invWm1;  // dy/dx in px
        S[i] = S[i - 1] + std::sqrt(1.0 + d * d);                        // increment >= 1
    }
    const double Stotal = S[W - 1];
    if (!std::isfinite(Stotal) || Stotal / (W - 1) > cfg::kMaxArcStretch) return srcRgba.clone();

    const int Wout = std::max(W, std::min(cvRound(Stotal) + 1, cvRound(W * cfg::kMaxWidthGrow)));
    const double sPerU = Stotal / (Wout - 1);  // uniform u -> arc length

    // ---- per-column tables (the maps only depend on u, then affinely on v) -----
    // mapX(v,u) = xOfU[u]
    // mapY(v,u) = topOfU[u] + (v - mt) * scaleOfU[u]
    // so each output column linearly spans the local [y_top, y_bottom] of the
    // page, which straightens text lines and equalises the line height.
    std::vector<float> xOfU(Wout), topOfU(Wout), scaleOfU(Wout);
    {
        int k = 0;
        for (int u = 0; u < Wout; ++u) {
            const double sT = u * sPerU;
            while (k < W - 2 && S[k + 1] < sT) ++k;          // monotone sweep, O(W)
            const double seg = S[k + 1] - S[k];              // >= 1, no div-by-zero
            const double t = std::min(1.0, std::max(0.0, (sT - S[k]) / seg));
            const double x = k + t;                          // inverse interpolation
            const double xn = 2.0 * x * invWm1 - 1.0;
            const double T = top.eval(xn), B = bot.eval(xn);
            xOfU[u]     = static_cast<float>(x);
            topOfU[u]   = static_cast<float>(T);
            scaleOfU[u] = static_cast<float>((B - T) / hbMean);
        }
    }

    // ---- 5. dense remap in bands -----------------------------------------------
    cv::Mat dst(H, Wout, srcRgba.type());
    const int bandRows = std::min(H, cfg::kBandRows);
    cv::Mat mapX(bandRows, Wout, CV_32FC1), mapY(bandRows, Wout, CV_32FC1);  // allocated once

    const float* xo = xOfU.data();
    const float* to = topOfU.data();
    const float* so = scaleOfU.data();
    const size_t rowBytes = sizeof(float) * static_cast<size_t>(Wout);

    for (int r0 = 0; r0 < H; r0 += bandRows) {
        const int rows = std::min(bandRows, H - r0);

        // No heap allocation, branches or function calls in this loop.
#ifdef _OPENMP
        #pragma omp parallel for schedule(static)
#endif
        for (int r = 0; r < rows; ++r) {
            float* px = mapX.ptr<float>(r);
            float* py = mapY.ptr<float>(r);
            const float dv = static_cast<float>((r0 + r) - tMean);
            std::memcpy(px, xo, rowBytes);
            for (int u = 0; u < Wout; ++u) py[u] = to[u] + dv * so[u];
        }

        cv::Mat mx = mapX.rowRange(0, rows);
        cv::Mat my = mapY.rowRange(0, rows);
        cv::Mat dstBand = dst.rowRange(r0, r0 + rows);  // header only, writes into dst
        cv::remap(srcRgba, dstBand, mx, my, cv::INTER_CUBIC, cv::BORDER_REPLICATE);
    }
    return dst;
}

cv::Mat CvEngine::dewarpPageZucker(const cv::Mat& srcRgba) {
    return dewarpBookPageCylindrical(srcRgba);
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

    // Apply adaptive text-contrast & seamless paper texture blending
    return blendInpaintedTexture(srcRgba, inpaintedRgba, cleanMask);
}

cv::Mat CvEngine::blendInpaintedTexture(
    const cv::Mat& originalRgba,
    const cv::Mat& inpaintedRgba,
    const cv::Mat& mask8U
) {
    if (originalRgba.empty() || inpaintedRgba.empty() || mask8U.empty()) {
        return inpaintedRgba.empty() ? originalRgba.clone() : inpaintedRgba.clone();
    }

    int W = originalRgba.cols;
    int H = originalRgba.rows;

    cv::Mat cleanMask;
    if (mask8U.channels() > 1) {
        cv::cvtColor(mask8U, cleanMask, cv::COLOR_RGBA2GRAY);
    } else {
        cleanMask = mask8U;
    }
    cv::threshold(cleanMask, cleanMask, 64, 255, cv::THRESH_BINARY);

    // 1. Surrounding Paper Border Ring Extraction
    cv::Mat structElem = cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(19, 19));
    cv::Mat dilatedMask;
    cv::dilate(cleanMask, dilatedMask, structElem);
    cv::Mat borderRing;
    cv::subtract(dilatedMask, cleanMask, borderRing);

    // 2. Measure genuine paper background luminance and standard deviation
    cv::Scalar meanBorder, stdBorder;
    cv::meanStdDev(originalRgba, meanBorder, stdBorder, borderRing);

    cv::Scalar meanInpaint, stdInpaint;
    cv::meanStdDev(inpaintedRgba, meanInpaint, stdInpaint, cleanMask);

    double deltaR = meanBorder[0] - meanInpaint[0];
    double deltaG = meanBorder[1] - meanInpaint[1];
    double deltaB = meanBorder[2] - meanInpaint[2];

    // 3. Smooth Sigmoid Distance-Transform Feathering for seamless transition boundary
    cv::Mat distInside, distOutside;
    cv::distanceTransform(cleanMask, distInside, cv::DIST_L2, 3);
    cv::Mat invertedMask;
    cv::bitwise_not(cleanMask, invertedMask);
    cv::distanceTransform(invertedMask, distOutside, cv::DIST_L2, 3);

    cv::Mat signedDist = distInside - distOutside;

    // 4. Generate High-Frequency Paper Grain Synthesis (sigma ~ 2.0 - 5.0)
    cv::Mat noise(H, W, CV_32FC3);
    double grainSigma = std::max(2.0, std::min(5.0, (stdBorder[0] + stdBorder[1] + stdBorder[2]) / 3.0));
    cv::randn(noise, 0.0, grainSigma);

    // 5. Adaptive Text-Contrast Enhancement (Laplacian Unsharp Masking)
    cv::Mat blurredInpainted;
    cv::GaussianBlur(inpaintedRgba, blurredInpainted, cv::Size(3, 3), 1.0);

    cv::Mat result(H, W, CV_8UC4);

#ifdef _OPENMP
#pragma omp parallel for schedule(dynamic, 32)
#endif
    for (int y = 0; y < H; ++y) {
        const uint8_t* origRow = originalRgba.ptr<uint8_t>(y);
        const uint8_t* inpaintRow = inpaintedRgba.ptr<uint8_t>(y);
        const uint8_t* blurRow = blurredInpainted.ptr<uint8_t>(y);
        const float* distRow = signedDist.ptr<float>(y);
        const cv::Vec3f* noiseRow = noise.ptr<cv::Vec3f>(y);
        uint8_t* resRow = result.ptr<uint8_t>(y);

        for (int x = 0; x < W; ++x) {
            int px = x * 4;
            float d = distRow[x];

            if (d < -8.0f) {
                // Completely outside inpaint zone: preserve original untouched
                resRow[px + 0] = origRow[px + 0];
                resRow[px + 1] = origRow[px + 1];
                resRow[px + 2] = origRow[px + 2];
                resRow[px + 3] = origRow[px + 3];
            } else {
                // Sigmoid feather blend weight (smooth S-curve over transition band [-6, +6])
                float alpha = 1.0f / (1.0f + std::exp(-0.6f * d));

                // Color calibration adjusted inpainted pixel
                float cR = inpaintRow[px + 0] + static_cast<float>(deltaR * 0.75);
                float cG = inpaintRow[px + 1] + static_cast<float>(deltaG * 0.75);
                float cB = inpaintRow[px + 2] + static_cast<float>(deltaB * 0.75);

                // Add synthetic paper grain to break up plastic artificial flatness
                cR += noiseRow[x][0] * 0.5f;
                cG += noiseRow[x][1] * 0.5f;
                cB += noiseRow[x][2] * 0.5f;

                // Adaptive text contrast unsharp mask for intersecting ink strokes
                float hpR = static_cast<float>(inpaintRow[px + 0]) - static_cast<float>(blurRow[px + 0]);
                float hpG = static_cast<float>(inpaintRow[px + 1]) - static_cast<float>(blurRow[px + 1]);
                float hpB = static_cast<float>(inpaintRow[px + 2]) - static_cast<float>(blurRow[px + 2]);

                float grayLum = 0.299f * cR + 0.587f * cG + 0.114f * cB;
                if (grayLum < 190.0f) {
                    cR += hpR * 0.6f;
                    cG += hpG * 0.6f;
                    cB += hpB * 0.6f;
                }

                // Blend with original boundary using smooth feather alpha
                float finalR = (1.0f - alpha) * origRow[px + 0] + alpha * cR;
                float finalG = (1.0f - alpha) * origRow[px + 1] + alpha * cG;
                float finalB = (1.0f - alpha) * origRow[px + 2] + alpha * cB;

                resRow[px + 0] = static_cast<uint8_t>(std::clamp(finalR, 0.0f, 255.0f));
                resRow[px + 1] = static_cast<uint8_t>(std::clamp(finalG, 0.0f, 255.0f));
                resRow[px + 2] = static_cast<uint8_t>(std::clamp(finalB, 0.0f, 255.0f));
                resRow[px + 3] = origRow[px + 3];
            }
        }
    }

    return result;
}

float CvEngine::computeFrameMotion(const cv::Mat& curr, const cv::Mat& prev, int thresholdVal) {
    if (curr.empty() || prev.empty()) return 1.0f;
    if (curr.size() != prev.size()) return 1.0f;

    int totalPixels = curr.cols * curr.rows;
    if (totalPixels <= 0) return 0.0f;

    cv::Mat currGray, prevGray;
    if (curr.channels() == 4) {
        currGray.create(curr.rows, curr.cols, CV_8UC1);
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        if (curr.isContinuous() && currGray.isContinuous()) {
            fastRgbaToGrayNeon(curr.data, currGray.data, totalPixels);
        } else {
            cv::cvtColor(curr, currGray, cv::COLOR_RGBA2GRAY);
        }
#else
        cv::cvtColor(curr, currGray, cv::COLOR_RGBA2GRAY);
#endif
    } else {
        currGray = curr;
    }

    if (prev.channels() == 4) {
        prevGray.create(prev.rows, prev.cols, CV_8UC1);
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        if (prev.isContinuous() && prevGray.isContinuous()) {
            fastRgbaToGrayNeon(prev.data, prevGray.data, totalPixels);
        } else {
            cv::cvtColor(prev, prevGray, cv::COLOR_RGBA2GRAY);
        }
#else
        cv::cvtColor(prev, prevGray, cv::COLOR_RGBA2GRAY);
#endif
    } else {
        prevGray = prev;
    }

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

cv::Mat CvEngine::applyEBookClean(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    int W = srcRgba.cols;
    int H = srcRgba.rows;

    cv::Mat gray, bg, normalized, sharpened;

    // 1. Grayscale conversion
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba.clone();
    }

    // 2. Background Illumination Extraction (Morphological Dilate with adaptive resolution-scaled window)
    // Captures page shadows, spine fold gradient, and uneven lighting to completely flatten paper
    int kSize = std::max(25, std::min(W, H) / 25);
    if ((kSize % 2) == 0) kSize += 1;
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kSize, kSize));
    cv::morphologyEx(gray, bg, cv::MORPH_DILATE, kernel);

    // 3. Division Normalization (Zero-Noise Pure Paper White Background)
    // Divides background out so paper becomes 255 pure white without breaking text edges
    cv::divide(gray, bg, normalized, 255.0);

    // 4. Soft Sigmoid Contrast Mapping (Letters dark & computerized, background pure white)
    cv::Mat lut(1, 256, CV_8U);
    uint8_t* p = lut.ptr<uint8_t>();
    for (int i = 0; i < 256; ++i) {
        float val = static_cast<float>(i) / 255.0f;
        if (val > 0.80f) {
            p[i] = 255; // Paper noise and yellowing totally eliminated
        } else if (val < 0.32f) {
            p[i] = static_cast<uint8_t>(val * 0.35f * 255.0f); // Text made laser-jet deep black
        } else {
            // Smooth anti-aliased edge transition for computerized type look
            float norm = (val - 0.32f) / (0.80f - 0.32f);
            p[i] = static_cast<uint8_t>(norm * 255.0f);
        }
    }
    cv::LUT(normalized, lut, normalized);

    // 5. Border cleanup: Whiten outermost 1% border to erase accidental dark table edge lines from crop
    int borderX = std::max(2, W / 100);
    int borderY = std::max(2, H / 100);
    if (borderX > 0 && borderY > 0) {
        cv::rectangle(normalized, cv::Point(0, 0), cv::Point(W, borderY), cv::Scalar(255), -1);
        cv::rectangle(normalized, cv::Point(0, H - borderY), cv::Point(W, H), cv::Scalar(255), -1);
        cv::rectangle(normalized, cv::Point(0, 0), cv::Point(borderX, H), cv::Scalar(255), -1);
        cv::rectangle(normalized, cv::Point(W - borderX, 0), cv::Point(W, H), cv::Scalar(255), -1);
    }

    // 6. Micro-Edge Sharpening (Unsharp Masking for Laser Ink Sharpness)
    cv::Mat blur;
    cv::GaussianBlur(normalized, blur, cv::Size(0, 0), 1.5);
    cv::addWeighted(normalized, 1.6, blur, -0.6, 0, sharpened);

    // Output formatted back to RGBA for Android display
    cv::Mat dstRgba;
    cv::cvtColor(sharpened, dstRgba, cv::COLOR_GRAY2RGBA);
    return dstRgba;
}

cv::Mat CvEngine::applyMagicColor(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat rgb;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, rgb, cv::COLOR_RGBA2RGB);
    } else {
        rgb = srcRgba.clone();
    }

    // Convert to Lab for independent luminance and chrominance processing
    cv::Mat lab;
    cv::cvtColor(rgb, lab, cv::COLOR_RGB2Lab);

    std::vector<cv::Mat> channels(3);
    cv::split(lab, channels);

    // Estimate background luminance on L-channel
    int kSize = std::max(25, (std::min(rgb.cols, rgb.rows) / 20) | 1);
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kSize, kSize));
    cv::Mat lBg;
    cv::morphologyEx(channels[0], lBg, cv::MORPH_CLOSE, kernel);

    // Normalize L-channel to remove uneven lighting/shadows
    cv::Mat lClean;
    cv::divide(channels[0], lBg, lClean, 255.0);

    // Boost colors slightly in chroma channels (a* and b*)
    channels[1].convertTo(channels[1], CV_32F);
    channels[2].convertTo(channels[2], CV_32F);
    channels[1] = (channels[1] - 128.0f) * 1.20f + 128.0f;
    channels[2] = (channels[2] - 128.0f) * 1.20f + 128.0f;
    channels[1].convertTo(channels[1], CV_8U);
    channels[2].convertTo(channels[2], CV_8U);

    channels[0] = lClean;
    cv::Mat mergedLab;
    cv::merge(channels, mergedLab);

    cv::Mat outRgb;
    cv::cvtColor(mergedLab, outRgb, cv::COLOR_Lab2RGB);

    // Unsharp masking for ink crispness
    cv::Mat blurred;
    cv::GaussianBlur(outRgb, blurred, cv::Size(0, 0), 2.0);
    cv::Mat sharpened;
    cv::addWeighted(outRgb, 1.3, blurred, -0.3, 0, sharpened);

    cv::Mat dstRgba;
    cv::cvtColor(sharpened, dstRgba, cv::COLOR_RGB2RGBA);
    return dstRgba;
}

cv::Mat CvEngine::applySharpDocument(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat blurred;
    cv::GaussianBlur(srcRgba, blurred, cv::Size(0, 0), 3.0);
    cv::Mat sharpened;
    cv::addWeighted(srcRgba, 1.5, blurred, -0.5, 0, sharpened);
    return sharpened;
}

cv::Mat CvEngine::applyDeepInk(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat gray;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba.clone();
    }

    // Gamma curve to darken light pencil/ballpoint strokes while keeping paper white
    cv::Mat lut(1, 256, CV_8U);
    uint8_t* pLut = lut.ptr<uint8_t>();
    for (int i = 0; i < 256; ++i) {
        double norm = static_cast<double>(i) / 255.0;
        // Non-linear gamma compression
        double val = std::pow(norm, 1.6) * 255.0;
        pLut[i] = static_cast<uint8_t>(std::clamp(val, 0.0, 255.0));
    }

    cv::Mat deepGray;
    cv::LUT(gray, lut, deepGray);

    cv::Mat dstRgba;
    cv::cvtColor(deepGray, dstRgba, cv::COLOR_GRAY2RGBA);
    return dstRgba;
}

cv::Mat CvEngine::applyGrayscaleSmooth(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat gray;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba.clone();
    }

    // Adaptive histogram equalization for clear readable grayscale
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
    cv::Mat enhanced;
    clahe->apply(gray, enhanced);

    cv::Mat dstRgba;
    cv::cvtColor(enhanced, dstRgba, cv::COLOR_GRAY2RGBA);
    return dstRgba;
}

cv::Mat CvEngine::applyPaperBrightener(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat rgb;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, rgb, cv::COLOR_RGBA2RGB);
    } else {
        rgb = srcRgba.clone();
    }

    cv::Mat lab;
    cv::cvtColor(rgb, lab, cv::COLOR_RGB2Lab);

    std::vector<cv::Mat> channels(3);
    cv::split(lab, channels);

    // b* channel holds blue-yellow spectrum (>128 = yellow). Neutralize yellow:
    channels[2].convertTo(channels[2], CV_32F);
    channels[2] = (channels[2] - 128.0f) * 0.30f + 128.0f;
    channels[2].convertTo(channels[2], CV_8U);

    // Gently boost L-channel brightness
    cv::add(channels[0], cv::Scalar(15), channels[0]);

    cv::Mat mergedLab;
    cv::merge(channels, mergedLab);

    cv::Mat outRgb;
    cv::cvtColor(mergedLab, outRgb, cv::COLOR_Lab2RGB);

    cv::Mat dstRgba;
    cv::cvtColor(outRgb, dstRgba, cv::COLOR_RGB2RGBA);
    return dstRgba;
}

cv::Mat CvEngine::applyShadowErase(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat rgb;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, rgb, cv::COLOR_RGBA2RGB);
    } else {
        rgb = srcRgba.clone();
    }

    // Multi-scale background illumination division
    int kSize = std::max(31, (std::min(rgb.cols, rgb.rows) / 16) | 1);
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kSize, kSize));
    cv::Mat bg;
    cv::morphologyEx(rgb, bg, cv::MORPH_CLOSE, kernel);

    cv::Mat normalized;
    cv::divide(rgb, bg, normalized, 255.0);

    cv::Mat dstRgba;
    cv::cvtColor(normalized, dstRgba, cv::COLOR_RGB2RGBA);
    return dstRgba;
}

cv::Mat CvEngine::applyBlueprint(const cv::Mat& srcRgba) {
    if (srcRgba.empty()) return cv::Mat();

    cv::Mat gray;
    if (srcRgba.channels() == 4) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGBA2GRAY);
    } else if (srcRgba.channels() == 3) {
        cv::cvtColor(srcRgba, gray, cv::COLOR_RGB2GRAY);
    } else {
        gray = srcRgba.clone();
    }

    // Invert: text becomes bright, background becomes dark
    cv::Mat inv;
    cv::bitwise_not(gray, inv);

    // Map to Blueprint colors: Navy blue background (20, 45, 95) with cyan text (120, 230, 255)
    cv::Mat dstRgba(srcRgba.size(), CV_8UC4);
    int total = gray.cols * gray.rows;
    const uint8_t* pInv = inv.ptr<uint8_t>();
    uint8_t* pDst = dstRgba.ptr<uint8_t>();

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (int i = 0; i < total; ++i) {
        float t = static_cast<float>(pInv[i]) / 255.0f;
        int idx = i * 4;
        pDst[idx + 0] = static_cast<uint8_t>(20.0f + t * 100.0f);  // R
        pDst[idx + 1] = static_cast<uint8_t>(45.0f + t * 185.0f);  // G
        pDst[idx + 2] = static_cast<uint8_t>(95.0f + t * 160.0f);  // B
        pDst[idx + 3] = 255;                                        // A
    }

    return dstRgba;
}

cv::Mat CvEngine::applyFilterById(const cv::Mat& srcRgba, int filterId) {
    switch (filterId) {
        case 0: // ORIGINAL
            return srcRgba.clone();
        case 1: // AUTO_BEST fallback to EBOOK_CLEAN
        case 2: // EBOOK_CLEAN
            return applyEBookClean(srcRgba);
        case 3: // MAGIC_COLOR
            return applyMagicColor(srcRgba);
        case 4: // B&W (Clean anti-aliased laser ink, zero paper noise)
            return applyEBookClean(srcRgba);
        case 5: // SHARP_DOCUMENT
            return applySharpDocument(srcRgba);
        case 6: // SUPER_CONTRAST (DEEP_INK)
            return applyDeepInk(srcRgba);
        case 7: // GRAYSCALE_SMOOTH
            return applyGrayscaleSmooth(srcRgba);
        case 8: // YELLOW_REMOVER (PAPER_BRIGHT)
            return applyPaperBrightener(srcRgba);
        case 9: // SHADOW_KILLER (SHADOW_ERASE)
            return applyShadowErase(srcRgba);
        case 10: // BLUEPRINT
            return applyBlueprint(srcRgba);
        default:
            return srcRgba.clone();
    }
}

} // namespace vflat
