package com.scanku.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scanku.app.AppContainer
import com.scanku.app.R
import com.scanku.app.ScanApp
import com.scanku.app.imaging.BitmapIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ScanApp).container

/** Creates a ViewModel scoped to the current nav destination, with constructor arguments. */
@Composable
inline fun <reified VM : ViewModel> scopedViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer<VM> { create(container) } })
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Locks the screen orientation while this composable is on screen (used by the camera). */
@Composable
fun LockScreenOrientation(orientation: Int) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(orientation, activity) {
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose {
            if (original != null) activity?.requestedOrientation = original
        }
    }
}

fun Context.toast(@StringRes res: Int) {
    Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}

/** Memory cache for thumbnails (1/8 of the app heap). */
private object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(file: File, maxDim: Int): Bitmap? {
        val key = "${file.path}:${file.lastModified()}:$maxDim"
        cache.get(key)?.let { return it }
        return BitmapIo.decodeSampled(file, maxDim)?.also { cache.put(key, it) }
    }
}

/** Asynchronously decoded, downsampled image from app storage. */
@Composable
fun FileImage(
    file: File?,
    maxDim: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val image by produceState<ImageBitmap?>(null, file?.path, maxDim) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            ThumbnailCache.load(file, maxDim)?.asImageBitmap()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Returns a launcher that opens the system photo picker (no storage permission needed),
 * copies + validates the chosen image into the capture store, and reports its capture name.
 */
@Composable
fun rememberImageImporter(onImported: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onImported)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = container.captures.importFrom(uri)
                if (name != null) callback(name) else context.toast(R.string.import_failed)
            }
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
