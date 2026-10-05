package com.scanku.app.ui.document

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanku.app.R
import com.scanku.app.data.DocumentRepository
import com.scanku.app.data.db.DocumentEntity
import com.scanku.app.data.db.PageEntity
import com.scanku.app.export.ExportService
import com.scanku.app.imaging.BitmapIo
import com.scanku.app.ocr.OcrEngine
import com.scanku.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DocLoad {
    data object Loading : DocLoad
    data object Missing : DocLoad
    data class Loaded(val doc: DocumentEntity) : DocLoad
}

sealed interface DocumentEvent {
    data class ShareFiles(val files: List<File>, val mime: String) : DocumentEvent
    data class ShowText(val text: String) : DocumentEvent
    data class Message(@StringRes val res: Int) : DocumentEvent
}

class DocumentViewModel(
    private val documentId: Long,
    private val repo: DocumentRepository,
    private val export: ExportService,
    private val settings: SettingsRepository,
    private val ocr: () -> OcrEngine,
) : ViewModel() {

    val document: StateFlow<DocLoad> = repo.observeDocument(documentId)
        .map { if (it == null) DocLoad.Missing else DocLoad.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocLoad.Loading)

    val pages: StateFlow<List<PageEntity>> = repo.observePages(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<DocumentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun pageFile(page: PageEntity): File? = runCatching { repo.pageFile(page) }.getOrNull()

    /** Runs one long operation at a time with a progress indicator and a generic failure message. */
    private fun runBusy(@StringRes failure: Int, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Operation failed", e)
                _events.send(DocumentEvent.Message(failure))
            } finally {
                _busy.value = false
            }
        }
    }

    fun sharePdf() = runBusy(R.string.export_failed) {
        val file = export.exportPdf(documentId, settings.settings.value.pdfPageSize)
        _events.send(DocumentEvent.ShareFiles(listOf(file), "application/pdf"))
    }

    fun savePdfTo(uri: Uri) = runBusy(R.string.export_failed) {
        export.exportPdfTo(documentId, settings.settings.value.pdfPageSize, uri)
        _events.send(DocumentEvent.Message(R.string.pdf_saved))
    }

    fun shareImages() = runBusy(R.string.export_failed) {
        _events.send(DocumentEvent.ShareFiles(export.exportImages(documentId), "image/jpeg"))
    }

    /** OCRs pages that have no text yet (results are cached in the DB), then shows everything. */
    fun extractText() = runBusy(R.string.ocr_failed) {
        val all = repo.getPages(documentId)
        val engine = ocr()
        val parts = all.mapIndexed { index, page ->
            val text = page.ocrText ?: withContext(Dispatchers.Default) {
                val bmp = BitmapIo.decodeSampled(repo.pageFile(page), OCR_MAX_DIM)
                    ?: return@withContext ""
                try {
                    engine.recognize(bmp)
                } finally {
                    bmp.recycle()
                }
            }.also { repo.setOcrText(page.id, it) }
            if (all.size == 1) text else "— Halaman ${index + 1} —\n$text"
        }
        _events.send(DocumentEvent.ShowText(parts.joinToString("\n\n").trim()))
    }

    fun rename(name: String) {
        viewModelScope.launch { repo.rename(documentId, name) }
    }

    fun delete() {
        viewModelScope.launch { repo.deleteDocument(documentId) }
    }

    fun movePage(pageId: Long, delta: Int) {
        viewModelScope.launch { repo.movePage(pageId, delta) }
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch { repo.deletePage(pageId) }
    }

    private companion object {
        const val TAG = "DocumentViewModel"
        const val OCR_MAX_DIM = 2400
    }
}
