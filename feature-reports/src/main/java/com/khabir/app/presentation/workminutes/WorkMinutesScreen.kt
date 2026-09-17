package com.khabir.app.presentation.workminutes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.domain.model.WorkMinutesEntry
import com.khabir.app.domain.model.WorkMinutesPhrases
import com.khabir.app.presentation.common.InAppCameraCapture
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirPrimaryButton
import com.khabir.app.presentation.components.KhabirTextField
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val WORD_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkMinutesScreen(onBack: () -> Unit, viewModel: WorkMinutesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var cameraEntryNumber by remember { mutableStateOf<Int?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var voiceEntryNumber by remember { mutableStateOf<Int?>(null) }
    var voiceUseAi by remember { mutableStateOf(false) }
    var pendingVoiceLaunch by remember { mutableStateOf(false) }
    var importEntryNumber by remember { mutableStateOf<Int?>(null) }

    val smartTemplateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onSelectWordTemplate(it)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        val target = voiceEntryNumber
        if (spoken.isNotBlank() && target != null) {
            if (voiceUseAi) viewModel.refineVoiceTranscript(spoken) { refined -> viewModel.appendToEntry(target, refined) }
            else viewModel.appendToEntry(target, spoken)
        }
    }

    fun launchArabicVoice() {
        voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالعربية لإضافة النص إلى محضر الأعمال")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
        })
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingVoiceLaunch) {
            pendingVoiceLaunch = false
            launchArabicVoice()
        } else if (!granted) {
            pendingVoiceLaunch = false
            scope.launch { snackbar.showSnackbar("يلزم السماح بالميكروفون للإدخال الصوتي") }
        }
    }

    fun startVoice(entryNumber: Int, ai: Boolean) {
        voiceEntryNumber = entryNumber
        voiceUseAi = ai
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchArabicVoice()
        } else {
            pendingVoiceLaunch = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = cameraEntryNumber != null
        else scope.launch { snackbar.showSnackbar("يلزم السماح بالكاميرا لتصوير محضر الأعمال") }
    }

    fun startCamera(entryNumber: Int) {
        cameraEntryNumber = entryNumber
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera = true
        } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val importImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = importEntryNumber
        val files = uris.take(10).mapNotNull { copyWorkMinutesImageToCache(context, it) }
        if (target != null && files.isNotEmpty()) {
            viewModel.onDocumentPagesCaptured(target, files, true)
        } else if (uris.isNotEmpty()) {
            scope.launch { snackbar.showSnackbar("تعذر استيراد الصور المختارة") }
        }
    }

    LaunchedEffect(state.exportedFileUri) {
        val uri = state.exportedFileUri ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, WORD_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        viewModel.onExportConsumed()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { scope.launch { snackbar.showSnackbar(it) }; viewModel.onErrorMessageConsumed() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("محاضر الأعمال", fontWeight = FontWeight.Bold)
                        val subtitle = if (state.caseNo.isNotBlank()) "الدعوى ${state.caseNo} لسنة ${state.caseYear}" else "دعوى جديدة"
                        Text(subtitle, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = viewModel::onAddEntry, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("محضر جديد") })
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.isIndependent) {
                KhabirCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بيانات الدعوى — دعوى غير مسجلة", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KhabirTextField(state.caseNo, viewModel::onCaseNoChanged, label = { Text("رقم الدعوى") }, modifier = Modifier.weight(1f), singleLine = true)
                            KhabirTextField(state.caseYear, viewModel::onCaseYearChanged, label = { Text("السنة") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        KhabirTextField(state.court, viewModel::onCourtChanged, label = { Text("المحكمة") }, singleLine = true)
                        KhabirTextField(state.plaintiffsSummary, viewModel::onPlaintiffsChanged, label = { Text("المرفوعة من") }, singleLine = true)
                        KhabirTextField(state.defendantsSummary, viewModel::onDefendantsChanged, label = { Text("ضد") }, singleLine = true)
                    }
                }
            } else {
                KhabirCard {
                    Text("الدعوى ${state.caseNo} لسنة ${state.caseYear}", fontWeight = FontWeight.Bold)
                    Text(state.court, style = MaterialTheme.typography.bodySmall)
                    if (state.plaintiffsSummary.isNotBlank()) Text("المرفوعة من: ${state.plaintiffsSummary}", style = MaterialTheme.typography.bodySmall)
                    if (state.defendantsSummary.isNotBlank()) Text("ضد: ${state.defendantsSummary}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("عدد النسخ المطلوبة", modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.onCopiesCountChanged(state.copiesCount - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(state.copiesCount.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.onCopiesCountChanged(state.copiesCount + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }

            if (state.isCaptureProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("جارٍ تحليل صور محضر الأعمال...", style = MaterialTheme.typography.bodySmall)
            }

            if (state.entries.isEmpty()) {
                KhabirCard {
                    Text("لا توجد محاضر أعمال بعد. اضغط «محضر جديد» لإضافة أول محضر.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.entries.sortedBy { it.number }.forEach { entry ->
                WorkMinutesEntryCard(
                    entry = entry,
                    isExpanded = state.expandedEntryNumber == entry.number,
                    expandedHeight = screenHeightDp * 0.8f,
                    onExpand = { viewModel.onEntryExpand(entry.number) },
                    onChange = { transform -> viewModel.onEntryChanged(entry.number, transform) },
                    onRemove = { viewModel.onRemoveEntry(entry.number) },
                    onGoogleVoice = { startVoice(entry.number, false) },
                    onAiVoice = { startVoice(entry.number, true) },
                    onCamera = { startCamera(entry.number) },
                    onImportImages = {
                        importEntryNumber = entry.number
                        importImagesLauncher.launch(arrayOf("image/*"))
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            KhabirPrimaryButton(
                text = if (state.isExporting) "جارٍ إنشاء Word..." else "تصدير محاضر الأعمال إلى Word",
                onClick = viewModel::onExport,
                enabled = !state.isExporting && state.entries.isNotEmpty()
            )
            Box {
                OutlinedButton(
                    onClick = { fileMenuExpanded = true },
                    enabled = !state.isExporting && state.entries.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("استخدام قالب Word خارجي") }
                DropdownMenu(expanded = fileMenuExpanded, onDismissRequest = { fileMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("تعبئة قالب Word بنفس التنسيق") },
                        onClick = { fileMenuExpanded = false; smartTemplateLauncher.launch(arrayOf(WORD_MIME)) }
                    )
                    if (state.savedWordTemplateUri.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("استخدام قالبي الشخصي لمحاضر الأعمال") },
                            onClick = { fileMenuExpanded = false; viewModel.onUseSavedWordTemplate() }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف القالب الشخصي") },
                            onClick = { fileMenuExpanded = false; viewModel.onForgetWordTemplate() }
                        )
                    }
                }
            }
            if (state.autoSaveStatus.isNotBlank()) {
                Text(state.autoSaveStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCamera && cameraEntryNumber != null) {
        val target = cameraEntryNumber!!
        InAppCameraCapture(
            onDismiss = { showCamera = false },
            onCaptured = {},
            onGeminiCaptured = {},
            onPagesCaptured = { pages, useAi ->
                showCamera = false
                viewModel.onDocumentPagesCaptured(target, pages, useAi)
            },
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } }
        )
    }

    if (state.captureReviewText.isNotBlank() && state.captureTargetEntryNumber != null) {
        AlertDialog(
            onDismissRequest = viewModel::onCaptureReviewDismissed,
            title = { Text("مراجعة النص المستخرج — محضر ${state.captureTargetEntryNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.captureReviewSource, style = MaterialTheme.typography.labelMedium)
                    KhabirTextField(
                        value = state.captureReviewText,
                        onValueChange = viewModel::onCaptureReviewChanged,
                        label = { Text("راجع النص قبل إضافته") },
                        minLines = 8,
                        maxLines = 16
                    )
                    Text("لن يُضاف النص للمحضر إلا بعد الضغط على «اعتماد وإضافة».", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = viewModel::onCaptureReviewAccepted) { Text("اعتماد وإضافة") } },
            dismissButton = { TextButton(onClick = viewModel::onCaptureReviewDismissed) { Text("إلغاء") } }
        )
    }

    state.pendingTemplateUri?.let {
        WorkMinutesTemplateMappingDialog(uriKey = it, state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkMinutesTemplateMappingDialog(uriKey: Any, state: WorkMinutesUiState, viewModel: WorkMinutesViewModel) {
    var mapping by remember(uriKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedField by remember(uriKey) { mutableStateOf("محاضر الأعمال") }
    val knownFields = listOf(
        "محاضر الأعمال", "رقم الدعوى", "السنة", "المحكمة", "المرفوعة من", "ضد", "الوارد", "اسم الخبير", "القطاع", "الإدارة"
    )
    AlertDialog(
        onDismissRequest = viewModel::onCancelTemplateMapping,
        title = { Text("تحويل نموذج Word إلى قالب محاضر أعمال") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("اختر اسم الحقل ثم اضغط الفقرة القديمة التي تريد استبدالها به. باقي النموذج يبقى كما هو.")
                KhabirTextField(selectedField, { selectedField = it }, label = { Text("اسم الحقل") })
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    knownFields.forEach { field -> TextButton(onClick = { selectedField = field }) { Text(field) } }
                }
                state.templateParagraphs.distinct().forEach { paragraph ->
                    OutlinedButton(onClick = {
                        mapping = if (mapping.containsKey(paragraph)) mapping - paragraph else mapping + (paragraph to selectedField.trim())
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text((mapping[paragraph]?.let { field -> "[$field] " } ?: "") + paragraph)
                    }
                }
            }
        },
        confirmButton = { Button(enabled = mapping.isNotEmpty(), onClick = { viewModel.onApplyTemplateMapping(mapping) }) { Text("اعتماد الربط وتعبئة نسخة") } },
        dismissButton = { TextButton(onClick = viewModel::onCancelTemplateMapping) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WorkMinutesEntryCard(
    entry: WorkMinutesEntry,
    isExpanded: Boolean,
    expandedHeight: Dp,
    onExpand: () -> Unit,
    onChange: ((WorkMinutesEntry) -> WorkMinutesEntry) -> Unit,
    onRemove: () -> Unit,
    onGoogleVoice: () -> Unit,
    onAiVoice: () -> Unit,
    onCamera: () -> Unit,
    onImportImages: () -> Unit
) {
    var showOpeningDatePicker by remember { mutableStateOf(false) }
    var showFollowUpDatePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d/M/yyyy") }

    if (showOpeningDatePicker) {
        WorkMinutesDatePicker(entry.openingDate, { showOpeningDatePicker = false }) { picked ->
            onChange { it.copy(openingDate = picked) }
            showOpeningDatePicker = false
        }
    }
    if (showFollowUpDatePicker) {
        WorkMinutesDatePicker(entry.scheduledFollowUpDate, { showFollowUpDatePicker = false }) { picked ->
            onChange { it.copy(scheduledFollowUpDate = picked) }
            showFollowUpDatePicker = false
        }
    }

    KhabirCard(containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Column(
            modifier = if (isExpanded) Modifier.fillMaxWidth().height(expandedHeight) else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("محضر أعمال رقم (${entry.number})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "حذف المحضر") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(
                    value = entry.openingDate?.format(dateFormat).orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("تاريخ الفتح") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = { IconButton(onClick = { showOpeningDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار التاريخ") } }
                )
                KhabirTextField(
                    value = entry.openingTime,
                    onValueChange = { onChange { e -> e.copy(openingTime = it) } },
                    label = { Text("ساعة الفتح") },
                    modifier = Modifier.weight(1f)
                )
            }
            Text("نصوص جاهزة", style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkMinutesPhrases.quickPhrases.forEach { (label, phrase) ->
                    AssistChip(onClick = { onChange { e -> e.copy(bodyText = phrase) } }, label = { Text(label) })
                }
            }
            KhabirTextField(
                value = entry.bodyText,
                onValueChange = { onChange { e -> e.copy(bodyText = it) } },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onExpand() }.let { if (isExpanded) it.weight(1f) else it },
                label = { Text("نص المحضر") },
                placeholder = { Text("لإثبات ...") },
                minLines = 3,
                maxLines = if (isExpanded) Int.MAX_VALUE else 6
            )
            Text("إدخال خاص بهذا المحضر فقط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = onGoogleVoice, label = { Text("صوت Google") }, leadingIcon = { Icon(Icons.Filled.Mic, null) })
                AssistChip(onClick = onAiVoice, label = { Text("الصوت بالـAI") }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) })
                AssistChip(onClick = onCamera, label = { Text("كاميرا 1–10") }, leadingIcon = { Icon(Icons.Filled.CameraAlt, null) })
                AssistChip(onClick = onImportImages, label = { Text("استيراد صور") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) })
            }
            KhabirTextField(
                value = entry.closingTime,
                onValueChange = { onChange { e -> e.copy(closingTime = it) } },
                label = { Text("ساعة القفل") }
            )
            KhabirTextField(
                value = entry.expertName,
                onValueChange = { onChange { e -> e.copy(expertName = it) } },
                label = { Text("الخبير") }
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("موعد الجلسة/المباشرة القادمة (يُستخدم تلقائيًا كتاريخ المحضر التالي)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
            KhabirTextField(
                value = entry.scheduledFollowUpDate?.format(dateFormat).orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("تاريخ الموعد القادم — اختياري") },
                trailingIcon = { IconButton(onClick = { showFollowUpDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار التاريخ") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkMinutesDatePicker(initialDate: LocalDate?, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val initialMillis = (initialDate ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis -> onSelected(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()) }
            }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) { DatePicker(picker) }
}

private fun copyWorkMinutesImageToCache(context: Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "work_minutes_import").apply { mkdirs() }
    val file = File.createTempFile("work_minutes_", ".jpg", directory)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "تعذر فتح الصورة" }
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file
}.getOrNull()
