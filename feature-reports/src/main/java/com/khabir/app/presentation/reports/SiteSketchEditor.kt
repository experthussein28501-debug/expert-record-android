package com.khabir.app.presentation.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest

internal data class SketchStroke(val points: List<Offset>, val color: Color, val width: Float)

internal data class RestoredSketch(
    val baseImage: Bitmap?,
    val strokes: List<SketchStroke>,
    val showBaseImage: Boolean,
    val fingerprint: String
)

/** Sidecar codec for editable drawing strokes. */
internal object SketchDraftCodec {
    fun encode(showBaseImage: Boolean, strokes: List<SketchStroke>): String = buildString {
        appendLine("v=1")
        appendLine("base=${if (showBaseImage) 1 else 0}")
        strokes.forEach { stroke ->
            val points = stroke.points.joinToString(";") { p -> "${p.x.coerceIn(0f, 1f)},${p.y.coerceIn(0f, 1f)}" }
            appendLine("s=${stroke.color.toArgb()}|${stroke.width}|$points")
        }
    }

    fun decode(value: String): Pair<Boolean, List<SketchStroke>> {
        var showBase = true
        val strokes = mutableListOf<SketchStroke>()
        value.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("base=") -> showBase = line.substringAfter('=').trim() != "0"
                line.startsWith("s=") -> {
                    val parts = line.substringAfter("s=").split('|', limit = 3)
                    if (parts.size != 3) return@forEach
                    val color = parts[0].toIntOrNull() ?: return@forEach
                    val width = parts[1].toFloatOrNull()?.coerceIn(0.001f, 0.03f) ?: return@forEach
                    val points = parts[2].split(';').mapNotNull { token ->
                        val xy = token.split(',', limit = 2)
                        if (xy.size != 2) null else {
                            val x = xy[0].toFloatOrNull()
                            val y = xy[1].toFloatOrNull()
                            if (x == null || y == null) null else Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                        }
                    }
                    if (points.isNotEmpty()) strokes += SketchStroke(points, Color(color), width)
                }
            }
        }
        return showBase to strokes
    }
}

/**
 * Keeps the background image and strokes separate without changing the report database schema.
 * The rendered PNG is fingerprinted; that fingerprint points to a private sidecar draft.
 */
private object SketchDraftRegistry {
    private const val MAX_DRAFTS = 60

    fun restore(context: Context, rendered: Bitmap): RestoredSketch? = runCatching {
        val fingerprint = fingerprint(rendered)
        val dir = draftDir(context)
        val meta = File(dir, "$fingerprint.sketch")
        if (!meta.isFile) return@runCatching null
        val (showBase, strokes) = SketchDraftCodec.decode(meta.readText())
        val baseFile = File(dir, "$fingerprint.base.png")
        val base = baseFile.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        meta.setLastModified(System.currentTimeMillis())
        baseFile.takeIf(File::exists)?.setLastModified(System.currentTimeMillis())
        RestoredSketch(base, strokes, showBase, fingerprint)
    }.getOrNull()

