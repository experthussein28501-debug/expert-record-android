package com.khabir.agenda

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    viewModel: AgendaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("الأجندة", fontWeight = FontWeight.Bold)
                        Text(arabicMonth(state.month), style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
                actions = {
                    IconButton(onClick = viewModel::previousMonth) { Icon(Icons.Filled.ChevronRight, "الشهر السابق") }
                    IconButton(onClick = viewModel::goToday) { Icon(Icons.Filled.Today, "اليوم") }
                    IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronLeft, "الشهر التالي") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WeekHeader()
            MonthGrid(
                month = state.month,
                days = state.days,
                onDayClick = viewModel::selectDate,
                modifier = Modifier.weight(1f)
            )
        }
    }

    state.selectedDate?.let { date ->
        val summary = state.days[date] ?: AgendaDaySummary(date)
        AgendaDayDialog(
            summary = summary,
            onDismiss = viewModel::closeDay,
            onSave = { text, strokes, images -> viewModel.saveDay(date, text, strokes, images) }
        )
    }
}

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة").forEach { day ->
            Text(
                day.take(3),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    days: Map<LocalDate, AgendaDaySummary>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value + 1) % 7 // السبت أول الأسبوع
    val slots = remember(month) {
        buildList<LocalDate?> {
            repeat(offset) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
            while (size % 7 != 0) add(null)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = true
    ) {
        items(slots) { date ->
            if (date == null) Spacer(Modifier.aspectRatio(.78f))
            else AgendaDayCell(days[date] ?: AgendaDaySummary(date), onDayClick)
        }
    }
}

