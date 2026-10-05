package com.scanku.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanku.app.R
import com.scanku.app.data.db.DocumentSummary
import com.scanku.app.export.ShareHelper
import com.scanku.app.ui.common.ConfirmDeleteDialog
import com.scanku.app.ui.common.FileImage
import com.scanku.app.ui.common.RenameDialog
import com.scanku.app.ui.common.appContainer
import com.scanku.app.ui.common.rememberImageImporter
import com.scanku.app.ui.common.scopedViewModel
import com.scanku.app.ui.common.toast
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onImported: (String) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = scopedViewModel { HomeViewModel(it.documents, it.export, it.settings) }
    val container = appContainer()
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importImage = rememberImageImporter(onImported)
    val shareTitle = stringResource(R.string.share_pdf_chooser)

    var renameTarget by remember { mutableStateOf<DocumentSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentSummary?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.SharePdf -> ShareHelper.shareFiles(context, listOf(event.file), "application/pdf", shareTitle)
                HomeEvent.ExportFailed -> context.toast(R.string.export_failed)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = importImage) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.action_import))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text(stringResource(R.string.action_scan)) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            val docs = documents
            when {
                docs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                docs.isEmpty() -> EmptyState(searching = query.isNotBlank(), onScan = onScan)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(docs, key = { it.id }) { doc ->
                        DocumentCard(
                            doc = doc,
                            coverFile = doc.coverFile?.let { runCatching { container.documents.pageFile(doc.id, it) }.getOrNull() },
                            onOpen = { onOpenDocument(doc.id) },
                            onRename = { renameTarget = doc },
                            onShare = { vm.sharePdf(doc.id) },
                            onDelete = { deleteTarget = doc },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { doc ->
        RenameDialog(
            initial = doc.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                vm.rename(doc.id, name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { doc ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_doc_title),
            message = stringResource(R.string.delete_doc_message, doc.name),
            onDismiss = { deleteTarget = null },
            onConfirm = {
                vm.delete(doc.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun DocumentCard(
    doc: DocumentSummary,
    coverFile: java.io.File?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val dateText = remember(doc.updatedAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(doc.updatedAt))
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Box {
            FileImage(
                file = coverFile,
                maxDim = 480,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            )
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Text(
                    pluralStringResource(R.plurals.page_count, doc.pageCount, doc.pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options_for, doc.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share_pdf)) },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, onScan: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (searching) R.string.empty_search_title else R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (searching) R.string.empty_search_body else R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!searching) {
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onScan) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_scan_first))
            }
        }
    }
}
