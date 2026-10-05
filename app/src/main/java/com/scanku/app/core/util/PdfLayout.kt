package com.scanku.app.core.util

import com.scanku.app.core.geometry.FitRect
import com.scanku.app.core.geometry.fitCenter
import kotlin.math.roundToInt

/** Paper sizes in PDF points (1/72 inch). [FIT] makes each page match its image's aspect. */
enum class PdfPageSize(val widthPt: Int, val heightPt: Int) {
    A4(595, 842),
    LETTER(612, 792),
    FIT(595, 0),
}

data class PdfPagePlan(val pageWidth: Int, val pageHeight: Int, val image: FitRect)

/** Pure layout math for one PDF page — unit-tested on the JVM. */
object PdfLayout {
    const val MARGIN_PT = 18f

    fun plan(size: PdfPageSize, imageWidth: Int, imageHeight: Int): PdfPagePlan {
        require(imageWidth > 0 && imageHeight > 0) { "Image size must be positive" }
        if (size == PdfPageSize.FIT) {
            val w = size.widthPt
            val h = (w.toFloat() * imageHeight / imageWidth).roundToInt().coerceAtLeast(1)
            return PdfPagePlan(w, h, FitRect(0f, 0f, w.toFloat(), h.toFloat()))
        }
        // Rotate the paper for landscape scans instead of shrinking them.
        val landscape = imageWidth > imageHeight
        val pw = if (landscape) size.heightPt else size.widthPt
        val ph = if (landscape) size.widthPt else size.heightPt
        val box = fitCenter(
            imageWidth.toFloat(), imageHeight.toFloat(),
            pw - 2 * MARGIN_PT, ph - 2 * MARGIN_PT,
        )
        return PdfPagePlan(pw, ph, box.copy(left = box.left + MARGIN_PT, top = box.top + MARGIN_PT))
    }
}
