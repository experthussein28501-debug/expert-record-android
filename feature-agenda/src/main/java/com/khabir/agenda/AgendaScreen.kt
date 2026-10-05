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
import com.khabir.app.presentation.components.InlineHelp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.khabir.app.data.ocr.ArabicPetitionOcrService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val context = LocalContext.current
    var exportDays by remember { mutableStateOf(emptyList<AgendaDaySummary>()) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportMenu by remember { mutableStateOf(false) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { runCatching { exportAgendaPdf(context, it, exportDays) }.onFailure { exportError = "تعذر تصدير PDF" } }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { runCatching { exportAgendaCsv(context, it, exportDays) }.onFailure { exportError = "تعذر تصدير الشيت" } }
    }
    exportError?.let { message -> AlertDialog(onDismissRequest = { exportError = null }, text = { Text(message) }, confirmButton = { TextButton(onClick = { exportError = null }) { Text("حسنًا") } }) }
    val saveError by viewModel.saveError.collectAsState()
    val saving by viewModel.isSaving.collectAsState()
    saveError?.let { message -> AlertDialog(onDismissRequest = { viewModel.saveError.value = null }, text = { Text(message) }, confirmButton = { TextButton(onClick = { viewModel.saveError.value = null }) { Text("حسنًا") } }) }
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
                    Box {
                        TextButton(onClick = { exportMenu = true }) { Text("تصدير") }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(text = { Text("الشهر PDF") }, onClick = {
                                exportMenu = false; exportDays = state.days.values.toList()
                                pdfLauncher.launch("agenda-${state.month}.pdf")
                            })
                            DropdownMenuItem(text = { Text("شيت الشهر CSV") }, onClick = {
                                exportMenu = false; exportDays = state.days.values.toList()
                                csvLauncher.launch("agenda-${state.month}.csv")
                            })
                        }
                    }
                    IconButton(onClick = viewModel::previousMonth) { Icon(Icons.Filled.ChevronRight, "الشهر السابق") }
                    IconButton(onClick = viewModel::goToday) { Icon(Icons.Filled.Today, "اليوم") }
                    IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronLeft, "الشهر التالي") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(state.month) {
                    var travel=0f
                    detectHorizontalDragGestures(
                        onDragStart={travel=0f},onDragCancel={travel=0f},
                        onDragEnd={when(agendaMonthSwipe(travel,64.dp.toPx())) {1->viewModel.nextMonth();-1->viewModel.previousMonth();else->Unit}},
                        onHorizontalDrag={change,amount->change.consume();travel+=amount}
                    )
                },
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
            saving = saving,
            draft = viewModel.draft,
            onDismiss = viewModel::closeDay,
            onExportPdf = { currentDay -> exportDays = listOf(currentDay); pdfLauncher.launch("agenda-${date}.pdf") },
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).fillMaxHeight())
                    else Box(Modifier.weight(1f).fillMaxHeight()) {
                        AgendaDayCell(days[date] ?: AgendaDaySummary(date), onDayClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaDayCell(summary: AgendaDaySummary, onClick: (LocalDate) -> Unit) {
    val today = summary.date == LocalDate.now()
    val hasContent = summary.holiday != null || summary.events.isNotEmpty() || summary.note != null
    Card(
        modifier = Modifier.fillMaxSize().clickable { onClick(summary.date) },
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
                if (it.time.isNotBlank()) Text("الساعة ${it.time}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    it.title,
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
internal fun AgendaDayDialog(
    summary: AgendaDaySummary,
    onDismiss: () -> Unit,
    onSave: (String, List<AgendaStroke>, List<String>, List<AgendaManualAppointment>) -> Unit,
    saving: Boolean = false,
    draft: AgendaDraft? = null,
    onExportPdf: ((AgendaDaySummary) -> Unit)? = null
) {
    val context = LocalContext.current
    val retained = draft ?: remember(summary.date) { AgendaDraft(summary.note) }
    var text by retained.text
    val strokes = retained.strokes
    val images = retained.images
    val manualAppointments = retained.appointments
    val hiddenImportedKeys = retained.hiddenImportedKeys
    var notesReview by remember {mutableStateOf<AgendaNoteReview?>(null)}
    var manualTitle by retained.manualTitle
    var manualTime by retained.manualTime
    var manualLocation by retained.manualLocation
    var manualDetails by retained.manualDetails
    var currentStroke by remember(summary.date) { mutableStateOf<List<AgendaPoint>>(emptyList()) }
    var selectedSketchTool by remember(summary.date) { mutableStateOf(AgendaSketchTool.FREEHAND) }
    var selectedSketchColor by remember(summary.date) { mutableStateOf(0xFF1B1B1B.toInt()) }
    var selectedSketchWidth by remember(summary.date) { mutableStateOf(3f) }
    var editingText by rememberSaveable(summary.date) { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var expandedDrawing by remember { mutableStateOf(false) }
    // Existing appointments are always visible above the notes. This flag
    // controls only the form for adding a new appointment.
    var showAppointments by remember(summary.date) { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var pendingImageReads by remember { mutableStateOf(0) }

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
    val readingImage = pendingImageReads > 0
    com.khabir.app.data.monetization.BlockWorkAds(true)
    val scope = rememberCoroutineScope()
    val ocr = remember { ArabicPetitionOcrService(context.applicationContext) }
    fun readImage(path: String) {
        pendingImageReads++
        scope.launch {
            try {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeAgendaImage(path, 2600) }
                if (bitmap == null) inputError = "تعذر فتح الصورة"
                else try {
                    when (val result = ocr.recognize(bitmap)) {
                        is ArabicPetitionOcrService.Result.Success -> text = listOf(text, result.text).filter(String::isNotBlank).joinToString("\n")
                        is ArabicPetitionOcrService.Result.Failure -> inputError = result.message
                    }
                } finally { bitmap.recycle() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) { inputError = "تعذر قراءة الصورة؛ حاول بصورة أوضح"
            } finally { pendingImageReads-- }
        }
    }
    fun requestClose() {
        if (readingImage || saving) return
        if (editingText) editingText = false
        else if (text != summary.note?.text.orEmpty() || strokes.toList() != summary.note?.strokes.orEmpty() ||
            images.toList() != summary.note?.imagePaths.orEmpty() ||
            manualAppointments.toList() != summary.note?.manualAppointments.orEmpty() ||
            hiddenImportedKeys.toSet() != summary.note?.hiddenImportedKeys.orEmpty() ||
            pendingManualAppointmentOrNull() != null || retained.noteTransfers.isNotEmpty()) confirmClose = true
        else onDismiss()
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) text = listOf(text, spoken).filter(String::isNotBlank).joinToString("\n")
    }
    val cameraFile = remember(summary.date) {
        File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "agenda-${summary.date}.jpg") }
    }
    fun cameraUri() = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val path = copyAgendaImage(context, cameraUri())
            if (path != null) { images.add(path); if (editingText) readImage(path) }
            else inputError = "تعذر حفظ صورة الكاميرا"
        }
    }
    var launchCameraAfterPermission by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && launchCameraAfterPermission) runCatching { cameraLauncher.launch(cameraUri()) }.onFailure { inputError = "تعذر فتح الكاميرا" }
        if (!granted) inputError = "يلزم السماح بالكاميرا لالتقاط المستند"
        launchCameraAfterPermission = false
    }
    val importImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take(10).forEach { uri ->
            val path = copyAgendaImage(context, uri)
            if (path != null) { images.add(path); if (editingText) readImage(path) }
            else inputError = "تعذر استيراد إحدى الصور؛ الحد الأقصى للصورة 20 ميجابايت"
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
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
                    InlineHelp("ملاحظات اليوم", "اضغط على الورقة لفتح الكتابة بالكيبورد. استخدم القلم في المساحة السفلية، وافتح أدواته من أسفل. زر حفظ اليوم يحفظ النص والقلم والصور معًا.")
                    onExportPdf?.let { export -> TextButton(onClick = {
                        val appointments = manualAppointments.toList() + listOfNotNull(pendingManualAppointmentOrNull())
                        export(summary.copy(
                            events = (summary.events+summary.hiddenEvents).filter { it.source != AgendaEventSource.MANUAL && !it.isHidden(hiddenImportedKeys) } +
                                appointments.map { AgendaEvent(summary.date, it.title, it.time, it.location, it.details, AgendaEventSource.MANUAL) },
                            note = AgendaDayNote(summary.date, text, strokes.toList(), images.toList(), appointments, hiddenImportedKeys.toSet())
                        ))
                    }, enabled = !readingImage && !saving) { Text("PDF") } }
                }
                HorizontalDivider()
                BackHandler { requestClose() }
                if (editingText) {
                    RuledAgendaEditor(text, { text = it }, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
                } else Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val importedEvents = (summary.events+summary.hiddenEvents).filter { it.source != AgendaEventSource.MANUAL && !it.isHidden(hiddenImportedKeys) }
                    if (importedEvents.isNotEmpty()) {
                        Text("المواعيد المستوردة", fontWeight = FontWeight.Bold)
                        importedEvents.forEach { event ->
                            EventCard(event)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = {
                                    text = listOf(text, event.toAgendaNoteText()).filter(String::isNotBlank).joinToString("\n")
                                }, modifier = Modifier.weight(1f)) { Text("نسخ للملاحظات") }
                                TextButton(onClick = {
                                    pendingManualAppointmentOrNull()?.let { manualAppointments.add(it) }
                                    manualTitle = event.title; manualTime = event.time
                                    manualLocation = event.location; manualDetails = event.details
                                    hiddenImportedKeys.add(event.importKey())
                                    showAppointments = true
                                }, modifier = Modifier.weight(1f)) { Text("تعديل") }
                                TextButton(onClick = { hiddenImportedKeys.add(event.importKey()) }, modifier = Modifier.weight(1f)) { Text("حذف") }
                            }
                        }
                    }

                    val hiddenEvents=(summary.events+summary.hiddenEvents).filter {it.source!=AgendaEventSource.MANUAL && it.isHidden(hiddenImportedKeys)}
                    if(hiddenEvents.isNotEmpty()) {
                        Text("مواعيد مخفية — المصدر الأصلي محفوظ",fontWeight=FontWeight.Bold)
                        hiddenEvents.forEach {event ->
                            TextButton(onClick={hiddenImportedKeys.removeAll {it==event.importKey() || it==event.legacyImportKey()}}) {Text("إظهار: ${event.title} — ${event.time}")}
                        }
                    }
                    if(retained.noteTransfers.isNotEmpty()) {
                        Text("مواعيد لأيام أخرى تنتظر الحفظ")
                        retained.noteTransfers.toList().forEach { item ->
                            TextButton(onClick={retained.noteTransfers.remove(item)}) {Text("إلغاء نقل ${item.appointment.title} إلى ${item.date}")}
                        }
                    }

                    if (manualAppointments.isNotEmpty()) {
                        Text("المواعيد اليدوية", fontWeight = FontWeight.Bold)
                        manualAppointments.forEachIndexed { index, appointment ->
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                            Text(appointment.title.ifBlank { "موعد يدوي" },fontWeight=FontWeight.Bold)
                                            if(appointment.time.isNotBlank()) Text("الساعة: ${appointment.time}")
                                            if(appointment.location.isNotBlank()) Text("المكان: ${appointment.location}")
                                            if(appointment.details.isNotBlank()) Text(appointment.details,style=MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick={manualAppointments.removeAt(index)}) {Icon(Icons.Filled.Delete,"حذف الموعد")}
                                    }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick={
                                            val line="${summary.date} — ${appointment.title}" + appointment.time.takeIf(String::isNotBlank)?.let {" — الساعة: $it"}.orEmpty() + appointment.location.takeIf(String::isNotBlank)?.let {" — المكان: $it"}.orEmpty()
                                            text=listOf(text,line,appointment.details).filter(String::isNotBlank).joinToString("\n")
                                        }) {Text("للملاحظات")}
                                        TextButton(onClick={
                                            pendingManualAppointmentOrNull()?.let {manualAppointments.add(it)}
                                            manualTitle=appointment.title;manualTime=appointment.time;manualLocation=appointment.location;manualDetails=appointment.details
                                            manualAppointments.removeAt(index);showAppointments=true
                                        }) {Text("تعديل")}
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ملاحظات اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            RuledAgendaPreview(text, onOpen = { editingText = true; showTools = false })
                        }
                    }
                    Text("الكتابة بالقلم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { expandedDrawing = true }) { Text("تكبير مساحة الرسم") }
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
                        onStrokeFinished = { points ->
                            if (selectedSketchTool != AgendaSketchTool.ERASER && points.isNotEmpty()) {
                                strokes.add(AgendaStroke(points, selectedSketchTool, selectedSketchColor, selectedSketchWidth))
                            }
                            currentStroke = emptyList()
                        }
                    )
                    if (images.isNotEmpty()) {
                        Text("مرفقات اليوم (${images.size})", fontWeight = FontWeight.Bold)
                        images.forEachIndexed { index, path ->
                            val bitmap = remember(path) { decodeAgendaImage(path, 160) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(72.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("صورة ${index + 1}", modifier = Modifier.weight(1f))
                                IconButton(onClick = { images.remove(path) }) { Icon(Icons.Filled.Delete, "حذف") }
                            }
                        }
                    }

                    if (showAppointments) {
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


                    }
                }
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (readingImage) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (editingText) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching { voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "إملاء ملاحظات الأجندة")
                                }) }.onFailure { inputError = "خدمة الإملاء الصوتي غير متاحة على الجهاز" }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Filled.Mic, null); Text(" صوت") }
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    runCatching { cameraLauncher.launch(cameraUri()) }.onFailure { inputError = "تعذر فتح الكاميرا" }
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

                    TextButton(onClick = { editingText = false }, modifier = Modifier.fillMaxWidth()) { Text("تم — العودة لليوم") }
                } else {
                    if (showTools) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = selectedSketchTool == AgendaSketchTool.FREEHAND, onClick = { selectedSketchTool = AgendaSketchTool.FREEHAND }, label = { Text("قلم") })
                            FilterChip(selected = selectedSketchTool == AgendaSketchTool.ERASER, onClick = { selectedSketchTool = AgendaSketchTool.ERASER }, label = { Text("ممحاة") })
                            FilterChip(selected = selectedSketchWidth == 3f, onClick = { selectedSketchWidth = 3f }, label = { Text("رفيع") })
                            FilterChip(selected = selectedSketchWidth == 8f, onClick = { selectedSketchWidth = 8f }, label = { Text("عريض") })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(0xFF1B1B1B.toInt() to "أسود", 0xFF1565C0.toInt() to "أزرق", 0xFFC62828.toInt() to "أحمر").forEach { (argb, label) ->
                                FilterChip(selected = selectedSketchColor == argb, onClick = { selectedSketchColor = argb }, label = { Text(label) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) }) { Text("تراجع") }
                            TextButton(enabled = strokes.isNotEmpty(), onClick = { confirmClear = true }) { Text("مسح الكتابة") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { showTools = !showTools; showAppointments = false }) { Text(if (showTools) "إخفاء الأدوات" else "أدوات القلم") }
                        TextButton(onClick = { showAppointments = !showAppointments; showTools = false }) { Text(if (showAppointments) "إغلاق إضافة الموعد" else "إضافة موعد") }
                        TextButton(onClick = { requestClose() }) { Text("إغلاق") }
                    }

                }
                TextButton(enabled=!readingImage && !saving,onClick={notesReview=AgendaNoteAppointments.parse(text,summary.date)}) {Text("الملاحظات ← مواعيد الشيت")}
                Button(
                    enabled = !readingImage && !saving,
                    onClick = {
                        val appointmentsToSave = manualAppointments.toList() + listOfNotNull(pendingManualAppointmentOrNull())
                        onSave(text, strokes.toList(), images.toList(), appointmentsToSave)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("agenda-save")
                ) {
                    Icon(Icons.Filled.Save, null)
                    Text(if (saving) "جارٍ الحفظ…" else "حفظ اليوم", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    notesReview?.let {review -> AgendaNotesReviewDialog(review,{notesReview=null},{items ->
        val current=items.filter {it.date==summary.date}.map {it.appointment}
        val merged=AgendaNoteAppointments.merge(manualAppointments.toList(),current)
        manualAppointments.clear();manualAppointments.addAll(merged)
        items.filter {it.date!=summary.date}.forEach {if(it !in retained.noteTransfers) retained.noteTransfers.add(it)}
        notesReview=null
    }) }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("حفظ تغييرات اليوم؟") },
        text = { Text("توجد ملاحظات لم تُحفظ بعد.") },
        confirmButton = { TextButton(onClick = {
            val appointmentsToSave = manualAppointments.toList() + listOfNotNull(pendingManualAppointmentOrNull())
            onSave(text, strokes.toList(), images.toList(), appointmentsToSave)
        }) { Text("حفظ وإغلاق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("تجاهل التغييرات") } }
    )
    if (expandedDrawing) Dialog(onDismissRequest = { expandedDrawing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("ارسم على الشاشة كاملة؛ ستُغلق بعد رفع القلم", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expandedDrawing = false }) { Text("تصغير") }
                DrawingBoard(strokes, currentStroke, selectedSketchTool, selectedSketchColor, selectedSketchWidth,
                    { currentStroke = it },
                    { point -> strokes.indexOfLast { agendaStrokeHit(it, point) }.takeIf { it >= 0 }?.let(strokes::removeAt) },
                    { points ->
                        if (selectedSketchTool != AgendaSketchTool.ERASER && points.isNotEmpty())
                            strokes.add(AgendaStroke(points, selectedSketchTool, selectedSketchColor, selectedSketchWidth))
                        currentStroke = emptyList()
                        expandedDrawing = false
                    }, Modifier.weight(1f))
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("مسح الكتابة بالقلم؟") },
        confirmButton = { TextButton(onClick = { strokes.clear(); currentStroke = emptyList(); confirmClear = false }) { Text("مسح") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
    )

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
                    AgendaEventSource.CASE_HEARING -> "موعد جلسة من بيانات القضية"
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
    onStrokeFinished: (List<AgendaPoint>) -> Unit,
    modifier: Modifier = Modifier.height(300.dp)
) {
    val latestCurrentStroke by rememberUpdatedState(currentStroke)
    val latestOnCurrentStrokeChange by rememberUpdatedState(onCurrentStrokeChange)
    val latestOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val latestOnErase by rememberUpdatedState(onErase)
    val latestTool by rememberUpdatedState(selectedTool)
    Canvas(
        modifier = modifier.fillMaxWidth().testTag("agenda-writing-board").clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                fun normalized(offset: Offset) = AgendaPoint(offset.x, offset.y * (300.dp.toPx() / size.height.coerceAtLeast(1).toFloat()))
                detectDragGestures(
                    onDragStart = { offset ->
                        val point = normalized(offset)
                        if (latestTool == AgendaSketchTool.ERASER) {
                            latestOnErase(point)
                            latestOnCurrentStrokeChange(emptyList())
                        } else latestOnCurrentStrokeChange(listOf(point))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val point = normalized(Offset(change.position.x.coerceIn(0f, size.width.toFloat()), change.position.y.coerceIn(0f, size.height.toFloat())))
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
                    onDragEnd = { latestOnStrokeFinished(latestCurrentStroke) },
                    onDragCancel = { latestOnCurrentStrokeChange(emptyList()) }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val point = AgendaPoint(offset.x, offset.y * (300.dp.toPx() / size.height.coerceAtLeast(1).toFloat()))
                    if (latestTool == AgendaSketchTool.ERASER) latestOnErase(point)
                    else latestOnStrokeFinished(listOf(point))
                }
            }
    ) {
        drawRect(Color(0xFFFFFDF7))
        val spacing = 30.dp.toPx()
        var lineY = spacing
        while (lineY < size.height) {
            drawLine(Color(0xFFE1E4E8), Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1f)
            lineY += spacing
        }
        val boardScaleY = size.height / 300.dp.toPx()
        withTransform({ scale(1f, boardScaleY, pivot = Offset.Zero) }) {
            strokes.forEach { drawAgendaStroke(it) }
            if (currentStroke.isNotEmpty() && selectedTool != AgendaSketchTool.ERASER) {
                drawAgendaStroke(AgendaStroke(currentStroke, selectedTool, selectedColorArgb, selectedWidth))
            }
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
        try {
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 20L * 1024 * 1024) { "Image too large" }
                    output.write(buffer, 0, count)
                }
            }
        } catch (error: Exception) { file.delete(); throw error }
    }
    file.absolutePath
}.getOrNull()

private fun arabicMonth(month: YearMonth): String {
    val names = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    return "${names[month.monthValue - 1]} ${month.year}"
}

private fun formatDate(date: LocalDate): String = "${date.dayOfMonth}/${date.monthValue}/${date.year}"


private fun decodeAgendaImage(path: String, maxSide: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > maxSide) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(path, options)
}
