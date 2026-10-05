package com.scanku.app.ui.review

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanku.app.R
import com.scanku.app.core.geometry.Pt
import com.scanku.app.core.geometry.Quad
import com.scanku.app.data.CaptureStore
import com.scanku.app.data.DocumentRepository
import com.scanku.app.imaging.BitmapIo
import com.scanku.app.imaging.DocumentDetector
import com.scanku.app.imaging.ImageProcessor
import com.scanku.app.imaging.OpenCv
import com.scanku.app.imaging.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReviewStep { CROP, FILTER }

data class ReviewUiState(
    val loading: Boolean = true,
    @StringRes val error: Int? = null,
    val step: ReviewStep = ReviewStep.CROP,
    val source: ImageBitmap? = null,
    /** Crop outline, normalized 0..1 to [source]. */
    val quad: Quad = Quad.inset(1f, 1f, 0.05f),
    val autoDetected: Boolean = false,
    val filter: ScanFilter = ScanFilter.MAGIC,
    val rotation: Int = 0,
    val preview: ImageBitmap? = null,
    val processing: Boolean = false,
) {
    val quadValid: Boolean get() = quad.isConvex() && quad.area() > MIN_AREA

    private companion object {
        const val MIN_AREA = 0.01f
    }
}

sealed interface ReviewEvent {
    data class Saved(val documentId: Long, val scanMore: Boolean) : ReviewEvent
    data object SaveFailed : ReviewEvent
}

/**
 * Crop → filter → save pipeline for one capture.
 * Full-resolution work happens off the main thread; the filter preview runs on a ≤1400 px copy
 * so switching filters stays instant, and the chosen filter is re-applied at full res on save.
 */
class ReviewViewModel(
    private val documentId: Long?,
    private val captureName: String,
    private val captures: CaptureStore,
    private val repo: DocumentRepository,
    defaultFilter: ScanFilter,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState(filter = defaultFilter))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var full: Bitmap? = null
    private var autoQuad: Quad? = null
    private var warpedFull: Bitmap? = null
    private var warpedPreview: Bitmap? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) {
                val file = captures.resolve(captureName) ?: return@withContext null
                val bmp = BitmapIo.decodeSampled(file, BitmapIo.WORKING_MAX_DIM) ?: return@withContext null
                bmp to DocumentDetector.detect(bmp)
            }
            if (loaded == null) {
                _state.update { it.copy(loading = false, error = R.string.review_load_failed) }
                return@launch
            }
            val (bmp, detected) = loaded
            full = bmp
            autoQuad = detected
            _state.update {
                it.copy(
                    loading = false,
                    source = bmp.asImageBitmap(),
                    quad = detected ?: Quad.inset(1f, 1f, 0.05f),
                    autoDetected = detected != null,
                )
            }
        }
    }

    fun moveCorner(index: Int, p: Pt) {
        _state.update { it.copy(quad = it.quad.withCorner(index, p.clamp(1f, 1f))) }
    }

    fun useAutoDetection() {
        _state.update { it.copy(quad = autoQuad ?: Quad.inset(1f, 1f, 0.05f)) }
    }

    fun useFullImage() {
        _state.update { it.copy(quad = Quad.full(1f, 1f)) }
    }

    fun toCrop() {
        _state.update { it.copy(step = ReviewStep.CROP) }
    }

    /** Applies the perspective crop and moves to the filter step. */
    fun confirmCrop() {
        val src = full ?: return
        val s = _state.value
        if (!s.quadValid || s.processing) return
        if (!OpenCv.ready) {
            _state.update { it.copy(error = R.string.review_opencv_missing) }
            return
        }
        _state.update { it.copy(processing = true) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                try {
                    val quadPx = s.quad.scale(src.width.toFloat(), src.height.toFloat())
                    val warped = ImageProcessor.warp(src, quadPx)
                    warpedFull = warped
                    warpedPreview = BitmapIo.scaleDown(warped.copy(Bitmap.Config.ARGB_8888, false), PREVIEW_MAX_DIM)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Warp failed", e)
                    false
                }
            }
            if (ok) {
                _state.update { it.copy(processing = false, step = ReviewStep.FILTER) }
                renderPreview()
            } else {
                _state.update { it.copy(processing = false, error = R.string.review_process_failed) }
            }
        }
    }

    fun setFilter(filter: ScanFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        renderPreview()
    }

    fun rotate(delta: Int) {
        _state.update { it.copy(rotation = ((it.rotation + delta) % 360 + 360) % 360) }
        renderPreview()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun renderPreview() {
        val base = warpedPreview ?: return
        val filter = _state.value.filter
        val rotation = _state.value.rotation
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val img = withContext(Dispatchers.Default) {
                val filtered = ImageProcessor.applyFilter(base, filter)
                val rotated = BitmapIo.rotate(filtered, rotation)
                if (rotated !== filtered) filtered.recycle() // never displayed, safe to free now
                rotated.asImageBitmap()
            }
            _state.update { it.copy(preview = img) }
        }
    }

    fun save(scanMore: Boolean) {
        val warped = warpedFull ?: return
        val s = _state.value
        if (s.processing) return
        _state.update { it.copy(processing = true) }
        viewModelScope.launch {
            try {
                val docId = withContext(Dispatchers.Default) {
                    val filtered = ImageProcessor.applyFilter(warped, s.filter)
                    val output = BitmapIo.rotate(filtered, s.rotation)
                    try {
                        repo.addPage(documentId, output, defaultDocumentName())
                    } finally {
                        if (output !== filtered) output.recycle()
                        filtered.recycle()
                    }
                }
                captures.delete(captureName)
                _events.send(ReviewEvent.Saved(docId, scanMore))
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
                _state.update { it.copy(processing = false) }
                _events.send(ReviewEvent.SaveFailed)
            }
        }
    }

    /** Leaving without saving discards the raw capture. */
    fun discard() {
        captures.delete(captureName)
    }

    private fun defaultDocumentName(): String =
        "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())

    private companion object {
        const val TAG = "ReviewViewModel"
        const val PREVIEW_MAX_DIM = 1400
    }
}
