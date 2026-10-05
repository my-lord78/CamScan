package com.scanku.app.ui.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanku.app.R
import com.scanku.app.core.util.FileNames
import com.scanku.app.data.db.PageEntity
import com.scanku.app.export.ShareHelper
import com.scanku.app.ui.common.ConfirmDeleteDialog
import com.scanku.app.ui.common.FileImage
import com.scanku.app.ui.common.RenameDialog
import com.scanku.app.ui.common.TextResultDialog
import com.scanku.app.ui.common.rememberImageImporter
import com.scanku.app.ui.common.scopedViewModel
import com.scanku.app.ui.common.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    documentId: Long,
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onImported: (String) -> Unit,
    onOpenPage: (Long) -> Unit,
) {
    val vm = scopedViewModel { c ->
        DocumentViewModel(documentId, c.documents, c.export, c.settings, ocr = { c.ocr })
    }
    val context = LocalContext.current
    val load by vm.document.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importImage = rememberImageImporter(onImported)
    val back by rememberUpdatedState(onBack)
    val shareChooser = stringResource(R.string.share_chooser)

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<PageEntity?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.savePdfTo(uri)
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DocumentEvent.ShareFiles -> ShareHelper.shareFiles(context, e.files, e.mime, shareChooser)
                is DocumentEvent.ShowText -> ocrText = e.text
                is DocumentEvent.Message -> context.toast(e.res)
            }
        }
    }
    // Document deleted (here or elsewhere) → leave.
    LaunchedEffect(load) {
        if (load is DocLoad.Missing) back()
    }

    val doc = (load as? DocLoad.Loaded)?.doc
    val hasPages = pages.isNotEmpty()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            doc?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::sharePdf, enabled = hasPages && !busy) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_pdf))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_save_pdf)) },
                                    leadingIcon = { Icon(Icons.Filled.SaveAlt, null) },
                                    enabled = hasPages && !busy,
                                    onClick = {
                                        menuOpen = false
                                        savePdf.launch(FileNames.sanitize(doc?.name.orEmpty()) + ".pdf")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share_images)) },
                                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    enabled = hasPages && !busy,
                                    onClick = { menuOpen = false; vm.shareImages() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { menuOpen = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete_doc)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = importImage) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.action_import))
                    }
                    IconButton(onClick = vm::extractText, enabled = hasPages && !busy) {
                        Icon(Icons.Filled.TextFields, contentDescription = stringResource(R.string.action_ocr))
                    }
                    IconButton(onClick = vm::sharePdf, enabled = hasPages && !busy) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = stringResource(R.string.action_share_pdf))
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = onAddPage) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = stringResource(R.string.action_add_page))
                    }
                },
            )
        },
    ) { padding ->
        if (load is DocLoad.Loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                    PageCard(
                        page = page,
                        number = index + 1,
                        isFirst = index == 0,
                        isLast = index == pages.lastIndex,
                        file = vm.pageFile(page),
                        onOpen = { onOpenPage(page.id) },
                        onMove = { delta -> vm.movePage(page.id, delta) },
                        onDelete = { pageToDelete = page },
                    )
                }
            }
        }
    }

    if (renaming && doc != null) {
        RenameDialog(
            initial = doc.name,
            onDismiss = { renaming = false },
            onConfirm = { vm.rename(it); renaming = false },
        )
    }
    if (confirmDelete && doc != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_doc_title),
            message = stringResource(R.string.delete_doc_message, doc.name),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete() },
        )
    }
    pageToDelete?.let { page ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_page_title),
            message = stringResource(R.string.delete_page_message),
            onDismiss = { pageToDelete = null },
            onConfirm = { vm.deletePage(page.id); pageToDelete = null },
        )
    }
    ocrText?.let { text ->
        TextResultDialog(text = text, onDismiss = { ocrText = null })
    }
}

@Composable
private fun PageCard(
    page: PageEntity,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    file: java.io.File?,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val pageLabel = stringResource(R.string.page_number, number)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Box {
            FileImage(
                file = file,
                maxDim = 480,
                contentDescription = pageLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(bottomEnd = 8.dp),
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.move_earlier, number))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options_for, pageLabel))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete_page)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
            IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.move_later, number))
            }
        }
    }
}
