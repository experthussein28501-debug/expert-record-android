package com.khabir.app.presentation.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class SketchTool { FREEHAND, LINE, ARROW, RECTANGLE, CIRCLE, TRIANGLE, SEMICIRCLE, ERASER }

internal data class SketchStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val tool: SketchTool = SketchTool.FREEHAND
)

internal data class RestoredSketch(
    val baseImage: Bitmap?,
    val strokes: List<SketchStroke>,
    val showBaseImage: Boolean,
    val fingerprint: String
)

/** Sidecar codec for editable drawing strokes. Reads old v1 drafts and writes v2 tool-aware drafts. */
internal object SketchDraftCodec {
    fun encode(showBaseImage: Boolean, strokes: List<SketchStroke>): String = buildString {
        appendLine("v=2")
        appendLine("base=${if (showBaseImage) 1 else 0}")
        strokes.forEach { stroke ->
            val points = stroke.points.joinToString(";") { p -> "${p.x.coerceIn(0f, 1f)},${p.y.coerceIn(0f, 1f)}" }
            appendLine("s=${stroke.tool.name}|${stroke.color.toArgb()}|${stroke.width}|$points")
        }
    }

    fun decode(value: String): Pair<Boolean, List<SketchStroke>> {
        var showBase = true
        val strokes = mutableListOf<SketchStroke>()
        value.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("base=") -> showBase = line.substringAfter('=').trim() != "0"
                line.startsWith("s=") -> decodeStroke(line.substringAfter("s="))?.let(strokes::add)
            }
        }
        return showBase to strokes
    }

    private fun decodeStroke(value: String): SketchStroke? {
        val v2 = value.split('|', limit = 4)
        if (v2.size == 4) {
            val tool = runCatching { SketchTool.valueOf(v2[0]) }.getOrNull()
            val color = v2[1].toIntOrNull()
            val width = v2[2].toFloatOrNull()?.coerceIn(0.001f, 0.03f)
            val points = parsePoints(v2[3])
            if (tool != null && color != null && width != null && points.isNotEmpty()) {
                return SketchStroke(points, Color(color), width, tool)
            }
        }

        // Backward compatibility with v1: color|width|points
        val v1 = value.split('|', limit = 3)
        if (v1.size != 3) return null
        val color = v1[0].toIntOrNull() ?: return null
        val width = v1[1].toFloatOrNull()?.coerceIn(0.001f, 0.03f) ?: return null
        val points = parsePoints(v1[2])
        return points.takeIf { it.isNotEmpty() }?.let { SketchStroke(it, Color(color), width, SketchTool.FREEHAND) }
    }

    private fun parsePoints(value: String): List<Offset> = value.split(';').mapNotNull { token ->
        val xy = token.split(',', limit = 2)
        if (xy.size != 2) null else {
            val x = xy[0].toFloatOrNull()
            val y = xy[1].toFloatOrNull()
            if (x == null || y == null) null else Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }
}

