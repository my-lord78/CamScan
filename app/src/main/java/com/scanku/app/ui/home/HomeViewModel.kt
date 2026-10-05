package com.scanku.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanku.app.data.DocumentRepository
import com.scanku.app.data.db.DocumentSummary
import com.scanku.app.export.ExportService
import com.scanku.app.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface HomeEvent {
    data class SharePdf(val file: File) : HomeEvent
    data object ExportFailed : HomeEvent
}

class HomeViewModel(
    private val repo: DocumentRepository,
    private val export: ExportService,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null while the first query is loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<DocumentSummary>?> = _query
        .flatMapLatest { repo.observeSummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q.take(100)
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteDocument(id) }
    }

    fun sharePdf(id: Long) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                _events.send(HomeEvent.SharePdf(export.exportPdf(id, settings.settings.value.pdfPageSize)))
            } catch (e: IOException) {
                _events.send(HomeEvent.ExportFailed)
            } finally {
                _busy.value = false
            }
        }
    }
}
