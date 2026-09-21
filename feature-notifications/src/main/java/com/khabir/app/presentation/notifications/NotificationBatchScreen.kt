@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.khabir.app.presentation.notifications

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.Party
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.usecase.notification.ExportNotificationBatchToWordUseCase
import com.khabir.app.presentation.cases.PetitionIntakeParser
import com.khabir.app.presentation.common.InAppCameraCapture
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirTextField
import java.io.File
import java.time.Instant
import java.time.ZoneId

private enum class NotificationVoiceTarget { COURT, FIRST_NAME, REST_NAME, ADDRESS, TIME, LOCATION, DOCUMENTS }
private enum class NotificationVoiceChoice { GOOGLE, AI, KEYBOARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBatchScreen(onBack: () -> Unit, viewModel: NotificationBatchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.exportedFileUri) {
        val uri = state.exportedFileUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "حفظ أو مشاركة الملف"))
            }.onFailure { viewModel.onCameraError("تعذر فتح الملف؛ ثبّت تطبيقًا لعرض ملفات Word أو مشاركتها") }
        }
        viewModel.onExportEventConsumed()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("الإخطارات وسركي الإخطارات", fontWeight = FontWeight.Bold); Text("إدخال واحد لكل المخرجات", style = MaterialTheme.typography.labelMedium) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        floatingActionButton = {
            if (state.mode == NotificationScreenUiState.Mode.BrowsingBatches) ExtendedFloatingActionButton(onClick = viewModel::onStartNewBatch, text = { Text("إضافة إخطارات") }, icon = {})
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.mode) {
                NotificationScreenUiState.Mode.BrowsingBatches -> BatchListSection(
                    state = state,
                    onToggle = viewModel::onTogglePastBatch,
                    onSelectAll = viewModel::onSelectAllPastBatches,
                    onClear = viewModel::onClearPastBatchSelection,
                    onExport = viewModel::onExportSelected,
                    onDelete = viewModel::onDeletePastBatch,
                    onEdit = viewModel::onEditPastBatch
                )
                NotificationScreenUiState.Mode.BuildingNew -> NewBatchSection(state, viewModel)
            }
        }
    }
}

