package com.scanku.app.core.scan

import com.scanku.app.core.geometry.Pt
import com.scanku.app.core.geometry.Quad
import org.junit.Assert.assertEquals
import org.junit.Test

class StabilityTrackerTest {

    private val base = Quad.inset(1f, 1f, 0.1f)
    private fun shifted(dx: Float) = base.map { Pt(it.x + dx, it.y) }

    @Test
    fun `progress reaches 1 after holding still for the required time`() {
        val t = StabilityTracker(requiredMillis = 1000, tolerance = 0.02f)
        assertEquals(0f, t.update(base, 0), 0f)
        assertEquals(0.5f, t.update(base, 500), 1e-4f)
        assertEquals(1f, t.update(base, 1000), 0f)
        assertEquals("progress is capped at 1", 1f, t.update(base, 5000), 0f)
    }

    @Test
    fun `small jitter within tolerance keeps the timer running`() {
        val t = StabilityTracker(requiredMillis = 1000, tolerance = 0.02f)
        t.update(base, 0)
        t.update(shifted(0.01f), 400)
        assertEquals(0.8f, t.update(shifted(-0.01f), 800), 1e-4f)
    }

    @Test
    fun `movement beyond tolerance restarts the timer`() {
        val t = StabilityTracker(requiredMillis = 1000, tolerance = 0.02f)
        t.update(base, 0)
        assertEquals(0f, t.update(shifted(0.05f), 900), 0f)
        assertEquals(0.3f, t.update(shifted(0.05f), 1200), 1e-4f)
    }

    @Test
    fun `slow drift is caught because movement is measured against the anchor`() {
        // 0.005 per frame is far under tolerance frame-to-frame, but accumulates past it.
        val t = StabilityTracker(requiredMillis = 1000, tolerance = 0.022f)
        var progress = 0f
        for (i in 0..8) progress = t.update(shifted(i * 0.005f), i * 100L)
        // Drift passed 0.022 at i=5 (t=500) → anchor reset there, so only 0.3 s held at t=800.
        assertEquals(0.3f, progress, 1e-4f)
    }

    @Test
    fun `losing the document resets progress`() {
        val t = StabilityTracker(requiredMillis = 1000, tolerance = 0.02f)
        t.update(base, 0)
        t.update(base, 900)
        assertEquals(0f, t.update(null, 950), 0f)
        assertEquals(0f, t.update(base, 1000), 0f)
    }

    @Test
    fun `reset starts a new window`() {
        val t = StabilityTracker(requiredMillis = 1000, tolerance = 0.02f)
        t.update(base, 0)
        t.update(base, 999)
        t.reset()
        assertEquals(0f, t.update(base, 1500), 0f)
    }
}
