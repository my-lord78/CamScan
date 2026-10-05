package com.scanku.app.ui.camera

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanku.app.R
import com.scanku.app.core.geometry.Quad
import com.scanku.app.ui.common.LockScreenOrientation
import com.scanku.app.ui.common.appContainer
import com.scanku.app.ui.common.rememberImageImporter
import com.scanku.app.ui.common.toast
import com.scanku.app.ui.theme.ScanTheme
import java.util.concurrent.Executors

private const val TAG = "CameraScreen"

/** Camera preview and detection run at 4:3 so overlay coordinates map 1:1 onto the preview box. */
private const val PREVIEW_ASPECT = 3f / 4f

@Composable
fun CameraScreen(
    documentId: Long?,
    onCaptured: (captureName: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val importImage = rememberImageImporter(onCaptured)

    Box(
        Modifier
            .fillMaxSize()
            .background(ScanTheme.tokens.cameraBackground),
    ) {
        if (hasPermission) {
            CameraContent(documentId = documentId, onCaptured = onCaptured, onImport = importImage, onClose = onClose)
        } else {
            PermissionNeeded(
                showRetry = askedOnce,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onImport = importImage,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun CameraContent(
    documentId: Long?,
    onCaptured: (String) -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = appContainer()
    val vm: CameraViewModel = viewModel()
    val tokens = ScanTheme.tokens

    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val quad by vm.quad.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val capturing by vm.capturing.collectAsStateWithLifecycle()
    val onCapturedState by rememberUpdatedState(onCaptured)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val aspect43 = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(aspect43)
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(settings.autoCapture) { vm.autoCapture = settings.autoCapture }
    LaunchedEffect(Unit) { vm.onResume() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder()
                    .setResolutionSelector(aspect43)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, vm.analyzer) }
                p.unbindAll()
                camera = p.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    imageCapture,
                )
                cameraError = false
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                cameraError = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            camera = null
            torchOn = false
        }
    }

    // Executes capture requests (auto or manual) coming from the ViewModel.
    LaunchedEffect(vm) {
        vm.captureRequests.collect {
            val file = container.captures.newFile()
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        vm.onCaptureFinished()
                        onCapturedState(file.name)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture failed", exception)
                        file.delete()
                        vm.onCaptureFinished()
                        context.toast(R.string.capture_failed)
                    }
                },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = tokens.onCamera)
            }
            Text(
                stringResource(if (documentId == null) R.string.camera_title_new else R.string.camera_title_add),
                color = tokens.onCamera,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = settings.autoCapture,
                onClick = { container.settings.update { it.copy(autoCapture = !it.autoCapture) } },
                label = { Text(stringResource(R.string.camera_auto)) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = tokens.onCamera,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            IconButton(
                enabled = hasFlash,
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = stringResource(if (torchOn) R.string.torch_off else R.string.torch_on),
                    tint = if (hasFlash) tokens.onCamera else tokens.onCamera.copy(alpha = 0.38f),
                )
            }
        }

        // Preview + detection overlay share one 3:4 box.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_ASPECT),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            DetectionOverlay(quad = quad, progress = progress, modifier = Modifier.fillMaxSize())

            val hint = when {
                cameraError -> R.string.camera_error
                capturing -> R.string.camera_hint_capturing
                quad == null -> R.string.camera_hint_searching
                settings.autoCapture -> R.string.camera_hint_hold
                else -> R.string.camera_hint_found
            }
            Surface(
                color = tokens.cameraScrim,
                contentColor = tokens.onCamera,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom controls
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onImport, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = stringResource(R.string.action_import),
                    tint = tokens.onCamera,
                    modifier = Modifier.size(30.dp),
                )
            }
            ShutterButton(
                progress = progress,
                enabled = !capturing && !cameraError,
                onClick = vm::requestCapture,
            )
            Spacer(Modifier.size(56.dp))
        }
    }
}

/** Draws the detected outline; colour shifts from "searching" to "locked" as the hold timer fills. */
@Composable
private fun DetectionOverlay(quad: Quad?, progress: Float, modifier: Modifier = Modifier) {
    val tokens = ScanTheme.tokens
    val color by animateColorAsState(
        targetValue = if (progress > 0.05f) tokens.detectionLocked else tokens.detectionSearching,
        label = "detectionColor",
    )
    Canvas(modifier) {
        val q = quad ?: return@Canvas
        val pts = q.points.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, color = color.copy(alpha = 0.22f))
        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()))
        pts.forEach { drawCircle(color = color, radius = 6.dp.toPx(), center = it) }
    }
}

@Composable
private fun ShutterButton(progress: Float, enabled: Boolean, onClick: () -> Unit) {
    val tokens = ScanTheme.tokens
    val label = stringResource(R.string.action_capture)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawCircle(color = tokens.onCamera.copy(alpha = 0.35f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    color = tokens.detectionLocked,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                )
            }
        }
        Box(
            Modifier
                .size(62.dp)
                .background(
                    if (enabled) tokens.onCamera else tokens.onCamera.copy(alpha = 0.5f),
                    CircleShape,
                )
                .border(2.dp, tokens.cameraBackground, CircleShape),
        )
    }
}

@Composable
private fun PermissionNeeded(
    showRetry: Boolean,
    onRequest: () -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = ScanTheme.tokens
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.camera_permission_title),
            color = tokens.onCamera,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (showRetry) R.string.camera_permission_denied else R.string.camera_permission_body),
            color = tokens.onCamera.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_grant)) }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onImport) {
            Text(stringResource(R.string.action_import), color = tokens.onCamera)
        }
        androidx.compose.material3.TextButton(onClick = onClose) {
            Text(stringResource(R.string.action_close), color = tokens.onCamera)
        }
    }
}
