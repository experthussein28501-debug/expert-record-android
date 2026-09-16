package com.khabir.app.presentation.reports

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private data class SketchStroke(val points: List<Offset>, val color: Color, val width: Float)

/** Editor for a simple, reviewable location sketch. Coordinates are normalised so they render at export size. */
@Composable
fun SiteSketchEditor(
    baseImage: Bitmap?,
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(Color(0xFF111111)) }
    var selectedWidth by remember { mutableStateOf(0.005f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showBaseImage by remember(baseImage) { mutableStateOf(baseImage != null) }
    val base = remember(baseImage) { baseImage?.asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مخطط الموقع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ارسم فوق لقطة الخريطة أو على صفحة بيضاء. الرسم يدوي للمراجعة وليس قياسًا مساحيًا.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Color(0xFF111111) to "أسود", Color(0xFFC62828) to "أحمر", Color(0xFF1565C0) to "أزرق").forEach { (color, label) ->
                        FilterChip(selected = selectedColor == color, onClick = { selectedColor = color }, label = { Text(label) })
                    }
                }
                if (baseImage != null) {
                    FilterChip(
                        selected = showBaseImage,
                        onClick = { showBaseImage = !showBaseImage },
                        label = { Text(if (showBaseImage) "خلفية الخريطة ظاهرة" else "الخريطة مخفية — الرسم فقط") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedWidth == 0.003f, onClick = { selectedWidth = 0.003f }, label = { Text("رفيع") })
                    FilterChip(selected = selectedWidth == 0.005f, onClick = { selectedWidth = 0.005f }, label = { Text("متوسط") })
                    FilterChip(selected = selectedWidth == 0.009f, onClick = { selectedWidth = 0.009f }, label = { Text("عريض") })
                    OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("تراجع") }
                }
                Box(
                    Modifier.fillMaxWidth().height(360.dp).background(Color.White).clipToBounds().onSizeChanged { canvasSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        Modifier.fillMaxWidth().height(360.dp).pointerInput(canvasSize, selectedColor, selectedWidth) {
                            fun pointAt(position: Offset): Offset = Offset(
                                (position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            )
                            detectDragGestures(
                                onDragStart = { currentPoints = listOf(pointAt(it)) },
                                onDrag = { change, _ ->
                                    currentPoints = currentPoints + pointAt(change.position)
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) strokes += SketchStroke(currentPoints, selectedColor, selectedWidth)
                                    currentPoints = emptyList()
                                },
                                onDragCancel = { currentPoints = emptyList() }
                            )
                        }
                    ) {
                        if (showBaseImage) base?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                        (strokes + SketchStroke(currentPoints, selectedColor, selectedWidth)).forEach { stroke ->
                            if (stroke.points.isEmpty()) return@forEach
                            val path = Path().apply {
                                moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                                stroke.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                            }
                            drawPath(path, stroke.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke.width * size.width))
                        }
                    }
                }
                OutlinedButton(onClick = { strokes.clear(); currentPoints = emptyList() }, modifier = Modifier.align(Alignment.End)) { Text("مسح الرسم") }
            }
        },
        confirmButton = { Button(onClick = { onSave(renderSketch(baseImage.takeIf { showBaseImage }, strokes)) }) { Text("حفظ المخطط") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun renderSketch(baseImage: Bitmap?, strokes: List<SketchStroke>): Bitmap {
    val width = 1600
    val height = 1200
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    canvas.drawColor(android.graphics.Color.WHITE)
    baseImage?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), null) }
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            strokeWidth = (stroke.width * width).coerceAtLeast(2f)
        }
        val path = android.graphics.Path().apply {
            moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
            stroke.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
        }
        canvas.drawPath(path, paint)
    }
    return result
}
