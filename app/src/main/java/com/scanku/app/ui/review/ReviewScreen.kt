package com.scanku.app.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanku.app.R
import com.scanku.app.imaging.ScanFilter
import com.scanku.app.ui.common.scopedViewModel
import com.scanku.app.ui.common.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    documentId: Long?,
    captureName: String,
    onBack: () -> Unit,
    onSavedScanMore: (Long) -> Unit,
    onSavedDone: (Long) -> Unit,
) {
    val vm = scopedViewModel {
        ReviewViewModel(
            documentId = documentId,
            captureName = captureName,
            captures = it.captures,
            repo = it.documents,
            defaultFilter = it.settings.settings.value.defaultFilter,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scanMore by rememberUpdatedState(onSavedScanMore)
    val done by rememberUpdatedState(onSavedDone)

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ReviewEvent.Saved -> if (e.scanMore) scanMore(e.documentId) else done(e.documentId)
                ReviewEvent.SaveFailed -> context.toast(R.string.save_failed)
            }
        }
    }

    val leave = {
        vm.discard()
        onBack()
    }
    BackHandler(enabled = !state.processing) {
        if (state.step == ReviewStep.FILTER) vm.toCrop() else leave()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (state.step == ReviewStep.CROP) R.string.review_crop_title else R.string.review_filter_title,
                            ),
                        )
                    },
                    navigationIcon = {
                        if (state.step == ReviewStep.CROP) {
                            IconButton(onClick = leave, enabled = !state.processing) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.review_discard))
                            }
                        } else {
                            IconButton(onClick = vm::toCrop, enabled = !state.processing) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back_to_crop))
                            }
                        }
                    },
                )
                if (state.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(12.dp)) {
                    if (state.step == ReviewStep.CROP) CropControls(state, vm) else FilterControls(state, vm)
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val source = state.source
            when {
                state.loading -> CircularProgressIndicator()
                source == null -> Text(
                    stringResource(R.string.review_load_failed),
                    modifier = Modifier.padding(24.dp),
                )
                state.step == ReviewStep.CROP -> CropEditor(
                    image = source,
                    quad = state.quad,
                    onCornerChange = vm::moveCorner,
                    contentDescription = stringResource(R.string.review_crop_a11y),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
                else -> state.preview?.let {
                    Image(
                        bitmap = it,
                        contentDescription = stringResource(R.string.review_preview_a11y),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                } ?: CircularProgressIndicator()
            }
        }
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(stringResource(err)) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun CropControls(state: ReviewUiState, vm: ReviewViewModel) {
    if (!state.quadValid && !state.loading) {
        Text(
            stringResource(R.string.review_invalid_quad),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = vm::useAutoDetection, enabled = !state.loading) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.review_auto))
        }
        TextButton(onClick = vm::useFullImage, enabled = !state.loading) {
            Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.review_full))
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = vm::confirmCrop,
            enabled = !state.loading && !state.processing && state.quadValid,
        ) { Text(stringResource(R.string.action_next)) }
    }
}

@Composable
private fun FilterControls(state: ReviewUiState, vm: ReviewViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanFilter.entries.forEach { f ->
            FilterChip(
                selected = state.filter == f,
                onClick = { vm.setFilter(f) },
                label = { Text(stringResource(filterLabel(f))) },
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { vm.rotate(-90) }) {
            Icon(Icons.Filled.RotateLeft, contentDescription = stringResource(R.string.rotate_left))
        }
        IconButton(onClick = { vm.rotate(90) }) {
            Icon(Icons.Filled.RotateRight, contentDescription = stringResource(R.string.rotate_right))
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { vm.save(scanMore = true) }, enabled = !state.processing) {
            Text(stringResource(R.string.review_save_more))
        }
        Button(onClick = { vm.save(scanMore = false) }, enabled = !state.processing) {
            Text(stringResource(R.string.action_done))
        }
    }
}

fun filterLabel(f: ScanFilter): Int = when (f) {
    ScanFilter.MAGIC -> R.string.filter_magic
    ScanFilter.ORIGINAL -> R.string.filter_original
    ScanFilter.GRAYSCALE -> R.string.filter_gray
    ScanFilter.BLACK_WHITE -> R.string.filter_bw
    ScanFilter.LIGHTEN -> R.string.filter_lighten
}