@Composable
private fun BatchListSection(
    state: NotificationScreenUiState,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onExport: (ExportNotificationBatchToWordUseCase.OutputType) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit
) {
    val batches = state.pastBatches
    var pendingDeleteBatchId by remember { mutableStateOf<Long?>(null) }
    pendingDeleteBatchId?.let { batchId ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("تأكيد إزالة الإخطارات") },
            text = { Text("هل تريد إزالة دفعة الإخطارات المحفوظة؟") },
            confirmButton = { Button(onClick = { onDelete(batchId); pendingDeleteBatchId = null }) { Text("إزالة") } },
            dismissButton = { TextButton(onClick = { pendingDeleteBatchId = null }) { Text("إلغاء") } }
        )
    }
    if (batches.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد دفعات إخطارات بعد") }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("اختر القضايا المطلوب تجميعها", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = onSelectAll) { Text("تحديد الكل") }
            if (state.selectedPastBatchIds.isNotEmpty()) TextButton(onClick = onClear) { Text("إلغاء") }
        }
        Text("تم تحديد ${state.selectedPastBatchIds.size} من ${batches.size}", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(batches, key = { it.id }) { batch ->
                val caseLabels = batch.recipients.map { "${it.caseNo}/${it.caseYear}" }.distinct().joinToString("، ")
                KhabirCard(contentPadding = PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = batch.id in state.selectedPastBatchIds,
                            onCheckedChange = { onToggle(batch.id) }
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("قضية $caseLabels", fontWeight = FontWeight.Bold)
                            Text("موعد ${batch.appointmentDate} — ${batch.appointmentTime}", style = MaterialTheme.typography.bodySmall)
                            Text("${batch.recipients.size} مُخطَر", style = MaterialTheme.typography.bodySmall)
                        }
                        if (batch.isReprint) AssistChip(onClick = {}, label = { Text("مُعاد") })
                        IconButton(onClick = { onEdit(batch.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "تعديل دفعة الإخطارات")
                        }
                        IconButton(onClick = { pendingDeleteBatchId = batch.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "إزالة دفعة الإخطارات")
                        }
                    }
                }
            }
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("المخرجات التالية ستجمع كل القضايا المحددة في ملف واحد:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutputButton("الإخطارات", state.isExporting && state.exportingType == ExportNotificationBatchToWordUseCase.OutputType.NOTIFICATIONS, !state.isExporting && state.selectedPastBatchIds.isNotEmpty(), { onExport(ExportNotificationBatchToWordUseCase.OutputType.NOTIFICATIONS) }, Modifier.weight(1f))
            OutputButton("سركي الإخطارات", state.isExporting && state.exportingType == ExportNotificationBatchToWordUseCase.OutputType.SIRKIS, !state.isExporting && state.selectedPastBatchIds.isNotEmpty(), { onExport(ExportNotificationBatchToWordUseCase.OutputType.SIRKIS) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutputButton("الأظرف", state.isExporting && state.exportingType == ExportNotificationBatchToWordUseCase.OutputType.ENVELOPES, !state.isExporting && state.selectedPastBatchIds.isNotEmpty(), { onExport(ExportNotificationBatchToWordUseCase.OutputType.ENVELOPES) }, Modifier.weight(1f))
            OutputButton("حافظة البريد", state.isExporting && state.exportingType == ExportNotificationBatchToWordUseCase.OutputType.MAIL_COVER, !state.isExporting && state.selectedPastBatchIds.isNotEmpty(), { onExport(ExportNotificationBatchToWordUseCase.OutputType.MAIL_COVER) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutputButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(if (busy) "جارٍ..." else label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewBatchSection(state: NotificationScreenUiState, viewModel: NotificationBatchViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showInAppCamera by remember { mutableStateOf(false) }
    var showLensCamera by remember { mutableStateOf(false) }
    var pendingLensCamera by remember { mutableStateOf(false) }
    var voiceTarget by remember { mutableStateOf<NotificationVoiceTarget?>(null) }
    var voiceChoiceTarget by remember { mutableStateOf<NotificationVoiceTarget?>(null) }
    var voiceChoice by remember { mutableStateOf(NotificationVoiceChoice.GOOGLE) }
    var showContinuousDictation by remember { mutableStateOf(false) }
    var pendingGoogleVoice by remember { mutableStateOf(false) }
    var ocrReviewDraft by remember { mutableStateOf("") }
    var reviewedParties by remember { mutableStateOf<List<PetitionIntakeParser.ParsedParty>>(emptyList()) }
    LaunchedEffect(state.ocrReviewText) {
        state.ocrReviewText?.let { recognized ->
            ocrReviewDraft = recognized
            reviewedParties = PetitionIntakeParser.parse(recognized).parties
        }
    }
    com.khabir.app.data.monetization.BlockWorkAds(showInAppCamera || state.isOcrProcessing || state.ocrReviewText != null || state.isCreating)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (pendingLensCamera) showLensCamera = true else showInAppCamera = true
            pendingLensCamera = false
        } else {
            pendingLensCamera = false
            viewModel.onCameraError("يلزم السماح بالكاميرا لتصوير العريضة أو الحكم")
        }
    }
    val importImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val files = uris.take(10).mapNotNull { uri -> copyNotificationImageToCache(context, uri) }
        if (files.isNotEmpty()) {
            viewModel.onCaseSourceChanged(NotificationScreenUiState.CaseSource.NEW_CASE)
            viewModel.onDocumentPagesCaptured(files, true)
        } else if (uris.isNotEmpty()) {
            viewModel.onCameraError("تعذر استيراد الصور المختارة")
        }
    }
    fun applyVoiceText(text: String, refineWithAi: Boolean = voiceChoice == NotificationVoiceChoice.AI) {
        if (text.isNotBlank()) {
            val applyText: (String) -> Unit = { refined ->
            when (voiceTarget) {
                NotificationVoiceTarget.COURT -> viewModel.onManualCourtChanged(refined)
                NotificationVoiceTarget.FIRST_NAME -> viewModel.onManualFirstNameChanged(refined)
                NotificationVoiceTarget.REST_NAME -> viewModel.onManualRestNameChanged(refined)
                NotificationVoiceTarget.ADDRESS -> viewModel.onManualAddressChanged(refined)
                NotificationVoiceTarget.TIME -> viewModel.onAppointmentTimeChanged(refined)
                NotificationVoiceTarget.LOCATION -> viewModel.onAppointmentLocationChanged(refined)
                NotificationVoiceTarget.DOCUMENTS -> viewModel.onRequestedDocumentsChanged(refined)
                null -> Unit
            }
            }
            if (refineWithAi) viewModel.refineVoiceTranscript(text, applyText)
            else applyText(text)
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        applyVoiceText(result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty())
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingGoogleVoice) {
            pendingGoogleVoice = false
            voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالعربية")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
            })
        } else if (!granted) {
            pendingGoogleVoice = false
            viewModel.onCameraError("يلزم السماح بالميكروفون للإدخال الصوتي")
        }
    }
    fun startVoice(target: NotificationVoiceTarget) {
        voiceTarget = target
        voiceChoiceTarget = target
    }
    fun launchGoogleVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingGoogleVoice = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالعربية")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
        })
    }
    voiceChoiceTarget?.let {
        NotificationVoiceChoiceDialog(
            onDismiss = { voiceChoiceTarget = null },
            onGoogleChosen = { voiceChoice = NotificationVoiceChoice.GOOGLE; voiceChoiceTarget = null; launchGoogleVoice() },
            onContinuousChosen = { voiceChoiceTarget = null; showContinuousDictation = true },
            onAiChosen = { voiceChoice = NotificationVoiceChoice.AI; voiceChoiceTarget = null; launchGoogleVoice() },
            onKeyboardChosen = { voiceChoiceTarget = null }
        )
    }
    if (showContinuousDictation) {
        val dictation = com.khabir.app.presentation.common.rememberContinuousDictation { message ->
            viewModel.onCameraError(message)
        }
        com.khabir.app.presentation.common.ContinuousDictationDialog(
            controller = dictation,
            onDismiss = { showContinuousDictation = false },
            onDone = { text ->
                showContinuousDictation = false
                applyVoiceText(text, refineWithAi = true)
            }
        )
    }
    fun startPetitionCamera(lensOnly: Boolean = false) {
        pendingLensCamera = lensOnly
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (lensOnly) showLensCamera = true else showInAppCamera = true
            pendingLensCamera = false
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                KhabirCard(
                    contentPadding = PaddingValues(14.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("بيانات القضية مرة واحدة", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "اختر الدعوى والخصوم ثم أدخل موعد الحضور. سيستخدم التطبيق نفس البيانات تلقائيًا في الإخطارات والسركي والأظرف وحافظة البريد.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                KhabirCard(
                    contentPadding = PaddingValues(14.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("استيراد الخصوم من مستند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "صوّر عريضة الدعوى أو الحكم التمهيدي، ثم راجع أسماء الخصوم وصفاتهم وعناوينهم قبل اعتمادهم للإخطارات.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.onCaseSourceChanged(NotificationScreenUiState.CaseSource.NEW_CASE)
                                    startPetitionCamera(lensOnly = true)
                                },
                                enabled = !state.isOcrProcessing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Search, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Google Lens")
                            }
                            Button(
                                onClick = {
                                    viewModel.onCaseSourceChanged(NotificationScreenUiState.CaseSource.NEW_CASE)
                                    startPetitionCamera()
                                },
                                enabled = !state.isOcrProcessing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isOcrProcessing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Filled.AutoAwesome, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.isOcrProcessing) "جارٍ..." else "AI Vision")
                            }
                        }
                        OutlinedButton(
                            onClick = { importImagesLauncher.launch(arrayOf("image/*")) },
                            enabled = !state.isOcrProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null)
                            Spacer(Modifier.width(6.dp))
                            Text("استيراد صور من الجهاز — حتى 10")
                        }
                        Text(
                            "داخل الكاميرا: لمسة واحدة للتركيز، وضغطتان لالتقاط صفحة.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("مصدر بيانات الإخطار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("دعوى جديدة تفتح خانات فارغة. الدعاوى المسجلة تفتح القائمة أولًا ثم تختار الدعوى وبعدها الشخص المطلوب إخطاره.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.onCaseSourceChanged(NotificationScreenUiState.CaseSource.NEW_CASE) }, modifier = Modifier.weight(1f), enabled = state.caseSource != NotificationScreenUiState.CaseSource.NEW_CASE) { Text("دعوى جديدة") }
                    Button(onClick = { viewModel.onCaseSourceChanged(NotificationScreenUiState.CaseSource.REGISTERED_CASES) }, modifier = Modifier.weight(1f), enabled = state.caseSource != NotificationScreenUiState.CaseSource.REGISTERED_CASES) { Text("الدعاوى المسجلة") }
                }
            }

            if (state.caseSource == NotificationScreenUiState.CaseSource.NEW_CASE) {
                item { ManualRecipientCard(state, viewModel, ::startVoice) }
            } else {
                val selectedCase = state.selectedRegisteredCase
                if (selectedCase == null) {
                    item {
                        Text("اختيار الدعوى المسجلة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        KhabirTextField(state.caseSearchQuery, viewModel::onCaseQueryChanged, label = { Text("ابحث برقم الدعوى أو المحكمة أو اسم الخصم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    items(state.caseSearchResults, key = { "case-${it.id}" }) { case -> RegisteredCaseCard(case = case, onSelect = { viewModel.onRegisteredCaseSelected(case) }) }
                } else {
                    item {
                        KhabirCard(contentPadding = PaddingValues(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("الدعوى المختارة", style = MaterialTheme.typography.labelLarge)
                                Text("دعوى ${selectedCase.caseNo} لسنة ${selectedCase.caseYear}", fontWeight = FontWeight.Bold)
                                Text(listOf(selectedCase.caseType, selectedCase.court).filter { it.isNotBlank() }.joinToString(" — "))
                                if (selectedCase.receiptDate != null) Text("تاريخ الاستلام: ${selectedCase.receiptDate}", style = MaterialTheme.typography.bodySmall)
                                if (selectedCase.preliminaryJudgmentDate != null) Text("تاريخ الحكم التمهيدي: ${selectedCase.preliminaryJudgmentDate}", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = viewModel::onChangeRegisteredCase, modifier = Modifier.fillMaxWidth()) { Text("اختيار دعوى أخرى للدفعة") }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("اختر المطلوب إخطارهم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("الاسم والصفة والعنوان مسحوبة من الدعوى دون إعادة كتابة.", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { viewModel.onSelectAllParties(selectedCase) }) { Text("تحديد الكل") }
                            if (state.selectedParties.isNotEmpty()) TextButton(onClick = viewModel::onClearPartySelection) { Text("إلغاء التحديد") }
                        }
                        Text("تم اختيار ${state.selectedParties.count { it.case.id == selectedCase.id }} من ${selectedCase.parties.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    items(selectedCase.parties, key = { "party-${selectedCase.id}-${it.id}-${it.orderIndex}" }) { party ->
                        PartyRecipientRow(party = party, checked = state.selectedParties.any { it.case.id == selectedCase.id && it.party.id == party.id }, onToggle = { viewModel.onTogglePartySelection(selectedCase, party) })
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (state.caseSource == NotificationScreenUiState.CaseSource.REGISTERED_CASES) {
                    state.selectedParties.groupBy { it.case.id }.forEach { (caseId, members) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("دعوى ${members.first().case.caseNo} لسنة ${members.first().case.caseYear} — ${members.size} مخاطب", Modifier.weight(1f))
                            TextButton(onClick = { viewModel.onRemoveSelectedCase(caseId) }) { Text("استبعاد") }
                        }
                    }
                }
                state.authorityCandidates.forEach { candidate ->
                    val approved = state.approvedAuthorityNotices[candidate.key]
                    OutlinedButton(onClick = { viewModel.onOpenAuthorityNotice(candidate) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (approved == null) "إضافة إخطار هيئة قضايا الدولة" else "تعديل إخطار هيئة قضايا الدولة") + " — ${candidate.caseNo}/${candidate.caseYear}")
                    }
                    Text("اختياري: لا يغيّر الإخطارات الأصلية ولا يحدد الجهة الإدارية بدلًا منك.", style = MaterialTheme.typography.bodySmall)
                    if (approved != null) TextButton(onClick = { viewModel.onRemoveAuthorityNotice(candidate.key) }) { Text("إلغاء إضافة إخطار الهيئة") }
                }
                Text("بيانات الحضور والإخطار", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KhabirTextField(state.appointmentDate.toString(), {}, readOnly = true, label = { Text("التاريخ") }, modifier = Modifier.weight(1f), trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار التاريخ") } })
                    KhabirTextField(state.appointmentTime, viewModel::onAppointmentTimeChanged, label = { Text("الوقت") }, modifier = Modifier.weight(1f), singleLine = true, trailingIcon = { IconButton(onClick = { startVoice(NotificationVoiceTarget.TIME) }) { Icon(Icons.Filled.Mic, "إملاء الوقت") } })
                }
                Spacer(Modifier.height(8.dp))
                KhabirTextField(state.appointmentLocation, viewModel::onAppointmentLocationChanged, label = { Text("مكان المباشرة") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { IconButton(onClick = { startVoice(NotificationVoiceTarget.LOCATION) }) { Icon(Icons.Filled.Mic, "إملاء مكان المباشرة") } })
                Spacer(Modifier.height(8.dp))
                KhabirTextField(state.requestedDocuments, viewModel::onRequestedDocumentsChanged, label = { Text("المستندات المطلوب تقديمها") }, supportingText = { Text("مثال: مذكرة دفاع - خريطة مساحية - سندات الملكية") }, modifier = Modifier.fillMaxWidth(), minLines = 2, trailingIcon = { IconButton(onClick = { startVoice(NotificationVoiceTarget.DOCUMENTS) }) { Icon(Icons.Filled.Mic, "إملاء المستندات") } })
                val selectedCount = if (state.caseSource == NotificationScreenUiState.CaseSource.NEW_CASE) state.manualRecipients.size else state.selectedParties.size
                Text("$selectedCount طرف مختار — نفس البيانات ستُستخدم في الإخطارات والسركي والأظرف وحافظة البريد.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::onCancelNewBatch, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(onClick = viewModel::onGenerateBatch, enabled = !state.isCreating, modifier = Modifier.weight(1f)) { Text(if (state.isCreating) "جارٍ الإنشاء..." else "إنشاء الإخطارات") }
        }
    }
    state.authorityEditor?.let { draft ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissAuthorityNotice,
            title = { Text("مراجعة إخطار هيئة قضايا الدولة") },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إخطار إضافي مستقل بطول إخطارين. لا يُرسل تلقائيًا ولا يستبدل أي مخاطب.")
                Text("الدعوى ${draft.caseNo} لسنة ${draft.caseYear} — ${draft.court}")
                KhabirTextField(draft.address, { viewModel.onEditAuthorityNotice(draft.copy(address = it)) }, label = { Text("عنوان هيئة قضايا الدولة — تحدده أنت") }, modifier = Modifier.fillMaxWidth())
                KhabirTextField(draft.subject, { viewModel.onEditAuthorityNotice(draft.copy(subject = it)) }, label = { Text("موضوع من الطلبات الختامية — راجع الاقتراح (حتى ١٨٠ حرفًا)") }, modifier = Modifier.fillMaxWidth())
                KhabirTextField(draft.plaintiffs, { viewModel.onEditAuthorityNotice(draft.copy(plaintiffs = it)) }, label = { Text("مرفوعة من") }, modifier = Modifier.fillMaxWidth())
                KhabirTextField(draft.defendants, { viewModel.onEditAuthorityNotice(draft.copy(defendants = it)) }, label = { Text("ضد") }, modifier = Modifier.fillMaxWidth())
                KhabirTextField(draft.judgmentDate, { viewModel.onEditAuthorityNotice(draft.copy(judgmentDate = it)) }, label = { Text("الحكم التمهيدي: YYYY-MM-DD — اختياري") }, modifier = Modifier.fillMaxWidth())
                KhabirTextField(draft.incomingNo, { viewModel.onEditAuthorityNotice(draft.copy(incomingNo = it)) }, label = { Text("رقم الوارد — اختياري") }, modifier = Modifier.fillMaxWidth())
                Text("مباشرة المأمورية: ${state.appointmentDate} — ${state.appointmentTime}\nالمكان: ${state.appointmentLocation}\nوتم إخطاركم لاتخاذ اللازم.")
            } },
            confirmButton = { Button(enabled = draft.valid(), onClick = viewModel::onApproveAuthorityNotice) { Text("اعتماد الإضافة") } },
            dismissButton = { TextButton(onClick = viewModel::onDismissAuthorityNotice) { Text("إلغاء") } }
        )
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.appointmentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { viewModel.onAppointmentDateChanged(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }; showDatePicker = false }) { Text("تأكيد") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }) { DatePicker(datePickerState) }
    }
    state.ocrReviewText?.let {
        OcrReviewDialog(
            ocrReviewDraft = ocrReviewDraft,
            onOcrReviewDraftChange = { ocrReviewDraft = it },
            reviewedParties = reviewedParties,
            onReviewedPartiesChange = { reviewedParties = it },
            onApply = { viewModel.onApplyOcrReview(ocrReviewDraft, reviewedParties) },
            onDismiss = viewModel::onDismissOcrReview
        )
    }
    if (showInAppCamera) {
        InAppCameraCapture(
            onDismiss = { showInAppCamera = false },
            onCaptured = { bitmap ->
                showInAppCamera = false
                viewModel.onPetitionPhotoCaptured(bitmap)
            },
            onGeminiCaptured = { bitmap ->
                showInAppCamera = false
                viewModel.onPetitionPhotoCapturedWithGemini(bitmap)
            },
            onPagesCaptured = { pages, useAi ->
                showInAppCamera = false
                viewModel.onDocumentPagesCaptured(pages, useAi)
            },
            onError = viewModel::onCameraError
        )
    }
    if (showLensCamera) {
        InAppCameraCapture(
            onDismiss = { showLensCamera = false },
            onCaptured = {},
            lensOnly = true,
            onError = viewModel::onCameraError
        )
    }
}

@Composable
private fun NotificationVoiceChoiceDialog(
    onDismiss: () -> Unit,
    onGoogleChosen: () -> Unit,
    onContinuousChosen: () -> Unit,
    onAiChosen: () -> Unit,
    onKeyboardChosen: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("اختر طريقة الإدخال الصوتي") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onGoogleChosen, modifier = Modifier.fillMaxWidth()) { Text("صوت Google — جملة واحدة") }
            TextButton(onClick = onContinuousChosen, modifier = Modifier.fillMaxWidth()) { Text("استماع مستمر حتى «تم»") }
            TextButton(onClick = onAiChosen, modifier = Modifier.fillMaxWidth()) { Text("صوت AI — تنقيح النص بعد التسجيل") }
            TextButton(onClick = onKeyboardChosen, modifier = Modifier.fillMaxWidth()) { Text("لوحة المفاتيح — كتابة أو مايك الكيبورد") }
        }
    }, confirmButton = {})
}

