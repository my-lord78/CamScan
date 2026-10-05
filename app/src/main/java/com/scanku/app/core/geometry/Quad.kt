package com.scanku.app.core.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A 2-D point. Pure Kotlin so geometry is unit-testable on the JVM without Android. */
data class Pt(val x: Float, val y: Float) {
    fun distanceTo(other: Pt): Float = hypot(x - other.x, y - other.y)
    fun clamp(maxX: Float, maxY: Float): Pt = Pt(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
}

/**
 * A document outline. Corners are always stored clockwise in screen coordinates (y down):
 * top-left, top-right, bottom-right, bottom-left.
 */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {

    val points: List<Pt> get() = listOf(tl, tr, br, bl)

    /** Polygon area via the shoelace formula. */
    fun area(): Float {
        val p = points
        var sum = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    /** True when the four corners form a strictly convex, non-degenerate quadrilateral. */
    fun isConvex(): Boolean {
        val p = points
        var sign = 0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < EPSILON) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    fun scale(sx: Float, sy: Float): Quad = map { Pt(it.x * sx, it.y * sy) }

    fun map(transform: (Pt) -> Pt): Quad = Quad(transform(tl), transform(tr), transform(br), transform(bl))

    fun withCorner(index: Int, p: Pt): Quad = when (index) {
        0 -> copy(tl = p)
        1 -> copy(tr = p)
        2 -> copy(br = p)
        3 -> copy(bl = p)
        else -> throw IndexOutOfBoundsException("Quad corner index $index")
    }

    /** Largest distance any single corner moved between two quads. */
    fun maxCornerDistance(other: Quad): Float =
        points.zip(other.points).maxOf { (a, b) -> a.distanceTo(b) }

    /**
     * Target size for a perspective warp: the longer of each pair of opposite edges,
     * so the flattened page keeps its real proportions.
     */
    fun outputSize(): Pair<Int, Int> {
        val w = max(tl.distanceTo(tr), bl.distanceTo(br))
        val h = max(tl.distanceTo(bl), tr.distanceTo(br))
        return w.roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
    }

    companion object {
        private const val EPSILON = 1e-6f

        /** The whole frame of a w×h image. */
        fun full(w: Float, h: Float): Quad = Quad(Pt(0f, 0f), Pt(w, 0f), Pt(w, h), Pt(0f, h))

        /** The frame inset by [fraction] of each side — a sensible default when nothing is detected. */
        fun inset(w: Float, h: Float, fraction: Float): Quad {
            val dx = w * fraction
            val dy = h * fraction
            return Quad(Pt(dx, dy), Pt(w - dx, dy), Pt(w - dx, h - dy), Pt(dx, h - dy))
        }

        /**
         * Orders four arbitrary points clockwise starting from the top-left-most corner.
         * Angular sort around the centroid is robust to any rotation, unlike the classic
         * x+y / x−y heuristic which breaks near 45°.
         */
        fun fromUnordered(points: List<Pt>): Quad {
            require(points.size == 4) { "A quad needs exactly 4 points, got ${points.size}" }
            val cx = points.sumOf { it.x.toDouble() } / 4.0
            val cy = points.sumOf { it.y.toDouble() } / 4.0
            // Image coordinates have y pointing down, so ascending atan2 runs clockwise on screen.
            val sorted = points.sortedBy { atan2(it.y - cy, it.x - cx) }
            val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            val o = List(4) { sorted[(start + it) % 4] }
            return Quad(o[0], o[1], o[2], o[3])
        }
    }
}

/** Placement of a source rectangle fitted (letterboxed) and centred inside a box. */
data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Fits a srcW×srcH rectangle inside boxW×boxH preserving aspect ratio (like ContentScale.Fit). */
fun fitCenter(srcW: Float, srcH: Float, boxW: Float, boxH: Float): FitRect {
    require(srcW > 0f && srcH > 0f) { "Source size must be positive" }
    if (boxW <= 0f || boxH <= 0f) return FitRect(0f, 0f, 0f, 0f)
    val s = min(boxW / srcW, boxH / srcH)
    val w = srcW * s
    val h = srcH * s
    return FitRect((boxW - w) / 2f, (boxH - h) / 2f, w, h)
}
