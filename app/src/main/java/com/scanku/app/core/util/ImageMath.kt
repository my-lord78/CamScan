package com.scanku.app.core.util

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure sizing helpers shared by decoding and filtering (unit-tested on the JVM). */
object ImageMath {

    /**
     * Power-of-two `inSampleSize` that keeps the decoded long side at most 1.5× [maxDim];
     * the remainder is scaled precisely afterwards. Bounds peak memory for huge or
     * maliciously large images (decompression bombs) while keeping 12 MP photos at full res.
     */
    fun inSampleSize(width: Int, height: Int, maxDim: Int): Int {
        require(maxDim > 0) { "maxDim must be positive" }
        val longSide = max(width, height)
        var sample = 1
        while (longSide / sample > maxDim * 3 / 2) sample *= 2
        return sample
    }

    /** Scales (w, h) down so the long side is ≤ [maxDim]; never scales up. */
    fun scaledToMax(width: Int, height: Int, maxDim: Int): Pair<Int, Int> {
        val longSide = max(width, height)
        if (longSide <= maxDim) return width to height
        val s = maxDim.toFloat() / longSide
        return (width * s).roundToInt().coerceAtLeast(1) to (height * s).roundToInt().coerceAtLeast(1)
    }

    /** Odd kernel size proportional to the image, at least [min]. OpenCV blur kernels must be odd. */
    fun oddKernel(longSide: Int, divisor: Int, min: Int): Int {
        val k = max(min, longSide / divisor)
        return if (k % 2 == 0) k + 1 else k
    }
}