@Composable
private fun OcrReviewDialog(
    ocrReviewDraft: String,
    onOcrReviewDraftChange: (String) -> Unit,
    reviewedParties: List<PetitionIntakeParser.ParsedParty>,
    onReviewedPartiesChange: (List<PetitionIntakeParser.ParsedParty>) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val parsedPreview = remember(ocrReviewDraft) { PetitionIntakeParser.parse(ocrReviewDraft) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مراجعة الخصوم المستخرجين") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("راجع كل اسم وجهة الخصم و«بصفته» والعنوان. لن تُحفظ البيانات قبل الضغط على «اعتماد وحفظ الكل».")
                KhabirTextField(
                    value = ocrReviewDraft,
                    onValueChange = onOcrReviewDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("النص المقروء من العريضة أو الحكم") }
                )
                OutlinedButton(
                    onClick = { onReviewedPartiesChange(PetitionIntakeParser.parse(ocrReviewDraft).parties) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إعادة تحليل النص") }
                val caseSummary = listOfNotNull(
                    parsedPreview.caseNo?.let { "رقم الدعوى: $it" },
                    parsedPreview.caseYear?.let { "السنة: $it" },
                    parsedPreview.court?.let { "المحكمة: $it" }
                ).joinToString(" • ")
                if (caseSummary.isNotBlank()) Text(caseSummary, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("الخصوم المستخرجون (${reviewedParties.size})", style = MaterialTheme.typography.titleSmall)
                if (reviewedParties.isEmpty()) {
                    Text("لم تظهر أسماء واضحة. عدّل النص ثم اضغط «إعادة تحليل النص».", style = MaterialTheme.typography.bodySmall)
                }
                reviewedParties.forEachIndexed { index, party ->
                    KhabirCard(contentPadding = PaddingValues(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            KhabirTextField(
                                value = party.name,
                                onValueChange = { value ->
                                    onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(name = value) })
                                },
                                label = { Text("اسم الخصم ${index + 1}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            KhabirTextField(
                                value = if (party.role == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.city(party.address) else party.address,
                                onValueChange = { value ->
                                    onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(address = value) })
                                },
                                label = { Text(if (party.role == PartyRole.LAWYER) "مدينة المحامي" else "العنوان") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PartyRole.entries.forEach { role ->
                                    FilterChip(
                                        selected = party.role == role,
                                        onClick = {
                                            onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(role = role) })
                                        },
                                        label = { Text(role.arabicLabel) }
                                    )
                                }
                            }
                            FilterChip(
                                selected = party.withCapacity,
                                onClick = {
                                    onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(withCapacity = !party.withCapacity) })
                                },
                                label = { Text("بصفته") }
                            )
                            TextButton(onClick = { onReviewedPartiesChange(reviewedParties.filterIndexed { i, _ -> i != index }) }) {
                                Text("حذف هذا الاسم")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply) {
                Text("اعتماد وحفظ الكل")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun RegisteredCaseCard(case: Case, onSelect: () -> Unit) {
    KhabirCard(onClick = onSelect, contentPadding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("دعوى ${case.caseNo} لسنة ${case.caseYear}", fontWeight = FontWeight.Bold)
            Text(listOf(case.caseType, case.court).filter { it.isNotBlank() }.joinToString(" — "), style = MaterialTheme.typography.bodyMedium)
            val firstParty = case.parties.minByOrNull { it.orderIndex }
            if (firstParty != null) Text("${firstParty.reportDisplayName}${if (case.parties.size > 1) " وآخرين" else ""}", style = MaterialTheme.typography.bodySmall)
            Text("اضغط لاختيار الدعوى", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PartyRecipientRow(party: Party, checked: Boolean, onToggle: () -> Unit) {
    KhabirCard(contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(party.reportDisplayName, fontWeight = FontWeight.Bold)
                Text(party.role.arabicLabel, style = MaterialTheme.typography.bodySmall)
                if (party.address.isNotBlank()) Text(party.address, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualRecipientCard(
    state: NotificationScreenUiState,
    viewModel: NotificationBatchViewModel,
    onVoice: (NotificationVoiceTarget) -> Unit
) {
    var roleMenuExpanded by remember { mutableStateOf(false) }
    KhabirCard(contentPadding = PaddingValues(12.dp)) {
        Column {
            Text("دعوى جديدة", style = MaterialTheme.typography.titleSmall)
            Text("الخانات فارغة لأن هذه الدعوى غير موجودة في سجل القضايا.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(state.manualCaseNo, viewModel::onManualCaseNoChanged, label = { Text("رقم الدعوى") }, modifier = Modifier.weight(1f), singleLine = true)
                KhabirTextField(state.manualCaseYear, viewModel::onManualCaseYearChanged, label = { Text("السنة") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            KhabirTextField(state.manualCourt, viewModel::onManualCourtChanged, label = { Text("المحكمة") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { IconButton(onClick = { onVoice(NotificationVoiceTarget.COURT) }) { Icon(Icons.Filled.Mic, "إملاء المحكمة") } })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(state.manualFirstName, viewModel::onManualFirstNameChanged, label = { Text("الاسم الأول") }, modifier = Modifier.weight(1f), singleLine = true, trailingIcon = { IconButton(onClick = { onVoice(NotificationVoiceTarget.FIRST_NAME) }) { Icon(Icons.Filled.Mic, "إملاء الاسم الأول") } })
                KhabirTextField(state.manualRestName, viewModel::onManualRestNameChanged, label = { Text("باقي الاسم") }, modifier = Modifier.weight(1f), singleLine = true, trailingIcon = { IconButton(onClick = { onVoice(NotificationVoiceTarget.REST_NAME) }) { Icon(Icons.Filled.Mic, "إملاء باقي الاسم") } })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(expanded = roleMenuExpanded, onExpandedChange = { roleMenuExpanded = it }, modifier = Modifier.weight(1f)) {
                    KhabirTextField(state.manualRole.arabicLabel, {}, readOnly = true, label = { Text("جهة الخصم") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        PartyRole.entries.forEach { role -> DropdownMenuItem(text = { Text(role.arabicLabel) }, onClick = { viewModel.onManualRoleChanged(role); roleMenuExpanded = false }) }
                    }
                }
                FilterChip(
                    selected = state.manualWithCapacity,
                    onClick = { viewModel.onManualWithCapacityChanged(!state.manualWithCapacity) },
                    label = { Text("بصفته") }
                )
            }
            Spacer(Modifier.height(8.dp))
            KhabirTextField(state.manualAddress, viewModel::onManualAddressChanged, label = { Text(if (state.manualRole == PartyRole.LAWYER) "مدينة المحامي" else "العنوان") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { onVoice(NotificationVoiceTarget.ADDRESS) }) { Icon(Icons.Filled.Mic, "إملاء العنوان") } })
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = viewModel::onAddManualRecipient, modifier = Modifier.fillMaxWidth()) { Text("حفظ الطرف وإضافة طرف آخر") }
            if (state.manualRecipients.isNotEmpty()) {
                Text(
                    "تم حفظ الطرف. مُسح الاسم الأول فقط؛ باقي الاسم والصفة والعنوان كما هي، ويمكن تعديلها يدويًا للطرف التالي.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            state.manualRecipients.forEachIndexed { index, recipient ->
                val name = listOf(recipient.firstName, recipient.restName).filter { it.isNotBlank() }.joinToString(" ") + if (recipient.withCapacity) " بصفته" else ""
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("$name — دعوى ${state.manualCaseNo}/${state.manualCaseYear}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { viewModel.onRemoveManualRecipient(index) }) { Icon(Icons.Filled.Delete, "حذف الطرف") }
                }
            }
        }
    }
}


private fun copyNotificationImageToCache(context: android.content.Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val destination = File.createTempFile("khabir_notification_import_", ".jpg", directory)
    context.contentResolver.openInputStream(uri)?.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    } ?: error("تعذر فتح الصورة")
    destination
}.getOrNull()