@Composable
private fun AgendaDayCell(summary: AgendaDaySummary, onClick: (LocalDate) -> Unit) {
    val today = summary.date == LocalDate.now()
    val hasContent = summary.holiday != null || summary.events.isNotEmpty() || summary.note != null
    Card(
        modifier = Modifier.aspectRatio(.78f).clickable { onClick(summary.date) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                today -> MaterialTheme.colorScheme.primaryContainer
                hasContent -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.date.dayOfMonth.toString(), fontWeight = FontWeight.Bold)
            summary.holiday?.let {
                Text(it.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            summary.events.firstOrNull()?.let {
                Text(
                    listOf(it.time, it.title).filter(String::isNotBlank).joinToString(" "),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (summary.events.size > 1) Text("+${summary.events.size - 1}", style = MaterialTheme.typography.labelSmall)
            summary.note?.text?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AgendaDayDialog(
    summary: AgendaDaySummary,
    onDismiss: () -> Unit,
    onSave: (String, List<AgendaStroke>, List<String>) -> Unit
) {
    val context = LocalContext.current
    var text by remember(summary.date) { mutableStateOf(summary.note?.text.orEmpty()) }
    val strokes = remember(summary.date) { mutableStateListOf<AgendaStroke>().apply { addAll(summary.note?.strokes.orEmpty()) } }
    val images = remember(summary.date) { mutableStateListOf<String>().apply { addAll(summary.note?.imagePaths.orEmpty()) } }
    var currentStroke by remember(summary.date) { mutableStateOf<List<AgendaPoint>>(emptyList()) }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) text = listOf(text, spoken).filter(String::isNotBlank).joinToString("\n")
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { saveAgendaBitmap(context, it)?.let(images::add) }
    }
    var launchCameraAfterPermission by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && launchCameraAfterPermission) cameraLauncher.launch(null)
        launchCameraAfterPermission = false
    }
    val importImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take(10).mapNotNull { copyAgendaImage(context, it) }.forEach(images::add)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.90f),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(summary.date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        summary.holiday?.let { Text(it.name, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = onDismiss) { Text("إغلاق") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (summary.events.isNotEmpty()) {
                        Text("المواعيد المستوردة", fontWeight = FontWeight.Bold)
                        summary.events.forEach { event -> EventCard(event) }
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        label = { Text("ملاحظات اليوم — تدعم لوحة مفاتيح الهاتف وS Pen للكتابة") }
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "إملاء ملاحظات الأجندة")
                                })
                            },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Filled.Mic, null); Text(" صوت") }
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(null)
                                } else {
                                    launchCameraAfterPermission = true
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Filled.CameraAlt, null); Text(" كاميرا") }
                        OutlinedButton(
                            onClick = { importImages.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Filled.PhotoLibrary, null); Text(" صور") }
                    }

                    Text("لوحة الكتابة والرسم — إصبع / قلم / S Pen", fontWeight = FontWeight.Bold)
                    DrawingBoard(strokes, currentStroke, onCurrentStrokeChange = { currentStroke = it }, onStrokeFinished = {
                        if (currentStroke.size > 1) strokes.add(AgendaStroke(currentStroke))
                        currentStroke = emptyList()
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                            Icon(Icons.Filled.Undo, null); Text(" تراجع")
                        }
                        OutlinedButton(onClick = { strokes.clear(); currentStroke = emptyList() }) {
                            Icon(Icons.Filled.Delete, null); Text(" مسح الرسم")
                        }
                    }

                    if (images.isNotEmpty()) {
                        Text("مرفقات اليوم (${images.size})", fontWeight = FontWeight.Bold)
                        images.forEachIndexed { index, path ->
                            val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(72.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("صورة ${index + 1}", modifier = Modifier.weight(1f))
                                IconButton(onClick = { images.remove(path) }) { Icon(Icons.Filled.Delete, "حذف") }
                            }
                        }
                    }
                }
                Button(
                    onClick = { onSave(text, strokes.toList(), images.toList()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.Save, null)
                    Text(" حفظ اليوم")
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: AgendaEvent) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(event.title, fontWeight = FontWeight.Bold)
            if (event.time.isNotBlank()) Text("الساعة: ${event.time}")
            if (event.location.isNotBlank()) Text("المكان: ${event.location}")
            if (event.details.isNotBlank()) Text(event.details, style = MaterialTheme.typography.bodySmall)
            Text(
                when (event.source) {
                    AgendaEventSource.NOTIFICATION_APPOINTMENT -> "مستورد من مواعيد الإخطارات"
                    AgendaEventSource.WORK_MINUTES -> "مستورد من محاضر الأعمال"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DrawingBoard(
    strokes: List<AgendaStroke>,
    currentStroke: List<AgendaPoint>,
    onCurrentStrokeChange: (List<AgendaPoint>) -> Unit,
    onStrokeFinished: () -> Unit
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.fillMaxWidth().height(230.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onCurrentStrokeChange(listOf(AgendaPoint(offset.x, offset.y))) },
                    onDrag = { change, _ ->
                        onCurrentStrokeChange(currentStroke + AgendaPoint(change.position.x, change.position.y))
                    },
                    onDragEnd = onStrokeFinished,
                    onDragCancel = onStrokeFinished
                )
            }
    ) {
        (strokes.map { it.points } + listOf(currentStroke)).forEach { points ->
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
            } else if (points.size == 1) {
                drawCircle(color, radius = 2f, center = Offset(points[0].x, points[0].y))
            }
        }
    }
}

private fun saveAgendaBitmap(context: Context, bitmap: Bitmap): String? = runCatching {
    val dir = File(context.filesDir, "agenda-media").apply { mkdirs() }
    val file = File(dir, "camera-${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    file.absolutePath
}.getOrNull()

private fun copyAgendaImage(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, "agenda-media").apply { mkdirs() }
    val file = File(dir, "import-${System.currentTimeMillis()}-${uri.hashCode()}.img")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input)
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file.absolutePath
}.getOrNull()

private fun arabicMonth(month: YearMonth): String {
    val names = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    return "${names[month.monthValue - 1]} ${month.year}"
}

private fun formatDate(date: LocalDate): String = "${date.dayOfMonth}/${date.monthValue}/${date.year}"
