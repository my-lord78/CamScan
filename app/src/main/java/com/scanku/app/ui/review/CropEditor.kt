package com.scanku.app.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.scanku.app.core.geometry.FitRect
import com.scanku.app.core.geometry.Pt
import com.scanku.app.core.geometry.Quad
import com.scanku.app.core.geometry.fitCenter
import com.scanku.app.ui.theme.ScanTheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows [image] fitted in the available space with a draggable 4-corner outline.
 * While a corner is dragged, a magnifier lens shows the area under the finger at 2.5×
 * so the corner can be placed precisely (the finger hides exactly that spot).
 */
@Composable
fun CropEditor(
    image: ImageBitmap,
    quad: Quad,
    onCornerChange: (index: Int, point: Pt) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tokens = ScanTheme.tokens
    val touchRadiusPx = with(LocalDensity.current) { 40.dp.toPx() }
    val currentQuad by rememberUpdatedState(quad)
    val onChange by rememberUpdatedState(onCornerChange)
    var activeCorner by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier) {
        val fit = fitCenter(
            image.width.toFloat(), image.height.toFloat(),
            constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(fit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val corners = currentQuad.points.map { toScreen(it, fit) }
                            val nearest = corners.indices.minByOrNull { (corners[it] - start).getDistance() }
                            activeCorner = if (nearest != null && (corners[nearest] - start).getDistance() <= touchRadiusPx) nearest else -1
                            if (activeCorner >= 0) dragPos = corners[activeCorner]
                        },
                        onDrag = { change, amount ->
                            if (activeCorner >= 0) {
                                change.consume()
                                dragPos += amount
                                onChange(activeCorner, toNormalized(dragPos, fit))
                            }
                        },
                        onDragEnd = { activeCorner = -1 },
                        onDragCancel = { activeCorner = -1 },
                    )
                },
        ) {
            if (fit.width <= 0f) return@Canvas
            drawImage(
                image = image,
                dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )

            val corners = quad.points.map { toScreen(it, fit) }
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                for (i in 1 until 4) lineTo(corners[i].x, corners[i].y)
                close()
            }
            // Dim everything outside the outline.
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height))
                addPath(outline)
            }
            drawPath(scrim, tokens.cropScrim)
            drawPath(outline, primary, style = Stroke(width = 2.5.dp.toPx()))

            corners.forEachIndexed { i, c ->
                val r = if (i == activeCorner) 16.dp.toPx() else 12.dp.toPx()
                drawCircle(tokens.handleFill.copy(alpha = 0.9f), radius = r, center = c)
                drawCircle(primary, radius = r, center = c, style = Stroke(3.dp.toPx()))
            }

            if (activeCorner >= 0) {
                drawMagnifier(image, fit, corners[activeCorner], primary, tokens.handleFill)
            }
        }
    }
}

private fun toScreen(p: Pt, fit: FitRect) = Offset(fit.left + p.x * fit.width, fit.top + p.y * fit.height)

private fun toNormalized(o: Offset, fit: FitRect) = Pt(
    ((o.x - fit.left) / fit.width).coerceIn(0f, 1f),
    ((o.y - fit.top) / fit.height).coerceIn(0f, 1f),
)

private fun DrawScope.drawMagnifier(
    image: ImageBitmap,
    fit: FitRect,
    corner: Offset,
    accent: Color,
    ring: Color,
) {
    val lensR = 56.dp.toPx()
    val margin = 16.dp.toPx()
    val zoom = 2.5f
    // Put the lens on the opposite side from the finger.
    val center = if (corner.x < size.width / 2) {
        Offset(size.width - lensR - margin, lensR + margin)
    } else {
        Offset(lensR + margin, lensR + margin)
    }

    val pxPerScreen = image.width / fit.width
    val imgX = (corner.x - fit.left) * pxPerScreen
    val imgY = (corner.y - fit.top) * pxPerScreen
    val srcSide = min(2 * lensR / zoom * pxPerScreen, min(image.width, image.height).toFloat()).roundToInt().coerceAtLeast(1)
    // Clamp the source window inside the image (drawImage rejects out-of-bounds rects).
    val srcLeft = (imgX - srcSide / 2f).roundToInt().coerceIn(0, image.width - srcSide)
    val srcTop = (imgY - srcSide / 2f).roundToInt().coerceIn(0, image.height - srcSide)
    val dstLeft = center.x - lensR
    val dstTop = center.y - lensR
    val dstScale = 2 * lensR / srcSide

    val lens = Path().apply { addOval(Rect(center, lensR)) }
    clipPath(lens) {
        drawRect(Color.Black, topLeft = Offset(dstLeft, dstTop), size = androidx.compose.ui.geometry.Size(2 * lensR, 2 * lensR))
        drawImage(
            image = image,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcSide, srcSide),
            dstOffset = IntOffset(dstLeft.roundToInt(), dstTop.roundToInt()),
            dstSize = IntSize((2 * lensR).roundToInt(), (2 * lensR).roundToInt()),
        )
        // Crosshair at the exact corner position.
        val cx = dstLeft + (imgX - srcLeft) * dstScale
        val cy = dstTop + (imgY - srcTop) * dstScale
        val arm = 10.dp.toPx()
        drawLine(accent, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 2.dp.toPx())
        drawLine(accent, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 2.dp.toPx())
    }
    drawCircle(ring, radius = lensR, center = center, style = Stroke(3.dp.toPx()))
}