/** Keeps the background image and editable strokes separate across app restarts. */
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
 * محرر الكروكي: رسم حر أو خط مستقيم أو سهم، مع حفظ كل عنصر كطبقة قابلة للتعديل.
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
    val strokes = remember(baseImage, restored) {
        mutableStateListOf<SketchStroke>().apply { addAll(restored?.strokes.orEmpty()) }
    }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedTool by remember { mutableStateOf(SketchTool.FREEHAND) }
    var selectedColor by remember { mutableStateOf(Color(0xFF111111)) }
    var selectedWidth by remember { mutableStateOf(0.005f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showBaseImage by remember(baseImage, restored) {
        mutableStateOf(restored?.showBaseImage ?: (editableBaseImage != null))
    }
    var showTools by remember { mutableStateOf(false) }
    val base = remember(editableBaseImage) { editableBaseImage?.asImageBitmap() }

    fun toolLabel(tool: SketchTool): String = when (tool) {
        SketchTool.FREEHAND -> "رسم حر"
        SketchTool.LINE -> "خط"
        SketchTool.ARROW -> "سهم"
        SketchTool.RECTANGLE -> "مربع/مستطيل"
        SketchTool.CIRCLE -> "دائرة"
        SketchTool.TRIANGLE -> "مثلث"
        SketchTool.SEMICIRCLE -> "نصف دائرة"
        SketchTool.ERASER -> "استيكة"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (restored == null) "رسم كروكي جديد" else "تعديل الرسم الكروكي",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        OutlinedButton(onClick = { showTools = true }) {
                            Text("أدوات الرسم")
                        }
                        DropdownMenu(
                            expanded = showTools,
                            onDismissRequest = { showTools = false }
                        ) {
                            SketchTool.entries.forEach { tool ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (selectedTool == tool) "✓ " else "") + toolLabel(tool)
                                        )
                                    },
                                    onClick = {
                                        selectedTool = tool
                                        showTools = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("اللون: أسود" + if (selectedColor == Color(0xFF111111)) " ✓" else "") },
                                onClick = { selectedColor = Color(0xFF111111); showTools = false }
                            )
                            DropdownMenuItem(
                                text = { Text("اللون: أحمر" + if (selectedColor == Color(0xFFC62828)) " ✓" else "") },
                                onClick = { selectedColor = Color(0xFFC62828); showTools = false }
                            )
                            DropdownMenuItem(
                                text = { Text("اللون: أزرق" + if (selectedColor == Color(0xFF1565C0)) " ✓" else "") },
                                onClick = { selectedColor = Color(0xFF1565C0); showTools = false }
                            )
                            HorizontalDivider()
                            listOf(
                                0.003f to "سمك رفيع",
                                0.005f to "سمك متوسط",
                                0.009f to "سمك عريض"
                            ).forEach { (width, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (selectedWidth == width) " ✓" else "") },
                                    onClick = { selectedWidth = width; showTools = false }
                                )
                            }
                            if (editableBaseImage != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (showBaseImage) "إخفاء خلفية الخريطة/الصورة" else "إظهار خلفية الخريطة/الصورة") },
                                    onClick = { showBaseImage = !showBaseImage; showTools = false }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("تراجع آخر عنصر") },
                                enabled = strokes.isNotEmpty(),
                                onClick = {
                                    if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                                    showTools = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مسح الرسم") },
                                enabled = strokes.isNotEmpty(),
                                onClick = {
                                    strokes.clear()
                                    currentPoints = emptyList()
                                    showTools = false
                                }
                            )
                        }
                    }
                }

                Text(
                    "الأداة الحالية: ${toolLabel(selectedTool)} — العناصر: ${strokes.size}",
                    style = MaterialTheme.typography.labelMedium
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(540.dp)
                        .background(Color.White)
                        .clipToBounds()
                        .onSizeChanged { canvasSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(540.dp)
                            .pointerInput(canvasSize, selectedColor, selectedWidth, selectedTool) {
                                fun pointAt(position: Offset): Offset = Offset(
                                    (position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    (position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                )
                                detectDragGestures(
                                    onDragStart = { position ->
                                        val point = pointAt(position)
                                        if (selectedTool == SketchTool.ERASER) {
                                            eraseSketchAt(strokes, point)
                                            currentPoints = emptyList()
                                        } else {
                                            currentPoints = listOf(point)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val point = pointAt(change.position)
                                        if (selectedTool == SketchTool.ERASER) {
                                            eraseSketchAt(strokes, point)
                                            currentPoints = emptyList()
                                        } else {
                                            currentPoints = when (selectedTool) {
                                                SketchTool.FREEHAND -> currentPoints + point
                                                SketchTool.LINE,
                                                SketchTool.ARROW,
                                                SketchTool.RECTANGLE,
                                                SketchTool.CIRCLE,
                                                SketchTool.TRIANGLE,
                                                SketchTool.SEMICIRCLE ->
                                                    listOf(currentPoints.firstOrNull() ?: point, point)
                                                SketchTool.ERASER -> emptyList()
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (selectedTool != SketchTool.ERASER && currentPoints.isNotEmpty()) {
                                            val points = if (selectedTool == SketchTool.FREEHAND) currentPoints else {
                                                if (currentPoints.size >= 2) {
                                                    listOf(currentPoints.first(), currentPoints.last())
                                                } else emptyList()
                                            }
                                            if (points.isNotEmpty()) {
                                                strokes += SketchStroke(points, selectedColor, selectedWidth, selectedTool)
                                            }
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = { currentPoints = emptyList() }
                                )
                            }
                    ) {
                        if (showBaseImage) {
                            base?.let {
                                drawImage(
                                    it,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                )
                            }
                        }
                        strokes.forEach(::drawSketchStroke)
                        if (currentPoints.isNotEmpty()) {
                            drawSketchStroke(
                                SketchStroke(
                                    currentPoints,
                                    selectedColor,
                                    selectedWidth,
                                    selectedTool
                                )
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            val rendered = renderSketch(
                                editableBaseImage.takeIf { showBaseImage },
                                strokes
                            )
                            SketchDraftRegistry.save(
                                context,
                                rendered,
                                editableBaseImage,
                                strokes,
                                showBaseImage,
                                restored?.fingerprint
                            )
                            onSave(rendered)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ المخطط")
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSketchStroke(stroke: SketchStroke) {
    if (stroke.points.isEmpty()) return
    val widthPx = (stroke.width * size.width).coerceAtLeast(2f)
    val pixelPoints = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
    when (stroke.tool) {
        SketchTool.FREEHAND -> {
            val path = Path().apply {
                moveTo(pixelPoints.first().x, pixelPoints.first().y)
                pixelPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, stroke.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = widthPx))
        }
        SketchTool.LINE -> if (pixelPoints.size >= 2) {
            drawLine(stroke.color, pixelPoints.first(), pixelPoints.last(), strokeWidth = widthPx)
        }
        SketchTool.ARROW -> if (pixelPoints.size >= 2) {
            val start = pixelPoints.first(); val end = pixelPoints.last()
            drawLine(stroke.color, start, end, strokeWidth = widthPx)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val head = (18f + widthPx * 2f).coerceAtMost(42f)
            val spread = PI / 7.0
            val left = Offset(
                end.x - (head * cos(angle - spread)).toFloat(),
                end.y - (head * sin(angle - spread)).toFloat()
            )
            val right = Offset(
                end.x - (head * cos(angle + spread)).toFloat(),
                end.y - (head * sin(angle + spread)).toFloat()
            )
            drawLine(stroke.color, end, left, strokeWidth = widthPx)
            drawLine(stroke.color, end, right, strokeWidth = widthPx)
        }
        SketchTool.RECTANGLE -> if (pixelPoints.size >= 2) {
            val a = pixelPoints.first(); val b = pixelPoints.last()
            val left = minOf(a.x, b.x); val top = minOf(a.y, b.y)
            drawRect(stroke.color, Offset(left, top), Size(kotlin.math.abs(b.x-a.x), kotlin.math.abs(b.y-a.y)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(widthPx))
        }
        SketchTool.CIRCLE -> if (pixelPoints.size >= 2) {
            val a = pixelPoints.first(); val b = pixelPoints.last()
            val left = minOf(a.x, b.x); val top = minOf(a.y, b.y)
            drawOval(stroke.color, Offset(left, top), Size(kotlin.math.abs(b.x-a.x), kotlin.math.abs(b.y-a.y)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(widthPx))
        }
        SketchTool.TRIANGLE -> if (pixelPoints.size >= 2) {
            val a = pixelPoints.first(); val b = pixelPoints.last()
            val left = minOf(a.x,b.x); val right = maxOf(a.x,b.x); val top = minOf(a.y,b.y); val bottom = maxOf(a.y,b.y)
            val path = Path().apply { moveTo((left+right)/2f, top); lineTo(right,bottom); lineTo(left,bottom); close() }
            drawPath(path, stroke.color, style = androidx.compose.ui.graphics.drawscope.Stroke(widthPx))
        }
        SketchTool.SEMICIRCLE -> if (pixelPoints.size >= 2) {
            val a = pixelPoints.first(); val b = pixelPoints.last()
            val left = minOf(a.x, b.x); val top = minOf(a.y, b.y)
            drawArc(stroke.color, 180f, 180f, false, Offset(left, top), Size(kotlin.math.abs(b.x-a.x), kotlin.math.abs(b.y-a.y)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(widthPx))
        }
        SketchTool.ERASER -> Unit
    }
}

private fun renderSketch(baseImage: Bitmap?, strokes: List<SketchStroke>): Bitmap {
    val width = 1600
    val height = 1200
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    canvas.drawColor(android.graphics.Color.WHITE)
    baseImage?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), null) }
    strokes.forEach { drawAndroidStroke(canvas, it, width, height) }
    return result
}

private fun drawAndroidStroke(canvas: AndroidCanvas, stroke: SketchStroke, width: Int, height: Int) {
    if (stroke.points.isEmpty()) return
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = stroke.color.toArgb()
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = (stroke.width * width).coerceAtLeast(2f)
    }
    fun px(point: Offset) = android.graphics.PointF(point.x * width, point.y * height)
    val start = px(stroke.points.first())
    val end = px(stroke.points.last())
    when (stroke.tool) {
        SketchTool.FREEHAND -> {
            val path = android.graphics.Path().apply {
                moveTo(start.x, start.y)
                stroke.points.drop(1).forEach { point ->
                    val p = px(point); lineTo(p.x, p.y)
                }
            }
            canvas.drawPath(path, paint)
        }
        SketchTool.LINE -> if (stroke.points.size >= 2) canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        SketchTool.ARROW -> if (stroke.points.size >= 2) {
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val head = (28f + paint.strokeWidth * 2f).coerceAtMost(70f)
            val spread = PI / 7.0
            val leftX = end.x - (head * cos(angle - spread)).toFloat()
            val leftY = end.y - (head * sin(angle - spread)).toFloat()
            val rightX = end.x - (head * cos(angle + spread)).toFloat()
            val rightY = end.y - (head * sin(angle + spread)).toFloat()
            canvas.drawLine(end.x, end.y, leftX, leftY, paint)
            canvas.drawLine(end.x, end.y, rightX, rightY, paint)
        }
        SketchTool.RECTANGLE -> if (stroke.points.size >= 2) {
            canvas.drawRect(minOf(start.x,end.x), minOf(start.y,end.y), maxOf(start.x,end.x), maxOf(start.y,end.y), paint)
        }
        SketchTool.CIRCLE -> if (stroke.points.size >= 2) {
            canvas.drawOval(RectF(minOf(start.x,end.x), minOf(start.y,end.y), maxOf(start.x,end.x), maxOf(start.y,end.y)), paint)
        }
        SketchTool.TRIANGLE -> if (stroke.points.size >= 2) {
            val left=minOf(start.x,end.x); val right=maxOf(start.x,end.x); val top=minOf(start.y,end.y); val bottom=maxOf(start.y,end.y)
            val path = android.graphics.Path().apply { moveTo((left+right)/2f,top); lineTo(right,bottom); lineTo(left,bottom); close() }
            canvas.drawPath(path, paint)
        }
        SketchTool.SEMICIRCLE -> if (stroke.points.size >= 2) {
            canvas.drawArc(RectF(minOf(start.x,end.x), minOf(start.y,end.y), maxOf(start.x,end.x), maxOf(start.y,end.y)), 180f, 180f, false, paint)
        }
        SketchTool.ERASER -> Unit
    }
}


private fun eraseSketchAt(strokes: MutableList<SketchStroke>, point: Offset) {
    val threshold = 0.045f
    val index = strokes.indexOfLast { stroke ->
        if (stroke.points.isEmpty()) false
        else if (stroke.tool == SketchTool.FREEHAND || stroke.tool == SketchTool.LINE || stroke.tool == SketchTool.ARROW) {
            stroke.points.any { p ->
                val dx = p.x - point.x
                val dy = p.y - point.y
                dx * dx + dy * dy <= threshold * threshold
            }
        } else {
            val a = stroke.points.first()
            val b = stroke.points.last()
            point.x in (minOf(a.x,b.x)-threshold)..(maxOf(a.x,b.x)+threshold) &&
                point.y in (minOf(a.y,b.y)-threshold)..(maxOf(a.y,b.y)+threshold)
        }
    }
    if (index >= 0) strokes.removeAt(index)
}
