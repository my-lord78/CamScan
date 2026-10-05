package com.scanku.app.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadTest {

    private val square = Quad(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f), Pt(0f, 10f))

    @Test
    fun `fromUnordered orders shuffled axis-aligned corners clockwise from top-left`() {
        val shuffled = listOf(Pt(10f, 10f), Pt(0f, 0f), Pt(0f, 10f), Pt(10f, 0f))
        assertEquals(square, Quad.fromUnordered(shuffled))
    }

    @Test
    fun `fromUnordered handles a page rotated 45 degrees`() {
        // Diamond: the x+y / x-y heuristic is ambiguous here; angular sort must still be clockwise.
        val q = Quad.fromUnordered(listOf(Pt(5f, 10f), Pt(0f, 5f), Pt(10f, 5f), Pt(5f, 0f)))
        assertTrue("result must be convex (clockwise, non-crossing)", q.isConvex())
        assertEquals(Pt(5f, 0f), q.tl)
        assertEquals(Pt(10f, 5f), q.tr)
        assertEquals(Pt(5f, 10f), q.br)
        assertEquals(Pt(0f, 5f), q.bl)
    }

    @Test
    fun `fromUnordered handles a slightly tilted perspective quad`() {
        val tl = Pt(12f, 30f)
        val tr = Pt(410f, 18f)
        val br = Pt(430f, 590f)
        val bl = Pt(5f, 560f)
        assertEquals(Quad(tl, tr, br, bl), Quad.fromUnordered(listOf(br, tl, bl, tr)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromUnordered rejects anything other than four points`() {
        Quad.fromUnordered(listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f)))
    }

    @Test
    fun `area of a 10x10 square is 100`() {
        assertEquals(100f, square.area(), 1e-4f)
    }

    @Test
    fun `isConvex accepts a rectangle and rejects a bow-tie`() {
        assertTrue(square.isConvex())
        val bowTie = Quad(Pt(0f, 0f), Pt(10f, 10f), Pt(10f, 0f), Pt(0f, 10f))
        assertFalse(bowTie.isConvex())
    }

    @Test
    fun `isConvex rejects a concave dart and a degenerate collapsed quad`() {
        val dart = Quad(Pt(0f, 0f), Pt(10f, 0f), Pt(3f, 3f), Pt(0f, 10f))
        assertFalse(dart.isConvex())
        val collapsed = Quad(Pt(0f, 0f), Pt(5f, 0f), Pt(10f, 0f), Pt(0f, 10f))
        assertFalse(collapsed.isConvex())
    }

    @Test
    fun `outputSize uses the longer of each pair of opposite edges`() {
        val trapezoid = Quad(Pt(20f, 0f), Pt(80f, 0f), Pt(100f, 200f), Pt(0f, 200f))
        val (w, h) = trapezoid.outputSize()
        assertEquals(100, w)
        assertEquals(201, h) // slanted side: hypot(20, 200) = 200.998
    }

    @Test
    fun `outputSize never returns zero`() {
        val point = Quad(Pt(1f, 1f), Pt(1f, 1f), Pt(1f, 1f), Pt(1f, 1f))
        assertEquals(1 to 1, point.outputSize())
    }

    @Test
    fun `scale maps normalized corners to pixels`() {
        val n = Quad.full(1f, 1f).scale(640f, 480f)
        assertEquals(Quad.full(640f, 480f), n)
    }

    @Test
    fun `withCorner replaces exactly the requested corner`() {
        val moved = square.withCorner(2, Pt(12f, 11f))
        assertEquals(Pt(12f, 11f), moved.br)
        assertEquals(square.tl, moved.tl)
        assertEquals(square.tr, moved.tr)
        assertEquals(square.bl, moved.bl)
    }

    @Test
    fun `maxCornerDistance reports the largest single-corner movement`() {
        val moved = square.withCorner(1, Pt(13f, 4f))
        assertEquals(5f, square.maxCornerDistance(moved), 1e-4f)
    }

    @Test
    fun `inset shrinks each side by the fraction`() {
        assertEquals(Quad(Pt(1f, 2f), Pt(9f, 2f), Pt(9f, 18f), Pt(1f, 18f)), Quad.inset(10f, 20f, 0.1f))
    }

    @Test
    fun `fitCenter letterboxes a wide image inside a tall box`() {
        val r = fitCenter(400f, 200f, 100f, 300f)
        assertEquals(FitRect(0f, 125f, 100f, 50f), r)
    }

    @Test
    fun `fitCenter pillarboxes a tall image inside a wide box`() {
        val r = fitCenter(300f, 600f, 400f, 300f)
        assertEquals(FitRect(125f, 0f, 150f, 300f), r)
    }

    @Test
    fun `fitCenter returns an empty rect for an unmeasured box`() {
        assertEquals(FitRect(0f, 0f, 0f, 0f), fitCenter(10f, 10f, 0f, 100f))
    }
}
