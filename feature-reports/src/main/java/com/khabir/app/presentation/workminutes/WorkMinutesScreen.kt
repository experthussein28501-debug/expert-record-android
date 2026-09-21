package com.khabir.app.presentation.workminutes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FormatListNumbered
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
import com.khabir.app.presentation.common.ExplicitDialogProperties
import com.khabir.app.presentation.common.ExplicitDialogTitle
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkMinutesScreen(onBack: () -> Unit, viewModel: WorkMinutesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    androidx.activity.compose.BackHandler { viewModel.saveAndClose(onBack) }
    if (state.isLeaving || state.isSaving || state.isExporting) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("جارٍ الحفظ والتجهيز…", Modifier.padding(start = 12.dp))
                }
            }
        }
    }
    if (state.caseUpdates.isNotEmpty()) com.khabir.app.presentation.components.CaseUpdatesDialog(
        state.caseUpdates, viewModel::dismissCaseUpdates, viewModel::applyCaseUpdates)
    if (state.canRetryPages) AlertDialog(onDismissRequest = {}, title = { Text("الصفحات لم تكتمل") },
        text = { Text(state.pageRetryMessage.ifBlank { "لم يتم اعتماد نص ناقص. يمكنك إعادة القراءة أو إلغاء المجموعة وإعادة التصوير." }) },
        confirmButton = { TextButton(onClick = viewModel::retryPages) { Text("إعادة المحاولة") } },
        dismissButton = { TextButton(onClick = viewModel::cancelPageRetry) { Text("إلغاء المجموعة") } })
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
    var editingEntryNumber by remember { mutableStateOf<Int?>(null) }

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
                actions = { if (!state.isIndependent) TextButton(enabled = !state.isLoading, onClick = viewModel::reviewCaseUpdates) { Text("تحديثات القضية") } }, navigationIcon = { IconButton(onClick = { viewModel.saveAndClose(onBack) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
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

            val orderedEntries = state.entries.sortedBy { it.number }
            if (orderedEntries.isNotEmpty()) {
                val pagerState = rememberPagerState(
                    initialPage = state.expandedEntryNumber?.let { number ->
                        orderedEntries.indexOfFirst { it.number == number }.coerceAtLeast(0)
                    } ?: 0,
                    pageCount = { orderedEntries.size }
                )
                var lastKnownCount by remember { mutableIntStateOf(orderedEntries.size) }

                LaunchedEffect(orderedEntries.size) {
                    if (orderedEntries.size > lastKnownCount) {
                        pagerState.animateScrollToPage(orderedEntries.lastIndex)
                    } else if (pagerState.currentPage > orderedEntries.lastIndex) {
                        pagerState.scrollToPage(orderedEntries.lastIndex.coerceAtLeast(0))
                    }
                    lastKnownCount = orderedEntries.size
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("محضر ${pagerState.currentPage + 1} من ${orderedEntries.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("اسحب يمينًا أو يسارًا", style = MaterialTheme.typography.labelSmall)
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(screenHeightDp * 0.78f),
                    pageSpacing = 12.dp
                ) { page ->
                    val entry = orderedEntries[page]
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp).verticalScroll(rememberScrollState())) {
                        WorkMinutesEntryCard(
                            entry = entry,
                            isExpanded = true,
                            expandedHeight = screenHeightDp * 0.72f,
                            onExpand = { viewModel.onEntryExpand(entry.number) },
                            onOpenEditor = { editingEntryNumber = entry.number },
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
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("السابق") }
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < orderedEntries.lastIndex,
                        modifier = Modifier.weight(1f)
                    ) { Text("التالي") }
                }
            }

            Spacer(Modifier.height(8.dp))
            KhabirPrimaryButton(text = "حفظ المحاضر", onClick = viewModel::onSave, enabled = !state.isLoading && !state.isSaving)
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

    editingEntryNumber?.let { number ->
        state.entries.firstOrNull { it.number == number }?.let { entry ->
            AlertDialog(
                onDismissRequest = { editingEntryNumber = null },
                title = { Text("تحرير محضر أعمال رقم (${entry.number})") },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = screenHeightDp * 0.55f, max = screenHeightDp * 0.78f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("المحرر الكبير — اكتب وراجع متن المحضر كاملًا في صفحة واحدة.", style = MaterialTheme.typography.bodySmall)
                        KhabirTextField(
                            value = entry.bodyText,
                            onValueChange = { value -> viewModel.onEntryChanged(entry.number) { it.copy(bodyText = value) } },
                            label = { Text("نص المحضر") },
                            placeholder = { Text("اكتب مباشرة المأمورية، حضور الخصوم، المناقشة، الأقوال، وما تم من إجراءات...") },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            minLines = 12,
                            maxLines = Int.MAX_VALUE
                        )
                    }
                },
                confirmButton = { Button(onClick = { editingEntryNumber = null }) { Text("تم") } },
                dismissButton = { TextButton(onClick = { editingEntryNumber = null }) { Text("إغلاق") } }
            )
        }
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
    onOpenEditor: () -> Unit,
    onChange: ((WorkMinutesEntry) -> WorkMinutesEntry) -> Unit,
    onRemove: () -> Unit,
    onGoogleVoice: () -> Unit,
    onAiVoice: () -> Unit,
    onCamera: () -> Unit,
    onImportImages: () -> Unit
) {
    var showOpeningDatePicker by remember { mutableStateOf(false) }
    var showFollowUpDatePicker by remember { mutableStateOf(false) }
    var listMenuExpanded by remember(entry.number) { mutableStateOf(false) }
    var activeListStyle by remember(entry.number) { mutableStateOf<WorkMinutesListStyle?>(null) }
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
            Text("بيانات فتح المحضر", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("متن المحضر", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onOpenEditor) {
                    Icon(Icons.Filled.Edit, contentDescription = "فتح محرر كبير")
                    Spacer(Modifier.width(4.dp))
                    Text("تعديل في صفحة كبيرة")
                }
            }
            Text("نصوص جاهزة", style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkMinutesPhrases.quickPhrases.forEach { (label, phrase) ->
                    AssistChip(
                        onClick = {
                            onChange { e ->
                                val merged = listOf(e.bodyText.trim(), phrase.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
                                e.copy(bodyText = merged)
                            }
                            onOpenEditor()
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (entry.number >= 2) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ترقيم البنود", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Box {
                        FilledTonalButton(onClick = { listMenuExpanded = true }) {
                            Icon(Icons.Filled.FormatListNumbered, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(activeListStyle?.label ?: "اختيار قائمة")
                        }
                        DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                            WorkMinutesListStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.label) },
                                    onClick = {
                                        activeListStyle = style.takeUnless { it == WorkMinutesListStyle.PLAIN }
                                        listMenuExpanded = false
                                        if (style != WorkMinutesListStyle.PLAIN) {
                                            onChange { e -> e.copy(bodyText = ensureListStarted(e.bodyText, style)) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            KhabirTextField(
                value = entry.bodyText,
                onValueChange = { value ->
                    val adjusted = activeListStyle?.let { style -> continueListOnEnter(entry.bodyText, value, style) } ?: value
                    onChange { e -> e.copy(bodyText = adjusted) }
                },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onExpand() }.let { if (isExpanded) it.weight(1f) else it },
                label = { Text("نص المحضر") },
                placeholder = { Text("لإثبات ...") },
                minLines = 8,
                maxLines = if (isExpanded) Int.MAX_VALUE else 10
            )
            Text("إدخال خاص بهذا المحضر فقط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = onGoogleVoice, label = { Text("صوت Google") }, leadingIcon = { Icon(Icons.Filled.Mic, null) })
                AssistChip(onClick = onAiVoice, label = { Text("الصوت بالـAI") }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) })
                AssistChip(onClick = onCamera, label = { Text("كاميرا 1–10") }, leadingIcon = { Icon(Icons.Filled.CameraAlt, null) })
                AssistChip(onClick = onImportImages, label = { Text("استيراد صور") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) })
            }
            HorizontalDivider()
            Text("القفل والتوقيع", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(
                    value = entry.closingTime,
                    onValueChange = { onChange { e -> e.copy(closingTime = it) } },
                    label = { Text("ساعة القفل") },
                    modifier = Modifier.weight(1f)
                )
                KhabirTextField(
                    value = entry.expertName,
                    onValueChange = { onChange { e -> e.copy(expertName = it) } },
                    label = { Text("الخبير") },
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("الموعد القادم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Text("موعد الجلسة/المباشرة القادمة يُستخدم تلقائيًا كتاريخ فتح المحضر التالي.", style = MaterialTheme.typography.labelSmall)
            KhabirTextField(
                value = entry.scheduledFollowUpDate?.format(dateFormat).orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("تاريخ الموعد القادم — اختياري") },
                trailingIcon = { IconButton(onClick = { showFollowUpDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار التاريخ") } },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KhabirTextField(
                    value = entry.scheduledFollowUpTime,
                    onValueChange = { value -> onChange { e -> e.copy(scheduledFollowUpTime = value) } },
                    label = { Text("وقت الموعد القادم") },
                    placeholder = { Text("مثال: ٩ صباحًا") },
                    modifier = Modifier.weight(1f)
                )
                KhabirTextField(
                    value = entry.scheduledFollowUpLocation,
                    onValueChange = { value -> onChange { e -> e.copy(scheduledFollowUpLocation = value) } },
                    label = { Text("مكان الموعد") },
                    placeholder = { Text("المكتب / المعاينة") },
                    modifier = Modifier.weight(1f)
                )
            }
            if (entry.scheduledFollowUpDate != null) {
                Text(
                    "عند إضافة «محضر جديد» سيبدأ تلقائيًا بتاريخ ووقت هذا الموعد وبنموذج حضور ومناقشة الخصوم، ويمكن تعديله بالكامل.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private enum class WorkMinutesListStyle(val label: String) {
    WESTERN("1، 2، 3"),
    ARABIC_INDIC("١، ٢، ٣"),
    ARABIC_LETTERS("أ، ب، ج"),
    BULLET("• نقطة"),
    DASH("– شرطة"),
    X_MARK("X"),
    PLAIN("نص عادي")
}

private fun ensureListStarted(text: String, style: WorkMinutesListStyle): String {
    val trimmed = text.trimEnd()
    if (trimmed.isBlank()) return listMarker(style, 1)
    val lastLine = trimmed.lineSequence().lastOrNull().orEmpty()
    return if (looksLikeListLine(lastLine, style)) text else trimmed + "\n" + listMarker(style, 1)
}

private fun continueListOnEnter(oldValue: String, newValue: String, style: WorkMinutesListStyle): String {
    if (newValue.length != oldValue.length + 1 || !newValue.endsWith("\n")) return newValue
    val completed = oldValue.lineSequence().count { looksLikeListLine(it, style) }.coerceAtLeast(1)
    return newValue + listMarker(style, completed + 1)
}

private fun looksLikeListLine(line: String, style: WorkMinutesListStyle): Boolean {
    val value = line.trimStart()
    return when (style) {
        WorkMinutesListStyle.WESTERN -> Regex("""\d+[.)-]?\s+.*""").matches(value)
        WorkMinutesListStyle.ARABIC_INDIC -> Regex("""[٠-٩]+[.)-]?\s+.*""").matches(value)
        WorkMinutesListStyle.ARABIC_LETTERS -> Regex("""[أبجدهوزحطيكلمنسعفصقرشتثخذضظغ][.)-]?\s+.*""").matches(value)
        WorkMinutesListStyle.BULLET -> value.startsWith("• ")
        WorkMinutesListStyle.DASH -> value.startsWith("– ")
        WorkMinutesListStyle.X_MARK -> value.startsWith("X ")
        WorkMinutesListStyle.PLAIN -> false
    }
}

private fun listMarker(style: WorkMinutesListStyle, index: Int): String = when (style) {
    WorkMinutesListStyle.WESTERN -> "$index. "
    WorkMinutesListStyle.ARABIC_INDIC -> arabicIndic(index) + ". "
    WorkMinutesListStyle.ARABIC_LETTERS -> arabicListLetter(index) + ". "
    WorkMinutesListStyle.BULLET -> "• "
    WorkMinutesListStyle.DASH -> "– "
    WorkMinutesListStyle.X_MARK -> "X "
    WorkMinutesListStyle.PLAIN -> ""
}

private fun arabicIndic(index: Int): String = index.toString().map { ch ->
    "٠١٢٣٤٥٦٧٨٩"[ch.digitToInt()]
}.joinToString("")

private fun arabicListLetter(index: Int): String {
    val letters = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي", "ك", "ل", "م", "ن", "س", "ع", "ف", "ص", "ق", "ر", "ش", "ت", "ث", "خ", "ذ", "ض", "ظ", "غ")
    return letters[(index - 1).mod(letters.size)]
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
