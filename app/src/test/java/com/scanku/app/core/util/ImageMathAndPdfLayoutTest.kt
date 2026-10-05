package com.scanku.app.core.util

import com.scanku.app.core.geometry.FitRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathAndPdfLayoutTest {

    @Test
    fun `inSampleSize keeps a 12MP photo at full resolution for a 3200 cap`() {
        assertEquals(1, ImageMath.inSampleSize(4000, 3000, 3200))
    }

    @Test
    fun `inSampleSize subsamples huge images to bound memory`() {
        val s = ImageMath.inSampleSize(12000, 9000, 3200)
        assertEquals(4, s)
        assertTrue("decoded long side must be ≤ 1.5× cap", 12000 / s <= 4800)
    }

    @Test
    fun `inSampleSize bounds a decompression-bomb sized header`() {
        val s = ImageMath.inSampleSize(60000, 60000, 3200)
        assertTrue(60000 / s <= 4800)
    }

    @Test
    fun `scaledToMax preserves aspect and never upscales`() {
        assertEquals(3200 to 2400, ImageMath.scaledToMax(4000, 3000, 3200))
        assertEquals(800 to 600, ImageMath.scaledToMax(800, 600, 3200))
    }

    @Test
    fun `oddKernel is always odd and at least the minimum`() {
        assertEquals(3, ImageMath.oddKernel(100, 170, 3))
        assertEquals(19, ImageMath.oddKernel(3200, 170, 3))
        assertEquals(59, ImageMath.oddKernel(3200, 55, 5))
        for (side in 1..5000 step 37) {
            assertEquals(1, ImageMath.oddKernel(side, 55, 5) % 2)
        }
    }

    @Test
    fun `A4 portrait scan is centred inside the margins`() {
        val plan = PdfLayout.plan(PdfPageSize.A4, 1000, 1414)
        assertEquals(595, plan.pageWidth)
        assertEquals(842, plan.pageHeight)
        val r = plan.image
        assertTrue(r.left >= PdfLayout.MARGIN_PT - 0.01f)
        assertTrue(r.top >= PdfLayout.MARGIN_PT - 0.01f)
        assertTrue(r.left + r.width <= 595 - PdfLayout.MARGIN_PT + 0.01f)
        assertTrue(r.top + r.height <= 842 - PdfLayout.MARGIN_PT + 0.01f)
    }

    @Test
    fun `landscape scan rotates the paper instead of shrinking`() {
        val plan = PdfLayout.plan(PdfPageSize.A4, 1414, 1000)
        assertEquals(842, plan.pageWidth)
        assertEquals(595, plan.pageHeight)
    }

    @Test
    fun `FIT pages take the image aspect with no margin`() {
        val plan = PdfLayout.plan(PdfPageSize.FIT, 1000, 500)
        assertEquals(595, plan.pageWidth)
        assertEquals(298, plan.pageHeight)
        assertEquals(FitRect(0f, 0f, 595f, 298f), plan.image)
    }
}
