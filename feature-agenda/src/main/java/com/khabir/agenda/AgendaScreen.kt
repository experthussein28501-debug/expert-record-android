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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.khabir.app.presentation.components.KhabirTextField
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
            onSave = { text, strokes, images, manualAppointments ->
                viewModel.saveDay(date, text, strokes, images, manualAppointments)
            }
        )
    }
}

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة").forEach { day ->
            Text(
                day,
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
    val offset = (first.dayOfWeek.value + 1) % 7
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
    onSave: (String, List<AgendaStroke>, List<String>, List<AgendaManualAppointment>) -> Unit
) {
    val context = LocalContext.current
    val noteVersion = summary.note?.updatedAt
    var text by remember(summary.date, noteVersion) { mutableStateOf(summary.note?.text.orEmpty()) }
    val strokes = remember(summary.date, noteVersion) { mutableStateListOf<AgendaStroke>().apply { addAll(summary.note?.strokes.orEmpty()) } }
    val images = remember(summary.date, noteVersion) { mutableStateListOf<String>().apply { addAll(summary.note?.imagePaths.orEmpty()) } }
    val manualAppointments = remember(summary.date, noteVersion) {
        mutableStateListOf<AgendaManualAppointment>().apply { addAll(summary.note?.manualAppointments.orEmpty()) }
    }
    var manualTitle by remember(summary.date) { mutableStateOf("") }
    var manualTime by remember(summary.date) { mutableStateOf("") }
    var manualLocation by remember(summary.date) { mutableStateOf("") }
    var manualDetails by remember(summary.date) { mutableStateOf("") }
    var currentStroke by remember(summary.date) { mutableStateOf<List<AgendaPoint>>(emptyList()) }
    var selectedSketchTool by remember(summary.date) { mutableStateOf(AgendaSketchTool.FREEHAND) }
    var selectedSketchColor by remember(summary.date) { mutableStateOf(0xFF1B1B1B.toInt()) }
    var selectedSketchWidth by remember(summary.date) { mutableStateOf(4f) }

    fun pendingManualAppointmentOrNull(): AgendaManualAppointment? {
        val appointment = AgendaManualAppointment(
            title = manualTitle.trim(),
            time = manualTime.trim(),
            location = manualLocation.trim(),
            details = manualDetails.trim()
        )
        return appointment.takeIf {
            it.title.isNotBlank() || it.time.isNotBlank() || it.location.isNotBlank() || it.details.isNotBlank()
        }
    }

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
                    val importedEvents = summary.events.filter { it.source != AgendaEventSource.MANUAL }
                    if (importedEvents.isNotEmpty()) {
                        Text("المواعيد المستوردة", fontWeight = FontWeight.Bold)
                        importedEvents.forEach { event -> EventCard(event) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("إضافة موعد يدوي", fontWeight = FontWeight.Bold)
                            KhabirTextField(
                                value = manualTitle,
                                onValueChange = { manualTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("العنوان أو رقم الدعوى") }
                            )
                            KhabirTextField(
                                value = manualTime,
                                onValueChange = { manualTime = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("الساعة — مثال: 9 صباحًا") }
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = manualLocation == "المكتب",
                                    onClick = { manualLocation = "المكتب" },
                                    label = { Text("المكتب") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = manualLocation == "المحكمة",
                                    onClick = { manualLocation = "المحكمة" },
                                    label = { Text("المحكمة") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            KhabirTextField(
                                value = manualLocation,
                                onValueChange = { manualLocation = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("المكان") }
                            )
                            KhabirTextField(
                                value = manualDetails,
                                onValueChange = { manualDetails = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                label = { Text("تفاصيل الموعد — اختياري") }
                            )
                            Button(
                                onClick = {
                                    pendingManualAppointmentOrNull()?.let { appointment ->
                                        manualAppointments.add(appointment)
                                        manualTitle = ""
                                        manualTime = ""
                                        manualLocation = ""
                                        manualDetails = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("إضافة الموعد لليوم") }
                        }
                    }

                    if (manualAppointments.isNotEmpty()) {
                        Text("المواعيد اليدوية", fontWeight = FontWeight.Bold)
                        manualAppointments.forEachIndexed { index, appointment ->
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(appointment.title.ifBlank { "موعد يدوي" }, fontWeight = FontWeight.Bold)
                                        if (appointment.time.isNotBlank()) Text("الساعة: ${appointment.time}")
                                        if (appointment.location.isNotBlank()) Text("المكان: ${appointment.location}")
                                        if (appointment.details.isNotBlank()) Text(appointment.details, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { manualAppointments.removeAt(index) }) {
                                        Icon(Icons.Filled.Delete, "حذف الموعد")
                                    }
                                }
                            }
                        }
                    }

                    KhabirTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        label = { Text("ملاحظات اليوم — لوحة مفاتيح الهاتف") }
                    )

                    Text("لوحة الكتابة والرسم — إصبع / قلم / S Pen", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.FREEHAND, onClick = { selectedSketchTool = AgendaSketchTool.FREEHAND }, label = { Text("قلم") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.LINE, onClick = { selectedSketchTool = AgendaSketchTool.LINE }, label = { Text("خط") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.ARROW, onClick = { selectedSketchTool = AgendaSketchTool.ARROW }, label = { Text("سهم") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.ERASER, onClick = { selectedSketchTool = AgendaSketchTool.ERASER }, label = { Text("استيكة") }, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.RECTANGLE, onClick = { selectedSketchTool = AgendaSketchTool.RECTANGLE }, label = { Text("مربع") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.CIRCLE, onClick = { selectedSketchTool = AgendaSketchTool.CIRCLE }, label = { Text("دائرة") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.TRIANGLE, onClick = { selectedSketchTool = AgendaSketchTool.TRIANGLE }, label = { Text("مثلث") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = selectedSketchTool == AgendaSketchTool.SEMICIRCLE, onClick = { selectedSketchTool = AgendaSketchTool.SEMICIRCLE }, label = { Text("نصف دائرة") }, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            0xFF1B1B1B.toInt() to "أسود",
                            0xFFC62828.toInt() to "أحمر",
                            0xFF1565C0.toInt() to "أزرق",
                            0xFF2E7D32.toInt() to "أخضر"
                        ).forEach { (argb, label) ->
                            FilterChip(
                                selected = selectedSketchColor == argb,
                                onClick = { selectedSketchColor = argb },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(3f to "رفيع", 5f to "متوسط", 8f to "عريض").forEach { (width, label) ->
                            FilterChip(
                                selected = selectedSketchWidth == width,
                                onClick = { selectedSketchWidth = width },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    DrawingBoard(
                        strokes = strokes,
                        currentStroke = currentStroke,
                        selectedTool = selectedSketchTool,
                        selectedColorArgb = selectedSketchColor,
                        selectedWidth = selectedSketchWidth,
                        onCurrentStrokeChange = { currentStroke = it },
                        onErase = { point ->
                            val index = strokes.indexOfLast { agendaStrokeHit(it, point) }
                            if (index >= 0) strokes.removeAt(index)
                        },
                        onStrokeFinished = {
                            if (selectedSketchTool != AgendaSketchTool.ERASER && currentStroke.size > 1) {
                                strokes.add(AgendaStroke(currentStroke, selectedSketchTool, selectedSketchColor, selectedSketchWidth))
                            }
                            currentStroke = emptyList()
                        }
                    )
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

                    Text("أدوات الملاحظة", fontWeight = FontWeight.Bold)
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

                }
                Button(
                    onClick = {
                        val appointmentsToSave = manualAppointments.toList() + listOfNotNull(pendingManualAppointmentOrNull())
                        onSave(text, strokes.toList(), images.toList(), appointmentsToSave)
                    },
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
                    AgendaEventSource.MANUAL -> "موعد يدوي"
                    AgendaEventSource.HOLIDAY -> "إجازة رسمية"
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
    selectedTool: AgendaSketchTool,
    selectedColorArgb: Int,
    selectedWidth: Float,
    onCurrentStrokeChange: (List<AgendaPoint>) -> Unit,
    onErase: (AgendaPoint) -> Unit,
    onStrokeFinished: () -> Unit
) {
    val latestCurrentStroke by rememberUpdatedState(currentStroke)
    val latestOnCurrentStrokeChange by rememberUpdatedState(onCurrentStrokeChange)
    val latestOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val latestOnErase by rememberUpdatedState(onErase)
    val latestTool by rememberUpdatedState(selectedTool)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(230.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val point = AgendaPoint(offset.x, offset.y)
                        if (latestTool == AgendaSketchTool.ERASER) {
                            latestOnErase(point)
                            latestOnCurrentStrokeChange(emptyList())
                        } else latestOnCurrentStrokeChange(listOf(point))
                    },
                    onDrag = { change, _ ->
                        val point = AgendaPoint(change.position.x, change.position.y)
                        if (latestTool == AgendaSketchTool.ERASER) {
                            latestOnErase(point)
                            latestOnCurrentStrokeChange(emptyList())
                        } else {
                            latestOnCurrentStrokeChange(
                                if (latestTool == AgendaSketchTool.FREEHAND) latestCurrentStroke + point
                                else listOf(latestCurrentStroke.firstOrNull() ?: point, point)
                            )
                        }
                    },
                    onDragEnd = { latestOnStrokeFinished() },
                    onDragCancel = { latestOnStrokeFinished() }
                )
            }
    ) {
        strokes.forEach { drawAgendaStroke(it) }
        if (currentStroke.isNotEmpty() && selectedTool != AgendaSketchTool.ERASER) {
            drawAgendaStroke(AgendaStroke(currentStroke, selectedTool, selectedColorArgb, selectedWidth))
        }
    }
}

private fun DrawScope.drawAgendaStroke(stroke: AgendaStroke) {
    if (stroke.points.isEmpty() || stroke.tool == AgendaSketchTool.ERASER) return
    val color = Color(stroke.colorArgb)
    val width = stroke.width.coerceIn(2f, 16f)
    val points = stroke.points.map { Offset(it.x, it.y) }
    val start = points.first()
    val end = points.last()
    when (stroke.tool) {
        AgendaSketchTool.FREEHAND -> {
            if (points.size == 1) drawCircle(color, radius = width / 2f, center = start)
            else {
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width))
            }
        }
        AgendaSketchTool.LINE -> if (points.size >= 2) drawLine(color, start, end, strokeWidth = width)
        AgendaSketchTool.ARROW -> if (points.size >= 2) {
            drawLine(color, start, end, strokeWidth = width)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val head = (18f + width * 2f).coerceAtMost(40f)
            val spread = PI / 7.0
            val left = Offset(end.x - (head * cos(angle - spread)).toFloat(), end.y - (head * sin(angle - spread)).toFloat())
            val right = Offset(end.x - (head * cos(angle + spread)).toFloat(), end.y - (head * sin(angle + spread)).toFloat())
            drawLine(color, end, left, strokeWidth = width)
            drawLine(color, end, right, strokeWidth = width)
        }
        AgendaSketchTool.RECTANGLE -> if (points.size >= 2) {
            val left=minOf(start.x,end.x); val top=minOf(start.y,end.y)
            drawRect(color, Offset(left,top), Size(kotlin.math.abs(end.x-start.x), kotlin.math.abs(end.y-start.y)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width))
        }
        AgendaSketchTool.CIRCLE -> if (points.size >= 2) {
            val left=minOf(start.x,end.x); val top=minOf(start.y,end.y)
            drawOval(color, Offset(left,top), Size(kotlin.math.abs(end.x-start.x), kotlin.math.abs(end.y-start.y)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width))
        }
        AgendaSketchTool.TRIANGLE -> if (points.size >= 2) {
            val left=minOf(start.x,end.x); val right=maxOf(start.x,end.x); val top=minOf(start.y,end.y); val bottom=maxOf(start.y,end.y)
            val path = Path().apply { moveTo((left+right)/2f,top); lineTo(right,bottom); lineTo(left,bottom); close() }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width))
        }
        AgendaSketchTool.SEMICIRCLE -> if (points.size >= 2) {
            val left=minOf(start.x,end.x); val top=minOf(start.y,end.y)
            drawArc(color, 180f, 180f, false, Offset(left,top), Size(kotlin.math.abs(end.x-start.x), kotlin.math.abs(end.y-start.y)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width))
        }
        AgendaSketchTool.ERASER -> Unit
    }
}

private fun agendaStrokeHit(stroke: AgendaStroke, point: AgendaPoint): Boolean {
    if (stroke.points.isEmpty()) return false
    val threshold = 26f
    if (stroke.tool in setOf(AgendaSketchTool.FREEHAND, AgendaSketchTool.LINE, AgendaSketchTool.ARROW)) {
        return stroke.points.any {
            val dx = it.x - point.x
            val dy = it.y - point.y
            dx * dx + dy * dy <= threshold * threshold
        }
    }
    val a = stroke.points.first()
    val b = stroke.points.last()
    return point.x in (minOf(a.x,b.x)-threshold)..(maxOf(a.x,b.x)+threshold) &&
        point.y in (minOf(a.y,b.y)-threshold)..(maxOf(a.y,b.y)+threshold)
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
