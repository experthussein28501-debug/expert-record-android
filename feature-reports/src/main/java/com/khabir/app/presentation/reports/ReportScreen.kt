package com.khabir.app.presentation.reports

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.presentation.common.InAppCameraCapture
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirPrimaryButton
import com.khabir.app.presentation.components.KhabirTextField
import com.khabir.app.presentation.components.ArabicListHangingIndentTransformation
import com.khabir.app.domain.model.ReportSectionDefinition
import com.khabir.app.domain.model.ReportTemplateCatalog
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

private enum class FileAction { OPEN, SAVE_AS, SHARE, PRINT }
private enum class VoiceChoice { GOOGLE, AI, KEYBOARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(onBack: () -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDepositDatePicker by remember { mutableStateOf(false) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var captureTarget by remember { mutableStateOf<ReportCaptureField?>(null) }
    var captureCustomSectionId by remember { mutableStateOf<String?>(null) }
    var newSectionTitle by remember { mutableStateOf("") }
    var quickInputText by remember { mutableStateOf("") }
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf(FileAction.OPEN) }
    var pendingSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importedTarget by remember { mutableStateOf(ReportCaptureField.DOCUMENTS) }
    var showInAppCamera by remember { mutableStateOf(false) }
    var voiceChoiceTarget by remember { mutableStateOf<ReportCaptureField?>(null) }
    var activeVoiceTarget by remember { mutableStateOf<ReportCaptureField?>(null) }
    var voiceChoice by remember { mutableStateOf(VoiceChoice.GOOGLE) }
    var showContinuousDictation by remember { mutableStateOf(false) }
    var showAiRecording by remember { mutableStateOf(false) }
    var pendingGoogleSpeech by remember { mutableStateOf(false) }
    var showSiteSketchEditor by remember { mutableStateOf(false) }
    var sketchBaseImage by remember { mutableStateOf<Bitmap?>(null) }
    var showMapLocationPicker by remember { mutableStateOf(false) }
    var mapLocationQuery by remember { mutableStateOf("") }
    var assistantRequest by remember { mutableStateOf("") }
    var assistantPreview by remember { mutableStateOf<String?>(null) }
    var rulesEditor by remember { mutableStateOf<String?>(null) }
    var styleLearningPreview by remember { mutableStateOf<String?>(null) }
    var expandedSectionId by remember { mutableStateOf<String?>(null) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination ->
        val source = pendingSourceUri
        if (destination != null && source != null) runCatching {
            context.contentResolver.openInputStream(source).use { input ->
                context.contentResolver.openOutputStream(destination).use { output ->
                    requireNotNull(input); requireNotNull(output); input.copyTo(output)
                }
            }
        }
        pendingSourceUri = null
    }
    val importWordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onImportWord) }
    val importExcelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onImportExcel) }
    val importPowerPointLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onImportPowerPoint) }
    val learningReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onImportLearningReport) }
    val editableTemplateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onImportEditableReportTemplate)
    }
    val smartTemplateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingAction = FileAction.SAVE_AS
            viewModel.onSelectWordTemplate(it)
        }
    }
    val sketchImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it).use { stream -> BitmapFactory.decodeStream(stream) } }
                .getOrNull()
                ?.let { bitmap -> sketchBaseImage = bitmap; showSiteSketchEditor = true }
                ?: scope.launch { snackbar.showSnackbar("تعذر فتح الصورة المختارة للمخطط") }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showInAppCamera = true
        else scope.launch { snackbar.showSnackbar("يلزم السماح بالكاميرا لتصوير مستندات التقرير") }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                quickInputText = spoken
                if (captureTarget == null) captureTarget = activeVoiceTarget ?: ReportCaptureField.SUBJECT
                if (voiceChoice == VoiceChoice.AI) {
                    viewModel.refineVoiceTranscript(spoken) { refined -> quickInputText = refined }
                }
            }
        }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingGoogleSpeech) {
            pendingGoogleSpeech = false
            runCatching {
                speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن، ثم راجع النص قبل اعتماده")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
                })
            }.onFailure { scope.launch { snackbar.showSnackbar("خدمة التعرف على الصوت غير متاحة على هذا الجهاز") } }
        } else if (!granted) {
            pendingGoogleSpeech = false
            scope.launch { snackbar.showSnackbar("يلزم السماح بالميكروفون للإدخال الصوتي") }
        }
    }

    fun startArabicDictation(target: ReportCaptureField = ReportCaptureField.SUBJECT, customSectionId: String? = null) {
        captureCustomSectionId = customSectionId; quickInputText = ""
        voiceChoiceTarget = target
    }

    fun launchGoogleVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingGoogleSpeech = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        runCatching {
            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن، ثم راجع النص قبل اعتماده")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
            })
        }.onFailure { scope.launch { snackbar.showSnackbar("خدمة التعرف على الصوت غير متاحة على هذا الجهاز") } }
    }

    fun startReportCamera(target: ReportCaptureField, customSectionId: String? = null) {
        importedTarget = target
        captureCustomSectionId = customSectionId
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showInAppCamera = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.exportedFileUri, state.exportedMimeType) {
        val uri = state.exportedFileUri ?: return@LaunchedEffect
        val mime = state.exportedMimeType ?: ReportViewModel.WORD_MIME
        when (pendingAction) {
            FileAction.OPEN -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            FileAction.SHARE -> runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "مشاركة الملف")) }
            FileAction.SAVE_AS -> {
                pendingSourceUri = uri
                val ext = when (mime) { ReportViewModel.PDF_MIME -> "pdf"; ReportViewModel.EXCEL_MIME -> "xlsx"; else -> "docx" }
                saveAsLauncher.launch("تقرير_${state.caseNo.ifBlank { "جديد" }}_${state.caseYear.ifBlank { "بدون_سنة" }}.$ext")
            }
            FileAction.PRINT -> if (mime == ReportViewModel.PDF_MIME) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                pm.print("تقرير ${state.caseNo}/${state.caseYear}", PdfUriPrintAdapter(context, uri, "report.pdf"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        pendingAction = FileAction.OPEN
        viewModel.onExportEventConsumed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Column { Text("تقرير الخبرة", fontWeight = FontWeight.Bold); Text(if (state.isIndependent) "تقرير مستقل" else "مرتبط بقضية مسجلة", style = MaterialTheme.typography.labelMedium) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = viewModel::onSave, enabled = !state.isSaving, modifier = Modifier.weight(1f)) { Text(if (state.isSaving) "جارٍ الحفظ..." else "حفظ") }
                    Box(Modifier.weight(1f)) {
                        Button(onClick = { fileMenuExpanded = true }, enabled = !state.isExporting, modifier = Modifier.fillMaxWidth()) { Text(if (state.isExporting) "جارٍ التجهيز..." else "ملف / طباعة") }
                        DropdownMenu(expanded = fileMenuExpanded, onDismissRequest = { fileMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("استيراد قالب للعمل عليه داخل التقرير") }, onClick = { fileMenuExpanded = false; editableTemplateLauncher.launch(arrayOf(ReportViewModel.WORD_MIME, "application/msword")) })
                            DropdownMenuItem(text = { Text("تعبئة قالب Word بنفس التنسيق") }, onClick = { fileMenuExpanded = false; smartTemplateLauncher.launch(arrayOf(ReportViewModel.WORD_MIME, "application/msword")) })
                            if (state.savedWordTemplateUri.isNotBlank()) {
                                DropdownMenuItem(text = { Text("استخدام قالب Word الشخصي لهذا النوع") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SAVE_AS; viewModel.onUseSavedWordTemplate() })
                            }
                            DropdownMenuItem(text = { Text("استيراد من Word") }, onClick = { fileMenuExpanded = false; importWordLauncher.launch(arrayOf(ReportViewModel.WORD_MIME, "application/msword")) })
                            DropdownMenuItem(text = { Text("استيراد من Excel") }, onClick = { fileMenuExpanded = false; importExcelLauncher.launch(arrayOf(ReportViewModel.EXCEL_MIME, "application/vnd.ms-excel")) })
                            DropdownMenuItem(text = { Text("استيراد من PowerPoint") }, onClick = { fileMenuExpanded = false; importPowerPointLauncher.launch(arrayOf(ReportViewModel.POWERPOINT_MIME, "application/vnd.ms-powerpoint")) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("فتح نسخة Word") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.OPEN; viewModel.onExportWord() })
                            DropdownMenuItem(text = { Text("حفظ باسم Word") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SAVE_AS; viewModel.onExportWord() })
                            DropdownMenuItem(text = { Text("فتح نسخة PDF") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.OPEN; viewModel.onExportPdf() })
                            DropdownMenuItem(text = { Text("حفظ باسم PDF") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SAVE_AS; viewModel.onExportPdf() })
                            DropdownMenuItem(text = { Text("حفظ بيانات التقرير Excel") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SAVE_AS; viewModel.onExportExcel() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("طباعة مباشرة") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.PRINT; viewModel.onExportPdf() })
                            DropdownMenuItem(text = { Text("مشاركة Word") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SHARE; viewModel.onExportWord() })
                            DropdownMenuItem(text = { Text("مشاركة PDF") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SHARE; viewModel.onExportPdf() })
                            DropdownMenuItem(text = { Text("مشاركة Excel") }, onClick = { fileMenuExpanded = false; pendingAction = FileAction.SHARE; viewModel.onExportExcel() })
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) { Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            if (state.autoSaveStatus.isNotBlank()) { Text(state.autoSaveStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(6.dp)) }
            KhabirCard(
                contentPadding = PaddingValues(14.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("محرر التقرير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("واجهة مكتبية مألوفة بخانات مرتبة، مع حفظ تلقائي وصوت وكاميرا بجوار كل قسم.", style = MaterialTheme.typography.bodySmall)
                    Text("بيانات الدعوى المسجلة تنتقل للغلاف ورأس التقرير تلقائيًا.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            KhabirCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(8.dp)); Text("بيانات الدعوى للتقرير", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    if (state.isIndependent) {
                        Text("هذا التقرير غير مرتبط بقضية مسجلة. أدخل بيانات الغلاف ورأس التقرير هنا.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KhabirTextField(state.caseNo, viewModel::onCaseNoChanged, label = { Text("رقم الدعوى") }, modifier = Modifier.weight(1f), singleLine = true)
                            KhabirTextField(state.caseYear, viewModel::onCaseYearChanged, label = { Text("السنة") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        KhabirTextField(state.court, viewModel::onCourtChanged, label = { Text("المحكمة") }, singleLine = true)
                        KhabirTextField(
                            state.manualHeader,
                            viewModel::onManualHeaderChanged,
                            label = { Text("رأس التقرير") },
                            minLines = 2
                        )
                        Text(
                            "مثال: تقرير في الدعوى رقم ... — اتركه فارغًا إذا لم ترغب في رأس صفحة",
                            style = MaterialTheme.typography.bodySmall
                        )
                        ReportSectionField(
                            "الخصوم والصفات",
                            state.partiesSummary,
                            viewModel::onPartiesSummaryChanged,
                            minLines = 2,
                            onCamera = { startReportCamera(ReportCaptureField.PARTIES) },
                            onMic = { startArabicDictation(ReportCaptureField.PARTIES) }
                        )
                    } else {
                        Text("الدعوى ${state.caseNo} لسنة ${state.caseYear}", fontWeight = FontWeight.Bold); Text(state.court)
                        if (state.partiesSummary.isNotBlank()) Text(state.partiesSummary, style = MaterialTheme.typography.bodySmall)
                        Text("البيانات ورأس التقرير تُنشأ تلقائيًا من القضية المسجلة.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            KhabirCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إدخال واستيراد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("الصوت والصورة وملفات Office تُراجع قبل اعتمادها، ولا يتم استبدال محتوى التقرير تلقائيًا.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (state.isOcrProcessing) "جارٍ قراءة صفحات المستند..." else "كل زر تصوير في التقرير يتيح تصوير مستند متعدد الصفحات ثم مراجعته دفعة واحدة.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FilledTonalButton(
                        onClick = { editableTemplateLauncher.launch(arrayOf(ReportViewModel.WORD_MIME, "application/msword")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("استيراد قالب للعمل عليه داخل التقرير") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { smartTemplateLauncher.launch(arrayOf(ReportViewModel.WORD_MIME, "application/msword")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("قالب Word بنفس التنسيق") }
                        OutlinedButton(
                            onClick = { styleLearningPreview = viewModel.assistantContext() },
                            modifier = Modifier.weight(1f)
                        ) { Text("التعلّم من تقرير معتمد") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            KhabirCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مساعد التقرير بالذكاء الاصطناعي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "اكتب المطلوب مثل: رتّب بحث المستندات أو صغ هذه الفقرة. لن يُضاف شيء للتقرير إلا بعد مراجعتك واعتمادك.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    KhabirTextField(
                        value = assistantRequest,
                        onValueChange = { assistantRequest = it },
                        label = { Text("ماذا تريد أن أعدّل أو أكتب؟") },
                        minLines = 2
                    )
                    KhabirPrimaryButton(
                        text = if (state.isAssistantWorking) "جارٍ إعداد الاقتراح..." else "إعداد اقتراح",
                        onClick = { assistantPreview = viewModel.assistantContext() },
                        enabled = assistantRequest.isNotBlank() && !state.isAssistantWorking
                    )
                    if (state.assistantReply.isNotBlank()) {
                        KhabirTextField(
                            value = state.assistantReply,
                            onValueChange = viewModel::onAssistantReplyChanged,
                            label = { Text("اقتراح قابل للتعديل") },
                            minLines = 5
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                quickInputText = state.assistantReply
                                captureTarget = ReportCaptureField.SUBJECT
                            }, modifier = Modifier.weight(1f)) { Text("إدراج") }
                            OutlinedButton(onClick = {
                                clipboardManager.setText(AnnotatedString(state.assistantReply))
                                scope.launch { snackbar.showSnackbar("تم نسخ الاقتراح") }
                            }, modifier = Modifier.weight(1f)) { Text("نسخ") }
                            TextButton(onClick = viewModel::onAssistantReplyConsumed, modifier = Modifier.weight(1f)) { Text("رفض") }
                        }
                    }
                    HorizontalDivider()
                    Text("قواعد الصياغة فقط: لا تحفظ أسماء أو وقائع قضايا. تُرسل القواعد مع طلب AI بعد مراجعتك.", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { rulesEditor = viewModel.learnedRules().ifBlank { "استخدم عناوين واضحة وفقرات قصيرة. حافظ على الأسماء والأرقام كما وردت. لا تضف وقائع غير موجودة." } },
                            modifier = Modifier.weight(1f)
                        ) { Text("مراجعة القواعد") }
                        FilledTonalButton(
                            onClick = { styleLearningPreview = viewModel.assistantContext() },
                            modifier = Modifier.weight(1f)
                        ) { Text("من التقرير الحالي") }
                    }
                    OutlinedButton(
                        onClick = { learningReportLauncher.launch(arrayOf(ReportViewModel.WORD_MIME, "application/msword")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("التعلّم من تقرير Word معتمد") }
                    TextButton(onClick = viewModel::onClearReportLearning, modifier = Modifier.fillMaxWidth()) { Text("مسح التعلّم") }
                    if (state.learningStatus.isNotBlank()) Text(state.learningStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            val savedSketch = remember(state.siteSketchPath) {
                state.siteSketchPath.takeIf(String::isNotBlank)?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
            }
            KhabirCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("قالب التقرير: ${state.template.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("العناوين والترتيب والبنود المختارة تُحفظ مع هذا التقرير وتظهر بنفسها في Word وPDF.", style = MaterialTheme.typography.bodySmall)
                            if (state.template.referenceReport.isNotBlank()) {
                                Text("النموذج المرجعي: ${state.template.referenceReport}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("قاعدة ثابتة: نتائج AI وOCR والاستيراد لا تُطبق مباشرة؛ تظهر أولًا في شاشة مراجعة فوقية قبل الاعتماد.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { showTemplateEditor = true }) { Text("تعديل القالب") }
                    }
                    if (state.savedWordTemplateUri.isNotBlank()) {
                        Text("تم حفظ نموذج Word شخصي لهذا النوع من التقارير ويمكن ملؤه مرة أخرى بنفس تنسيقه.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { pendingAction = FileAction.SAVE_AS; viewModel.onUseSavedWordTemplate() }, modifier = Modifier.weight(1f)) { Text("استخدام قالبي لهذا النوع") }
                            TextButton(onClick = viewModel::onForgetWordTemplate, modifier = Modifier.weight(1f)) { Text("حذف النموذج") }
                        }
                    }
                }
            }
            state.template.orderedSections().filter { it.enabled }.forEach { section ->
                Spacer(Modifier.height(12.dp))
                ReportTemplateSection(
                    section, state, viewModel, ::startReportCamera, ::startArabicDictation,
                    isExpanded = expandedSectionId == section.id,
                    onExpand = { expandedSectionId = section.id },
                    expandedHeight = screenHeightDp * 0.8f
                )
                if (section.id == "inspection" || section.title.contains("معاين")) {
                    Spacer(Modifier.height(8.dp))
                    KhabirCard(contentPadding = PaddingValues(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("الخريطة والرسم الكروكي — ضمن المعاينة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("افتح الخريطة التفاعلية داخل التطبيق، حرّكها وكبّرها، ثم التقط الجزء المطلوب وارسم فوقه. ويمكن حفظ الرسم وحده بعد إخفاء خلفية الخريطة.", style = MaterialTheme.typography.bodySmall)
                            if (savedSketch != null) androidx.compose.foundation.Image(
                                bitmap = savedSketch.asImageBitmap(), contentDescription = "معاينة مخطط الموقع",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { sketchBaseImage = savedSketch; showSiteSketchEditor = true }, modifier = Modifier.weight(1f)) { Text(if (savedSketch == null) "رسم كروكي" else "تعديل الرسم") }
                                OutlinedButton(onClick = { sketchImageLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("استيراد صورة") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilledTonalButton(onClick = { showMapLocationPicker = true }, modifier = Modifier.weight(1f)) { Text("خريطة تفاعلية داخل التطبيق") }
                                if (savedSketch != null) TextButton(onClick = viewModel::onSiteSketchRemoved) { Text("حذف") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            KhabirTextField(value = state.depositDate?.toString().orEmpty(), onValueChange = {}, readOnly = true, label = { Text("تاريخ إيداع التقرير") }, trailingIcon = { IconButton(onClick = { showDepositDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار التاريخ") } })
            state.errorMessage?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(80.dp))
        }
    }

    state.pendingTemplateUri?.let {
        TemplateMappingDialog(uriKey = it, state = state, viewModel = viewModel)
    }
    assistantPreview?.let { preview ->
        AssistantPreviewDialog(
            assistantRequest = assistantRequest,
            preview = preview,
            onPreviewChange = { assistantPreview = it },
            onDismiss = { assistantPreview = null },
            viewModel = viewModel
        )
    }
    styleLearningPreview?.let { preview ->
        StyleLearningPreviewDialog(
            preview = preview,
            onPreviewChange = { styleLearningPreview = it },
            onDismiss = { styleLearningPreview = null },
            viewModel = viewModel,
            onRulesGenerated = { generated -> rulesEditor = generated }
        )
    }

    if (state.pendingLearningText.isNotBlank()) {
        StyleLearningPreviewDialog(
            preview = state.pendingLearningText,
            onPreviewChange = viewModel::onLearningTextChanged,
            onDismiss = viewModel::onLearningImportConsumed,
            viewModel = viewModel,
            onRulesGenerated = { generated -> rulesEditor = generated }
        )
    }

    rulesEditor?.let { rules ->
        RulesEditorDialog(rules = rules, onRulesChange = { rulesEditor = it }, onDismiss = { rulesEditor = null }, viewModel = viewModel)
    }
    if (showTemplateEditor) {
        TemplateEditorDialog(
            state = state,
            viewModel = viewModel,
            newSectionTitle = newSectionTitle,
            onNewSectionTitleChange = { newSectionTitle = it },
            onDismiss = { showTemplateEditor = false }
        )
    }

    if (showSiteSketchEditor) {
        SiteSketchEditor(
            baseImage = sketchBaseImage,
            onDismiss = { showSiteSketchEditor = false },
            onSave = { bitmap -> viewModel.onSiteSketchSaved(bitmap); showSiteSketchEditor = false }
        )
    }
    if (showMapLocationPicker) {
        InteractiveMapCapture(
            initialQuery = mapLocationQuery,
            onDismiss = { showMapLocationPicker = false },
            onCapture = { bitmap ->
                sketchBaseImage = bitmap
                showMapLocationPicker = false
                showSiteSketchEditor = true
            }
        )
    }

    if (showDepositDatePicker) {
        val initialDate = state.depositDate ?: java.time.LocalDate.now()
        val dp = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDepositDatePicker = false }, confirmButton = { TextButton(onClick = { dp.selectedDateMillis?.let { viewModel.onDepositDateChanged(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }; showDepositDatePicker = false }) { Text("تأكيد") } }, dismissButton = { TextButton(onClick = { showDepositDatePicker = false }) { Text("إلغاء") } }) { DatePicker(dp) }
    }

    voiceChoiceTarget?.let { target ->
        VoiceChoiceDialog(
            onDismiss = { voiceChoiceTarget = null },
            onGoogleChosen = { voiceChoice = VoiceChoice.GOOGLE; activeVoiceTarget = target; voiceChoiceTarget = null; launchGoogleVoice() },
            onContinuousChosen = { activeVoiceTarget = target; voiceChoiceTarget = null; showContinuousDictation = true },
            onAiChosen = { activeVoiceTarget = target; voiceChoiceTarget = null; showAiRecording = true },
            onKeyboardChosen = { voiceChoiceTarget = null; captureTarget = target }
        )
    }

    if (showAiRecording) {
        com.khabir.app.presentation.common.AiAudioRecordingDialog(
            transcribe = viewModel::transcribeAudio,
            onDismiss = { showAiRecording = false },
            onDone = { text ->
                showAiRecording = false
                captureTarget = activeVoiceTarget ?: ReportCaptureField.SUBJECT
                quickInputText = text
            }
        )
    }
    if (showContinuousDictation) {
        val dictation = com.khabir.app.presentation.common.rememberContinuousDictation { message ->
            scope.launch { snackbar.showSnackbar(message) }
        }
        com.khabir.app.presentation.common.ContinuousDictationDialog(
            controller = dictation,
            onDismiss = { showContinuousDictation = false },
            onDone = { text ->
                showContinuousDictation = false
                if (text.isNotBlank()) {
                    captureTarget = activeVoiceTarget ?: ReportCaptureField.SUBJECT
                    quickInputText = text
                }
            }
        )
    }

    captureTarget?.let { initialTarget ->
        CaptureReviewDialog(
            initialTarget = initialTarget,
            captureCustomSectionId = captureCustomSectionId,
            quickInputText = quickInputText,
            onQuickInputTextChange = { quickInputText = it },
            state = state,
            viewModel = viewModel,
            onDismiss = { captureTarget = null }
        )
    }

    if (state.pendingExcelImport.isNotEmpty()) {
        ExcelImportReviewDialog(pendingExcelImport = state.pendingExcelImport, viewModel = viewModel)
    }

    state.importedOfficeText?.let { importedText ->
        OfficeImportReviewDialog(
            importedText = importedText,
            importedOfficeSource = state.importedOfficeSource,
            captureCustomSectionId = captureCustomSectionId,
            importedTarget = importedTarget,
            onImportedTargetChange = { importedTarget = it },
            state = state,
            viewModel = viewModel
        )
    }

    if (showInAppCamera) {
        InAppCameraCapture(
            onDismiss = { showInAppCamera = false },
            onCaptured = { bitmap ->
                showInAppCamera = false
                viewModel.onReportPhotoCaptured(bitmap)
            },
            onGeminiCaptured = { bitmap ->
                showInAppCamera = false
                viewModel.onReportPhotoCapturedWithGemini(bitmap)
            },
            onPagesCaptured = { pages, useAi ->
                showInAppCamera = false
                if (importedTarget == ReportCaptureField.SUBJECT) viewModel.onPetitionSubjectPagesCaptured(pages, useAi)
                else viewModel.onDocumentPagesCaptured(pages, useAi)
            },
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } }
        )
    }
}

@Composable
private fun TemplateMappingDialog(uriKey: Any, state: ReportUiState, viewModel: ReportViewModel) {
    var mapping by remember(uriKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedField by remember(uriKey) { mutableStateOf("الموضوع") }
    AlertDialog(
        onDismissRequest = viewModel::onCancelTemplateMapping,
        title = { Text("تحويل نموذج Word إلى قالب") },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Text("اختر اسم الحقل ثم اضغط الفقرة القديمة التي تريد استبدالها به. باقي النموذج يبقى كما هو. راجع أي بيانات قديمة قبل مشاركة الناتج.")
            KhabirTextField(selectedField, { selectedField = it }, label = { Text("اسم الحقل: الموضوع، المأمورية، المحكمة، الخصوم، بنود القالب أو عنوان بند") })
            state.template.orderedSections().filter { section -> section.enabled }.forEach { section ->
                TextButton(onClick = { selectedField = section.title }) { Text(section.title) }
            }
            state.templateParagraphs.distinct().forEach { paragraph ->
                OutlinedButton(onClick = {
                    mapping = if (mapping.containsKey(paragraph)) mapping - paragraph
                    else mapping + (paragraph to selectedField.trim())
                }, modifier = Modifier.fillMaxWidth()) {
                    Text((mapping[paragraph]?.let { field -> "[$field] " } ?: "") + paragraph)
                }
            }
        } },
        confirmButton = { Button(enabled = mapping.isNotEmpty(), onClick = { viewModel.onApplyTemplateMapping(mapping) }) { Text("اعتماد الربط وتعبئة نسخة") } },
        dismissButton = { TextButton(onClick = viewModel::onCancelTemplateMapping) { Text("إلغاء") } }
    )
}

@Composable
private fun AssistantPreviewDialog(
    assistantRequest: String,
    preview: String,
    onPreviewChange: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ReportViewModel
) {
    val rules = remember(preview) { viewModel.learnedRules() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مراجعة ما سيُرسل إلى مزوّد AI") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                Text("الطلب: $assistantRequest")
                Text("يمكن حذف أي بيانات لا تريد إرسالها. لا يرسل التطبيق الملف الأصلي.")
                KhabirTextField(preview, onPreviewChange, label = { Text("النص المرسل") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                Text("قواعد الصياغة: $rules")
                Text("تعليمات الخبير: ${viewModel.expertInstructions()}")
            }
        },
        confirmButton = { Button(onClick = { viewModel.onAskAssistant(assistantRequest, preview, rules); onDismiss() }) { Text("موافقة وإرسال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun StyleLearningPreviewDialog(
    preview: String,
    onPreviewChange: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ReportViewModel,
    onRulesGenerated: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مراجعة التقرير قبل استخراج الأسلوب") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                Text("لن يُحفظ هذا النص كذاكرة. يمكنك حذف أي أسماء أو بيانات لا تريد إرسالها قبل الموافقة.")
                KhabirTextField(
                    value = preview,
                    onValueChange = onPreviewChange,
                    label = { Text("النص المستخدم لاستخراج قواعد الصياغة") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 7
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val approved = preview
                onDismiss()
                viewModel.onDeriveStyleRulesFromApprovedReport(approved, onRulesGenerated)
            }) { Text("موافقة واستخراج القواعد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun RulesEditorDialog(rules: String, onRulesChange: (String) -> Unit, onDismiss: () -> Unit, viewModel: ReportViewModel) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قواعد الصياغة المعتمدة") },
        text = { KhabirTextField(rules, onRulesChange, label = { Text("أسلوب الكتابة فقط، بدون بيانات قضايا") }, minLines = 5) },
        confirmButton = { Button(onClick = { viewModel.onLearnFromCurrentReport(rules); onDismiss() }) { Text("اعتماد القواعد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun TemplateEditorDialog(
    state: ReportUiState,
    viewModel: ReportViewModel,
    newSectionTitle: String,
    onNewSectionTitleChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار وتعديل قالب التقرير") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اختر قالب البداية، ثم عدّل الاسم والعناوين والترتيب. التعديل يخص هذا التقرير فقط.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportTemplateCatalog.all.take(3).forEach { template ->
                        FilterChip(selected = state.template.id == template.id, onClick = { viewModel.onTemplateSelected(template.id) }, label = { Text(template.name) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportTemplateCatalog.all.drop(3).forEach { template ->
                        FilterChip(selected = state.template.id == template.id, onClick = { viewModel.onTemplateSelected(template.id) }, label = { Text(template.name) })
                    }
                }
                KhabirTextField(state.template.name, viewModel::onTemplateNameChanged, label = { Text("اسم القالب لهذا التقرير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KhabirTextField(
                        newSectionTitle,
                        onNewSectionTitleChange,
                        label = { Text("اسم بند جديد") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = { viewModel.onSectionAdded(newSectionTitle); onNewSectionTitleChange("") },
                        enabled = newSectionTitle.isNotBlank()
                    ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("إضافة") }
                }
                state.template.orderedSections().forEachIndexed { index, section ->
                    KhabirCard(contentPadding = PaddingValues(10.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = section.enabled, onCheckedChange = { viewModel.onSectionEnabledChanged(section.id, it) })
                                Spacer(Modifier.width(8.dp))
                                KhabirTextField(section.title, { viewModel.onSectionTitleChanged(section.id, it) }, label = { Text("عنوان البند") }, modifier = Modifier.weight(1f), singleLine = true)
                                IconButton(onClick = { viewModel.onSectionMoved(section.id, index - 1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, "لأعلى") }
                                IconButton(onClick = { viewModel.onSectionMoved(section.id, index + 1) }, enabled = index < state.template.sections.lastIndex) { Icon(Icons.Filled.ArrowDownward, "لأسفل") }
                                if (section.removable) {
                                    IconButton(onClick = { viewModel.onSectionRemoved(section.id) }) { Icon(Icons.Filled.Delete, "حذف البند") }
                                }
                            }
                            section.headingLines.forEachIndexed { lineIndex, line ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    KhabirTextField(line, { viewModel.onSectionHeadingChanged(section.id, lineIndex, it) }, label = { Text("سطر عنوان إضافي") }, modifier = Modifier.weight(1f), singleLine = true)
                                    IconButton(onClick = { viewModel.onSectionHeadingRemoved(section.id, lineIndex) }) { Icon(Icons.Filled.Delete, "حذف السطر") }
                                }
                            }
                            TextButton(onClick = { viewModel.onSectionHeadingAdded(section.id) }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("إضافة سطر تحت العنوان") }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("اعتماد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun VoiceChoiceDialog(
    onDismiss: () -> Unit,
    onGoogleChosen: () -> Unit,
    onContinuousChosen: () -> Unit,
    onAiChosen: () -> Unit,
    onKeyboardChosen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر طريقة الإدخال الصوتي") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onGoogleChosen, modifier = Modifier.fillMaxWidth()) { Text("صوت Google — جملة واحدة") }
            TextButton(onClick = onContinuousChosen, modifier = Modifier.fillMaxWidth()) { Text("استماع مستمر حتى «تم»") }
            TextButton(onClick = onAiChosen, modifier = Modifier.fillMaxWidth()) { Text("صوت AI — تسجيل مستقل ثم تفريغ") }
            TextButton(onClick = onKeyboardChosen, modifier = Modifier.fillMaxWidth()) { Text("لوحة المفاتيح — كتابة أو مايك الكيبورد") }
        } },
        confirmButton = {}
    )
}

@Composable
private fun CaptureReviewDialog(
    initialTarget: ReportCaptureField,
    captureCustomSectionId: String?,
    quickInputText: String,
    onQuickInputTextChange: (String) -> Unit,
    state: ReportUiState,
    viewModel: ReportViewModel,
    onDismiss: () -> Unit
) {
    var chosenTarget by remember(initialTarget) { mutableStateOf(initialTarget) }
    var replaceExisting by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("مراجعة الإدخال") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("اختر القسم ثم راجع أو عدّل النص قبل اعتماده")
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(replaceExisting, { replaceExisting = it }); Text("استبدال النص القديم بالكامل (بدل الإضافة)") }
            ReportCaptureField.entries.filter { it != ReportCaptureField.CUSTOM || captureCustomSectionId != null }.forEach { field -> FilterChip(selected = chosenTarget == field, onClick = { chosenTarget = field }, label = { Text(field.label) }) }
            KhabirTextField(quickInputText, onQuickInputTextChange, label = { Text("النص المستخرج / المملى") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = {
        if (chosenTarget == ReportCaptureField.CUSTOM) captureCustomSectionId?.let {
            viewModel.onCustomSectionChanged(it, mergeReportInput(state.customSectionContents[it].orEmpty(), quickInputText, replaceExisting))
        }
        else chosenTarget.write(viewModel, quickInputText, replaceExisting)
        onDismiss()
    }) { Text("اعتماد") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun ExcelImportReviewDialog(pendingExcelImport: Map<String, String>, viewModel: ReportViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::onCancelExcelImport,
        title = { Text("مراجعة بيانات Excel") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تم التعرف على ${pendingExcelImport.size} خانة. راجعها قبل استبدال بيانات التقرير الحالية.")
                pendingExcelImport.forEach { (field, value) ->
                    KhabirCard(contentPadding = PaddingValues(10.dp)) { Text(field, fontWeight = FontWeight.Bold); Text(value.ifBlank { "(فارغ)" }, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { Button(onClick = viewModel::onApplyExcelImport) { Text("اعتماد البيانات") } },
        dismissButton = { TextButton(onClick = viewModel::onCancelExcelImport) { Text("إلغاء") } }
    )
}

@Composable
private fun OfficeImportReviewDialog(
    importedText: String,
    importedOfficeSource: String?,
    captureCustomSectionId: String?,
    importedTarget: ReportCaptureField,
    onImportedTargetChange: (ReportCaptureField) -> Unit,
    state: ReportUiState,
    viewModel: ReportViewModel
) {
    var reviewedText by remember(importedText) { mutableStateOf(importedText) }
    var replaceExisting by remember(importedText) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = viewModel::onImportedOfficeTextConsumed, title = { Text("مراجعة استيراد ${importedOfficeSource ?: "Word"}") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("اختر القسم الذي سيضاف إليه النص. لن يتم اعتماد النص قبل المراجعة.")
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(replaceExisting, { replaceExisting = it }); Text("استبدال النص القديم بالكامل") }
            ReportCaptureField.entries.filter { it != ReportCaptureField.CUSTOM || captureCustomSectionId != null }.forEach { field -> FilterChip(selected = importedTarget == field, onClick = { onImportedTargetChange(field) }, label = { Text(field.label) }) }
            KhabirTextField(value = reviewedText, onValueChange = { reviewedText = it }, minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = {
        if (importedTarget == ReportCaptureField.CUSTOM) captureCustomSectionId?.let {
            viewModel.onCustomSectionChanged(it, mergeReportInput(state.customSectionContents[it].orEmpty(), reviewedText, replaceExisting))
        }
        else importedTarget.write(viewModel, reviewedText, replaceExisting)
        viewModel.onImportedOfficeTextConsumed()
    }) { Text("اعتماد في القسم") } }, dismissButton = { TextButton(onClick = viewModel::onImportedOfficeTextConsumed) { Text("إلغاء") } })
}

@Composable
private fun ReportTemplateSection(
    section: ReportSectionDefinition,
    state: ReportUiState,
    viewModel: ReportViewModel,
    onCamera: (ReportCaptureField, String?) -> Unit,
    onMic: (ReportCaptureField, String?) -> Unit,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    expandedHeight: Dp
) {
    val mapping = when (section.id) {
        "subject" -> Triple(state.subjectOfCase, viewModel::onSubjectChanged, ReportCaptureField.SUBJECT)
        "assignment" -> Triple(state.assignment, viewModel::onAssignmentChanged, ReportCaptureField.ASSIGNMENT)
        "proceedings" -> Triple(state.proceedings, viewModel::onProceedingsChanged, ReportCaptureField.PROCEEDINGS)
        "statements" -> Triple(state.partyStatements, viewModel::onPartyStatementsChanged, ReportCaptureField.STATEMENTS)
        "witnesses" -> Triple(state.witnessStatements, viewModel::onWitnessStatementsChanged, ReportCaptureField.WITNESSES)
        "inspection" -> Triple(state.inspection, viewModel::onInspectionChanged, ReportCaptureField.INSPECTION)
        "documents" -> Triple(state.documentsSubmitted, viewModel::onDocumentsChanged, ReportCaptureField.DOCUMENTS)
        "facts" -> Triple(state.facts, viewModel::onFactsChanged, ReportCaptureField.FACTS)
        "research" -> Triple(state.research, viewModel::onResearchChanged, ReportCaptureField.RESEARCH)
        "conclusion" -> Triple(state.conclusion, viewModel::onConclusionChanged, ReportCaptureField.CONCLUSION)
        "attachments" -> Triple(state.attachmentsNote, viewModel::onAttachmentsNoteChanged, ReportCaptureField.ATTACHMENTS)
        else -> null
    }
    if (section.id == "calculations") {
        ReportCalculationsEditor(state.calculationsTable, viewModel::onCalculationsChanged, { onCamera(ReportCaptureField.CALCULATIONS, null) }, { onMic(ReportCaptureField.CALCULATIONS, null) })
    } else if (mapping != null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.headingLines.filter(String::isNotBlank).forEach { Text(it, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium) }
            if (section.id == "subject") {
                KhabirCard(contentPadding = PaddingValues(10.dp), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("موضع الطلبات الختامية داخل الموضوع", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = state.finalRequestsPlacement == FinalRequestsPlacement.START,
                                onClick = { viewModel.onFinalRequestsPlacementChanged(FinalRequestsPlacement.START) },
                                label = { Text("في البداية") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.finalRequestsPlacement == FinalRequestsPlacement.END,
                                onClick = { viewModel.onFinalRequestsPlacementChanged(FinalRequestsPlacement.END) },
                                label = { Text("في النهاية") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text("الاختيار يعيد ترتيب الموضوع الحالي ويُستخدم أيضًا عند استخراج موضوع الدعوى من العريضة.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                ReportSectionField(
                    section.title, mapping.first, mapping.second,
                    5,
                    { onCamera(mapping.third, null) }, { onMic(mapping.third, null) },
                    isExpanded = isExpanded, onExpand = onExpand, expandedHeight = expandedHeight
                )
            } else if (section.id == "statements") {
                val statements = splitPartyStatements(state.partyStatements)
                ReportSectionField(
                    "أقوال المدعي",
                    statements.first,
                    { plaintiff -> viewModel.onPartyStatementsChanged(joinPartyStatements(plaintiff, statements.second)) },
                    5,
                    { onCamera(ReportCaptureField.STATEMENTS, null) },
                    { onMic(ReportCaptureField.STATEMENTS, null) },
                    isExpanded = isExpanded,
                    onExpand = onExpand,
                    expandedHeight = expandedHeight
                )
                Spacer(Modifier.height(8.dp))
                ReportSectionField(
                    "أقوال المدعى عليه",
                    statements.second,
                    { defendant -> viewModel.onPartyStatementsChanged(joinPartyStatements(statements.first, defendant)) },
                    5,
                    { onCamera(ReportCaptureField.STATEMENTS, null) },
                    { onMic(ReportCaptureField.STATEMENTS, null) },
                    isExpanded = isExpanded,
                    onExpand = onExpand,
                    expandedHeight = expandedHeight
                )
            } else {
                ReportSectionField(
                    section.title, mapping.first, mapping.second,
                    if (section.id in setOf("witnesses", "inspection", "documents", "research")) 5 else 4,
                    { onCamera(mapping.third, null) }, { onMic(mapping.third, null) },
                    isExpanded = isExpanded, onExpand = onExpand, expandedHeight = expandedHeight
                )
            }
        }
    } else {
        ReportSectionField(
            section.title,
            state.customSectionContents[section.id].orEmpty(),
            { viewModel.onCustomSectionChanged(section.id, it) },
            minLines = 4,
            onCamera = { onCamera(ReportCaptureField.CUSTOM, section.id) },
            onMic = { onMic(ReportCaptureField.CUSTOM, section.id) },
            isExpanded = isExpanded, onExpand = onExpand, expandedHeight = expandedHeight
        )
    }
}

private fun splitPartyStatements(value: String): Pair<String, String> {
    val normalized = value.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) return "" to ""
    val plaintiffLabel = "أقوال المدعي:"
    val defendantLabel = "أقوال المدعى عليه:"
    val pIndex = normalized.indexOf(plaintiffLabel)
    val dIndex = normalized.indexOf(defendantLabel)
    if (pIndex >= 0 && dIndex > pIndex) {
        val plaintiff = normalized.substring(pIndex + plaintiffLabel.length, dIndex).trim()
        val defendant = normalized.substring(dIndex + defendantLabel.length).trim()
        return plaintiff to defendant
    }
    return normalized to ""
}

private fun joinPartyStatements(plaintiff: String, defendant: String): String = buildString {
    append("أقوال المدعي:\n")
    append(plaintiff.trim())
    append("\n\nأقوال المدعى عليه:\n")
    append(defendant.trim())
}.trim()

private enum class ReportCaptureField(val label: String) {
    PARTIES("الخصوم والصفات"), SUBJECT("الموضوع"), ASSIGNMENT("المأمورية"), PROCEEDINGS("مباشرة المأمورية"), STATEMENTS("أقوال طرفي التداعي"), WITNESSES("سماع الشهود"), INSPECTION("المعاينة على الطبيعة"), DOCUMENTS("بحث المستندات"), FACTS("الوقائع والملاحظات"), RESEARCH("البحث"), CALCULATIONS("الحسابات والجداول"), CONCLUSION("النتيجة النهائية"), ATTACHMENTS("ملاحظات المرفقات"), CUSTOM("البند المضاف");
    fun write(vm: ReportViewModel, incoming: String, replace: Boolean = false) {
        val state = vm.uiState.value
        val old = when (this) {
            PARTIES -> state.partiesSummary; SUBJECT -> state.subjectOfCase; ASSIGNMENT -> state.assignment
            PROCEEDINGS -> state.proceedings; STATEMENTS -> state.partyStatements; WITNESSES -> state.witnessStatements
            INSPECTION -> state.inspection; DOCUMENTS -> state.documentsSubmitted; FACTS -> state.facts
            RESEARCH -> state.research; CALCULATIONS -> state.calculationsTable; CONCLUSION -> state.conclusion
            ATTACHMENTS -> state.attachmentsNote; CUSTOM -> ""
        }
        val value = mergeReportInput(old, incoming, replace)
        when (this) {
        PARTIES -> vm.onPartiesSummaryChanged(value); SUBJECT -> vm.onSubjectChanged(value); ASSIGNMENT -> vm.onAssignmentChanged(value); PROCEEDINGS -> vm.onProceedingsChanged(value); STATEMENTS -> vm.onPartyStatementsChanged(value); WITNESSES -> vm.onWitnessStatementsChanged(value); INSPECTION -> vm.onInspectionChanged(value); DOCUMENTS -> vm.onDocumentsChanged(value); FACTS -> vm.onFactsChanged(value); RESEARCH -> vm.onResearchChanged(value); CALCULATIONS -> vm.onCalculationsChanged(value); CONCLUSION -> vm.onConclusionChanged(value); ATTACHMENTS -> vm.onAttachmentsNoteChanged(value); CUSTOM -> Unit
        }
    }
}

@Composable
private fun ReportSectionField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    minLines: Int = 2,
    onCamera: () -> Unit,
    onMic: () -> Unit,
    isExpanded: Boolean = false,
    onExpand: () -> Unit = {},
    expandedHeight: Dp = 480.dp
) {
    val listIndentTransformation = remember { ArabicListHangingIndentTransformation() }
    KhabirCard(
        contentPadding = PaddingValues(12.dp),
        containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = if (isExpanded) Modifier.fillMaxWidth().height(expandedHeight) else Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onExpand) {
                    Icon(Icons.Filled.Edit, contentDescription = "تعديل $label")
                    Spacer(Modifier.width(4.dp))
                    Text(if (isExpanded) "مفتوح" else "تعديل")
                }
                FilledTonalIconButton(onClick = onCamera) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "تصوير $label")
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onMic) {
                    Icon(Icons.Filled.Mic, contentDescription = "إملاء صوتي $label")
                }
            }
            Spacer(Modifier.height(6.dp))
            KhabirTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .onFocusChanged { if (it.isFocused) onExpand() }
                    .let { if (isExpanded) it.weight(1f) else it },
                minLines = minLines,
                maxLines = if (isExpanded) Int.MAX_VALUE else minLines,
                visualTransformation = listIndentTransformation,
                placeholder = { Text("اكتب هنا أو استخدم الكاميرا أو الإملاء الصوتي") }
            )
        }
    }
}