    fun save(
        context: Context,
        rendered: Bitmap,
        baseImage: Bitmap?,
        strokes: List<SketchStroke>,
        showBaseImage: Boolean,
        previousFingerprint: String?
    ) {
        runCatching {
            val fingerprint = fingerprint(rendered)
            val dir = draftDir(context)
            File(dir, "$fingerprint.sketch").writeText(SketchDraftCodec.encode(showBaseImage, strokes))
            val baseFile = File(dir, "$fingerprint.base.png")
            if (baseImage != null) {
                baseFile.outputStream().use { output ->
                    check(baseImage.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر حفظ خلفية المخطط" }
                }
            } else baseFile.delete()
            if (!previousFingerprint.isNullOrBlank() && previousFingerprint != fingerprint) {
                File(dir, "$previousFingerprint.sketch").delete()
                File(dir, "$previousFingerprint.base.png").delete()
            }
            prune(dir)
        }
    }

    private fun draftDir(context: Context): File = File(context.filesDir, "report_sketch_drafts").apply { mkdirs() }

    private fun prune(dir: File) {
        val meta = dir.listFiles()?.filter { it.extension == "sketch" }?.sortedByDescending(File::lastModified).orEmpty()
        meta.drop(MAX_DRAFTS).forEach { file ->
            val key = file.nameWithoutExtension
            file.delete()
            File(dir, "$key.base.png").delete()
        }
    }

    private fun fingerprint(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun putInt(v: Int) {
            digest.update(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
        }
        putInt(bitmap.width); putInt(bitmap.height)
        val sx = 24; val sy = 24
        for (iy in 0 until sy) {
            val y = ((bitmap.height - 1L) * iy / (sy - 1)).toInt().coerceIn(0, bitmap.height - 1)
            for (ix in 0 until sx) {
                val x = ((bitmap.width - 1L) * ix / (sx - 1)).toInt().coerceIn(0, bitmap.width - 1)
                putInt(bitmap.getPixel(x, y))
            }
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Location sketch editor. New sketches preserve the map/photo background and the drawing
 * strokes as separate editable layers across app restarts.
 */
@Composable
fun SiteSketchEditor(
    baseImage: Bitmap?,
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val restored = remember(baseImage) { baseImage?.let { SketchDraftRegistry.restore(context, it) } }
    val editableBaseImage = remember(baseImage, restored) { restored?.baseImage ?: baseImage }
    val strokes = remember(baseImage, restored) { mutableStateListOf<SketchStroke>().apply { addAll(restored?.strokes.orEmpty()) } }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(Color(0xFF111111)) }
    var selectedWidth by remember { mutableStateOf(0.005f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showBaseImage by remember(baseImage, restored) { mutableStateOf(restored?.showBaseImage ?: (editableBaseImage != null)) }
    val base = remember(editableBaseImage) { editableBaseImage?.asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مخطط الموقع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (restored != null) "تم استرجاع طبقة الرسم السابقة؛ يمكنك تعديل الخطوط أو إخفاء خلفية الخريطة."
                    else "ارسم فوق لقطة الخريطة أو على صفحة بيضاء. الرسم يدوي للمراجعة وليس قياسًا مساحيًا.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Color(0xFF111111) to "أسود", Color(0xFFC62828) to "أحمر", Color(0xFF1565C0) to "أزرق").forEach { (color, label) ->
                        FilterChip(selected = selectedColor == color, onClick = { selectedColor = color }, label = { Text(label) })
                    }
                }
                if (editableBaseImage != null) {
                    FilterChip(selected = showBaseImage, onClick = { showBaseImage = !showBaseImage }, label = { Text(if (showBaseImage) "خلفية الخريطة ظاهرة" else "الخريطة مخفية — الرسم فقط") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedWidth == 0.003f, onClick = { selectedWidth = 0.003f }, label = { Text("رفيع") })
                    FilterChip(selected = selectedWidth == 0.005f, onClick = { selectedWidth = 0.005f }, label = { Text("متوسط") })
                    FilterChip(selected = selectedWidth == 0.009f, onClick = { selectedWidth = 0.009f }, label = { Text("عريض") })
                    OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("تراجع") }
                }
                Box(Modifier.fillMaxWidth().height(360.dp).background(Color.White).clipToBounds().onSizeChanged { canvasSize = it }, contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier.fillMaxWidth().height(360.dp).pointerInput(canvasSize, selectedColor, selectedWidth) {
                            fun pointAt(position: Offset): Offset = Offset(
                                (position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            )
                            detectDragGestures(
                                onDragStart = { currentPoints = listOf(pointAt(it)) },
                                onDrag = { change, _ -> currentPoints = currentPoints + pointAt(change.position) },
                                onDragEnd = { if (currentPoints.isNotEmpty()) strokes += SketchStroke(currentPoints, selectedColor, selectedWidth); currentPoints = emptyList() },
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
        confirmButton = {
            Button(onClick = {
                val rendered = renderSketch(editableBaseImage.takeIf { showBaseImage }, strokes)
                SketchDraftRegistry.save(context, rendered, editableBaseImage, strokes, showBaseImage, restored?.fingerprint)
                onSave(rendered)
            }) { Text("حفظ المخطط") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("إلغاء") } }
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
            color = stroke.color.toArgb(); style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND
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
