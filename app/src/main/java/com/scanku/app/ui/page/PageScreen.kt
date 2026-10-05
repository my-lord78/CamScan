package com.scanku.app.ui.page

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scanku.app.R
import com.scanku.app.data.DocumentRepository
import com.scanku.app.data.db.PageEntity
import com.scanku.app.export.ExportService
import com.scanku.app.export.ShareHelper
import com.scanku.app.imaging.BitmapIo
import com.scanku.app.ocr.OcrEngine
import com.scanku.app.ui.common.ConfirmDeleteDialog
import com.scanku.app.ui.common.FileImage
import com.scanku.app.ui.common.TextResultDialog
import com.scanku.app.ui.common.scopedViewModel
import com.scanku.app.ui.common.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PageEvent {
    data class ShowText(val text: String) : PageEvent
    data class Share(val file: File) : PageEvent
    data class Message(@StringRes val res: Int) : PageEvent
    data object Deleted : PageEvent
}

class PageViewModel(
    private val pageId: Long,
    private val repo: DocumentRepository,
    private val export: ExportService,
    private val ocr: () -> OcrEngine,
) : ViewModel() {

    val page: StateFlow<PageEntity?> = repo.observePage(pageId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<PageEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun file(p: PageEntity): File? = runCatching { repo.pageFile(p) }.getOrNull()

    private fun runBusy(@StringRes failure: Int, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                Log.e("PageViewModel", "Operation failed", e)
                _events.send(PageEvent.Message(failure))
            } finally {
                _busy.value = false
            }
        }
    }

    fun recognize() = runBusy(R.string.ocr_failed) {
        val p = repo.getPage(pageId) ?: return@runBusy
        val text = p.ocrText ?: withContext(Dispatchers.Default) {
            val bmp = BitmapIo.decodeSampled(repo.pageFile(p), 2400) ?: return@withContext ""
            try {
                ocr().recognize(bmp)
            } finally {
                bmp.recycle()
            }
        }.also { repo.setOcrText(p.id, it) }
        _events.send(PageEvent.ShowText(text))
    }

    fun share() = runBusy(R.string.export_failed) {
        val p = repo.getPage(pageId) ?: return@runBusy
        val files = export.exportImages(p.documentId, listOf(p.id))
        files.firstOrNull()?.let { _events.send(PageEvent.Share(it)) }
    }

    fun delete() = runBusy(R.string.delete_failed) {
        repo.deletePage(pageId)
        _events.send(PageEvent.Deleted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(pageId: Long, onBack: () -> Unit) {
    val vm = scopedViewModel { c -> PageViewModel(pageId, c.documents, c.export, ocr = { c.ocr }) }
    val context = LocalContext.current
    val page by vm.page.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val back by rememberUpdatedState(onBack)
    val shareChooser = stringResource(R.string.share_chooser)
    var text by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PageEvent.ShowText -> text = e.text
                is PageEvent.Share -> ShareHelper.shareFiles(context, listOf(e.file), "image/jpeg", shareChooser)
                is PageEvent.Message -> context.toast(e.res)
                PageEvent.Deleted -> back()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.page_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = vm::recognize, enabled = !busy) {
                        Icon(Icons.Filled.TextFields, contentDescription = stringResource(R.string.action_ocr))
                    }
                    IconButton(onClick = vm::share, enabled = !busy) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_image))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_page))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            page?.let { p -> ZoomableFileImage(vm.file(p)) }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_page_title),
            message = stringResource(R.string.delete_page_message),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete() },
        )
    }
    text?.let { TextResultDialog(text = it, onDismiss = { text = null }) }
}

/** Pinch-to-zoom (1×–5×) and pan. */
@Composable
private fun ZoomableFileImage(file: File?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    FileImage(
        file = file,
        maxDim = 2400,
        contentDescription = stringResource(R.string.page_title),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
