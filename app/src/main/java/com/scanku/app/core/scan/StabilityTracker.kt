package com.scanku.app.core.scan

import com.scanku.app.core.geometry.Quad

/**
 * Decides when a detected document has been held steady long enough to auto-capture.
 *
 * Movement is measured against an *anchor* (the first quad of the current steady window),
 * not the previous frame — otherwise a slow drift of 1px per frame would never reset.
 * Time is injected so the logic is deterministic in tests.
 *
 * @param requiredMillis how long the document must stay still.
 * @param tolerance max corner movement allowed, in the quad's own units (normalized 0..1 here).
 */
class StabilityTracker(
    private val requiredMillis: Long = 1_000L,
    private val tolerance: Float = 0.025f,
) {
    private var anchor: Quad? = null
    private var anchorTime: Long = 0L

    fun reset() {
        anchor = null
        anchorTime = 0L
    }

    /** Feeds a frame result; returns steadiness progress in 0..1 (1 = ready to capture). */
    fun update(quad: Quad?, nowMillis: Long): Float {
        if (quad == null) {
            reset()
            return 0f
        }
        val current = anchor
        if (current == null || current.maxCornerDistance(quad) > tolerance) {
            anchor = quad
            anchorTime = nowMillis
            return 0f
        }
        val held = (nowMillis - anchorTime).coerceAtLeast(0L)
        return (held.toFloat() / requiredMillis).coerceAtMost(1f)
    }
}
