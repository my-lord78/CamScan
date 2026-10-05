package com.scanku.app.imaging

import android.graphics.Bitmap
import com.scanku.app.core.geometry.Quad
import com.scanku.app.core.util.ImageMath
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Scan enhancement modes, in the order they appear in the filter bar. */
enum class ScanFilter { MAGIC, ORIGINAL, GRAYSCALE, BLACK_WHITE, LIGHTEN }

/** Perspective correction and document filters. Every function returns a NEW bitmap. */
object ImageProcessor {

    /**
     * Flattens the region inside [quadPx] (pixel coordinates of [src]) into a rectangle.
     * Output long side is capped at [maxDim].
     */
    fun warp(src: Bitmap, quadPx: Quad, maxDim: Int = BitmapIo.WORKING_MAX_DIM): Bitmap {
        val (w0, h0) = quadPx.outputSize()
        val s = min(1f, maxDim.toFloat() / max(w0, h0))
        val w = (w0 * s).roundToInt().coerceAtLeast(1)
        val h = (h0 * s).roundToInt().coerceAtLeast(1)

        val srcMat = Mat()
        val out = Mat()
        val srcPts = MatOfPoint2f(*quadPx.points.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(w - 1.0, 0.0),
            Point(w - 1.0, h - 1.0),
            Point(0.0, h - 1.0),
        )
        var transform: Mat? = null
        try {
            Utils.bitmapToMat(src, srcMat)
            transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(
                srcMat, out, transform, Size(w.toDouble(), h.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE,
            )
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, bmp)
            return bmp
        } finally {
            srcMat.release()
            out.release()
            srcPts.release()
            dstPts.release()
            transform?.release()
        }
    }

    fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL) return src.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat()
        try {
            Utils.bitmapToMat(src, rgba)
            val longSide = max(rgba.cols(), rgba.rows())
            when (filter) {
                ScanFilter.MAGIC -> magicColor(rgba, longSide)
                ScanFilter.GRAYSCALE -> grayscale(rgba)
                ScanFilter.BLACK_WHITE -> blackWhite(rgba, longSide)
                ScanFilter.LIGHTEN -> rgba.convertTo(rgba, -1, 1.15, 25.0)
                ScanFilter.ORIGINAL -> Unit
            }
            val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bmp)
            return bmp
        } finally {
            rgba.release()
        }
    }

    /**
     * Removes shadows and evens the paper to white while keeping ink colour: estimate each
     * channel's background (dilate removes text, median smooths), divide it out via
     * 255 − |plane − background|, then stretch to full range.
     * Kernel sizes scale with the image so preview and full-res output look the same.
     */
    private fun magicColor(rgba: Mat, longSide: Int) {
        val channels = ArrayList<Mat>()
        Core.split(rgba, channels)
        val dilateK = ImageMath.oddKernel(longSide, 170, 3)
        val medianK = min(ImageMath.oddKernel(longSide, 55, 5), 255)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(dilateK.toDouble(), dilateK.toDouble()))
        try {
            for (i in 0 until 3) {
                removeBackground(channels[i], kernel, medianK)
            }
            Core.merge(channels, rgba)
        } finally {
            kernel.release()
            channels.forEach { it.release() }
        }
    }

    private fun removeBackground(plane: Mat, kernel: Mat, medianK: Int) {
        val bg = Mat()
        val diff = Mat()
        try {
            Imgproc.dilate(plane, bg, kernel)
            Imgproc.medianBlur(bg, bg, medianK)
            Core.absdiff(plane, bg, diff)
            Core.bitwise_not(diff, diff)
            Core.normalize(diff, plane, 0.0, 255.0, Core.NORM_MINMAX)
        } finally {
            bg.release()
            diff.release()
        }
    }

    private fun grayscale(rgba: Mat) {
        val gray = Mat()
        val equalized = Mat()
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(gray, equalized)
            Imgproc.cvtColor(equalized, rgba, Imgproc.COLOR_GRAY2RGBA)
        } finally {
            gray.release()
            equalized.release()
        }
    }

    /** Crisp black text on white paper: shadow removal, then local (adaptive) thresholding. */
    private fun blackWhite(rgba: Mat, longSide: Int) {
        val gray = Mat()
        val bw = Mat()
        val dilateK = ImageMath.oddKernel(longSide, 170, 3)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(dilateK.toDouble(), dilateK.toDouble()))
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            removeBackground(gray, kernel, min(ImageMath.oddKernel(longSide, 55, 5), 255))
            val block = ImageMath.oddKernel(longSide, 60, 11)
            Imgproc.adaptiveThreshold(
                gray, bw, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                block, 12.0,
            )
            Imgproc.cvtColor(bw, rgba, Imgproc.COLOR_GRAY2RGBA)
        } finally {
            gray.release()
            bw.release()
            kernel.release()
        }
    }
}
