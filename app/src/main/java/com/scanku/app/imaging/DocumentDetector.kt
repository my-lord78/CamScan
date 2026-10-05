package com.scanku.app.imaging

import android.graphics.Bitmap
import com.scanku.app.core.geometry.Pt
import com.scanku.app.core.geometry.Quad
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the outline of a paper document in a frame.
 *
 * Pipeline (on a ≤640 px copy, so it runs per camera frame):
 *  1. Gaussian blur → Canny with Otsu-derived thresholds (adapts to lighting) → dilate to close gaps.
 *  2. Fallback: Otsu binarisation + morphological close (bright page on dark desk).
 *  For each, the largest contours are simplified with approxPolyDP; the biggest convex
 *  4-gon covering ≥ [MIN_AREA_FRACTION] of the frame wins. Contours that simplify to more
 *  than 4 points are retried through their convex hull (handles dog-eared corners).
 *
 * All results are normalized to 0..1 of the input so callers can map them to any resolution.
 */
object DocumentDetector {
    private const val DETECT_MAX_DIM = 640.0
    private const val MIN_AREA_FRACTION = 0.12
    private const val CONTOURS_TO_CHECK = 6
    private val EPSILONS = doubleArrayOf(0.02, 0.03, 0.045)

    /** Detects in an ARGB bitmap. Returns a normalized quad or null. */
    fun detect(bitmap: Bitmap): Quad? {
        if (!OpenCv.ready) return null
        val longSide = max(bitmap.width, bitmap.height)
        val small = if (longSide > DETECT_MAX_DIM) {
            val s = DETECT_MAX_DIM / longSide
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * s).roundToInt().coerceAtLeast(1),
                (bitmap.height * s).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return detectGray(gray)
        } finally {
            rgba.release()
            gray.release()
            if (small !== bitmap) small.recycle()
        }
    }

    /** Detects in a single-channel 8-bit image. Returns a normalized quad or null. */
    fun detectGray(gray: Mat): Quad? {
        if (!OpenCv.ready || gray.empty()) return null
        val scale = min(1.0, DETECT_MAX_DIM / max(gray.cols(), gray.rows()))
        val small = Mat()
        try {
            if (scale < 1.0) {
                Imgproc.resize(
                    gray, small,
                    Size(gray.cols() * scale, gray.rows() * scale),
                    0.0, 0.0, Imgproc.INTER_AREA,
                )
            } else {
                gray.copyTo(small)
            }
            val w = small.cols().toFloat()
            val h = small.rows().toFloat()
            val quad = findQuad(small) ?: return null
            return quad.scale(1f / w, 1f / h)
        } finally {
            small.release()
        }
    }

    private fun findQuad(gray: Mat): Quad? {
        val minArea = gray.cols() * gray.rows() * MIN_AREA_FRACTION
        val blurred = Mat()
        val binary = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        try {
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            val otsu = Imgproc.threshold(
                blurred, binary, 0.0, 255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU,
            )
            Imgproc.Canny(blurred, edges, max(10.0, otsu * 0.5), max(30.0, otsu))
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, closeKernel)

            val fromEdges = bestQuadIn(edges, minArea)
            val fromBinary = bestQuadIn(binary, minArea)
            return listOfNotNull(fromEdges, fromBinary).maxByOrNull { it.area() }
        } finally {
            blurred.release()
            binary.release()
            edges.release()
            kernel.release()
            closeKernel.release()
        }
    }

    private fun bestQuadIn(mask: Mat, minArea: Double): Quad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            // findContours may modify its input on older OpenCV versions; work on a copy.
            val work = mask.clone()
            Imgproc.findContours(work, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            work.release()

            val candidates = contours
                .map { it to Imgproc.contourArea(it) }
                .filter { it.second >= minArea }
                .sortedByDescending { it.second }
                .take(CONTOURS_TO_CHECK)

            for ((contour, _) in candidates) {
                quadFromContour(contour)?.let { q ->
                    if (q.isConvex() && q.area() >= minArea) return q
                }
            }
            return null
        } finally {
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun quadFromContour(contour: MatOfPoint): Quad? {
        approxFour(contour)?.let { return it }
        // Retry via convex hull: torn or folded corners add extra vertices to the raw contour.
        val hullIdx = MatOfInt()
        try {
            Imgproc.convexHull(contour, hullIdx)
            val pts = contour.toArray()
            val hullPts = hullIdx.toArray().map { pts[it] }
            if (hullPts.size < 4) return null
            val hull = MatOfPoint(*hullPts.toTypedArray())
            try {
                return approxFour(hull)
            } finally {
                hull.release()
            }
        } finally {
            hullIdx.release()
        }
    }

    private fun approxFour(contour: MatOfPoint): Quad? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        try {
            val peri = Imgproc.arcLength(curve, true)
            for (eps in EPSILONS) {
                Imgproc.approxPolyDP(curve, approx, eps * peri, true)
                val p: Array<Point> = approx.toArray()
                if (p.size == 4) {
                    return Quad.fromUnordered(p.map { Pt(it.x.toFloat(), it.y.toFloat()) })
                }
            }
            return null
        } finally {
            curve.release()
            approx.release()
        }
    }
}
