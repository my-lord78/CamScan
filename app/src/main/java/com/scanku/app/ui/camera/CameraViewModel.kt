package com.scanku.app.ui.camera

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.scanku.app.camera.DocumentAnalyzer
import com.scanku.app.core.geometry.Quad
import com.scanku.app.core.scan.StabilityTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds live detection state and decides when to auto-capture.
 * Frame callbacks arrive on the analysis thread; state lives in thread-safe StateFlows
 * and the tracker is guarded by [lock].
 */
class CameraViewModel : ViewModel() {

    private val lock = Any()
    private val tracker = StabilityTracker(requiredMillis = AUTO_CAPTURE_HOLD_MS, tolerance = 0.025f)

    private val _quad = MutableStateFlow<Quad?>(null)
    val quad: StateFlow<Quad?> = _quad.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests.asSharedFlow()

    @Volatile
    var autoCapture: Boolean = true

    @Volatile
    private var resumedAt = 0L

    val analyzer = DocumentAnalyzer(::onFrame)

    private fun onFrame(quad: Quad?) {
        val now = SystemClock.elapsedRealtime()
        _quad.value = quad
        val p = synchronized(lock) { tracker.update(quad, now) }
        val auto = autoCapture
        _progress.value = if (auto) p else 0f
        // Cooldown stops an immediate re-capture of the same page when returning to the camera.
        if (auto && p >= 1f && now - resumedAt > RESUME_COOLDOWN_MS) {
            requestCapture()
        }
    }

    /** Called whenever the camera screen (re)enters composition. */
    fun onResume() {
        resumedAt = SystemClock.elapsedRealtime()
        synchronized(lock) { tracker.reset() }
        _progress.value = 0f
        _capturing.value = false
    }

    /** Manual shutter or auto-capture. Ignored while a capture is in flight. */
    fun requestCapture() {
        if (!_capturing.compareAndSet(expect = false, update = true)) return
        synchronized(lock) { tracker.reset() }
        _progress.value = 0f
        if (!_captureRequests.tryEmit(Unit)) _capturing.value = false
    }

    fun onCaptureFinished() {
        _capturing.value = false
    }

    private companion object {
        const val AUTO_CAPTURE_HOLD_MS = 1_100L
        const val RESUME_COOLDOWN_MS = 1_500L
    }
}
