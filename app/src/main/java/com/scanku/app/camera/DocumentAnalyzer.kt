package com.scanku.app.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.scanku.app.core.geometry.Quad
import com.scanku.app.imaging.DocumentDetector
import com.scanku.app.imaging.OpenCv
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * CameraX analyzer: runs document detection on the luminance (Y) plane of each preview frame.
 * Only Y is needed for edge detection, so no YUV→RGB conversion is paid per frame.
 *
 * [onResult] receives a quad normalized to the *upright* frame (rotation already applied), or null.
 * Runs on the analysis executor thread.
 */
class DocumentAnalyzer(private val onResult: (Quad?) -> Unit) : ImageAnalysis.Analyzer {

    private var buffer = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        try {
            if (!OpenCv.ready) {
                onResult(null)
                return
            }
            onResult(detect(image))
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis failed", e)
            onResult(null)
        } finally {
            image.close()
        }
    }

    private fun detect(image: ImageProxy): Quad? {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val src = plane.buffer
        src.rewind()

        val needed = width * height
        if (buffer.size != needed) buffer = ByteArray(needed)
        if (rowStride == width) {
            src.get(buffer, 0, needed)
        } else {
            // Rows are padded; copy only the visible pixels of each row.
            for (row in 0 until height) {
                src.position(row * rowStride)
                src.get(buffer, row * width, width)
            }
        }

        val gray = Mat(height, width, CvType.CV_8UC1)
        val upright = Mat()
        try {
            gray.put(0, 0, buffer)
            when (image.imageInfo.rotationDegrees) {
                90 -> Core.rotate(gray, upright, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(gray, upright, Core.ROTATE_180)
                270 -> Core.rotate(gray, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> gray.copyTo(upright)
            }
            return DocumentDetector.detectGray(upright)
        } finally {
            gray.release()
            upright.release()
        }
    }

    private companion object {
        const val TAG = "DocumentAnalyzer"
    }
}
