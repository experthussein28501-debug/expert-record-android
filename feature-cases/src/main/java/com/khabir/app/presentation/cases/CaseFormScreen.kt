package com.khabir.app.presentation.cases

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.presentation.common.InAppCameraCapture
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirTextField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseFormScreen(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit,
    onOpenWorkMinutes: (Long) -> Unit = {},
    showReportAction: Boolean = true,
    viewModel: CaseFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    state.partyConflicts.firstOrNull()?.let { (old, incoming) ->
        AlertDialog(onDismissRequest = {}, title = { Text("مراجعة عنوان الاسم المتكرر") },
            text = { Column { Text("${old.firstName} ${old.restName}"); Text("المحفوظ: ${old.address}"); Text("المستخرج: ${incoming.address}") } },
            confirmButton = { Column {
                TextButton(onClick = { viewModel.resolvePartyConflict(1) }) { Text("استخدام العنوان المستخرج") }
                TextButton(onClick = { viewModel.resolvePartyConflict(2) }) { Text("شخصان مختلفان — احتفظ بالاثنين") }
            } }, dismissButton = { TextButton(onClick = { viewModel.resolvePartyConflict(0) }) { Text("احتفظ بالمحفوظ") } })
    }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var voiceReviewDraft by remember { mutableStateOf("") }
    var reviewedParties by remember { mutableStateOf<List<PetitionIntakeParser.ParsedParty>>(emptyList()) }
    var showIncomingDatePicker by remember { mutableStateOf(false) }
    var showReceiptDatePicker by remember { mutableStateOf(false) }
    var showJudgmentDatePicker by remember { mutableStateOf(false) }
    var caseTypeMenuExpanded by remember { mutableStateOf(false) }
    var showInAppCamera by remember { mutableStateOf(false) }
    var showLensCamera by remember { mutableStateOf(false) }
    var pendingLensCamera by remember { mutableStateOf(false) }
    var showVoiceChoice by remember { mutableStateOf(false) }
    var showContinuousDictation by remember { mutableStateOf(false) }
    var useAiVoice by remember { mutableStateOf(false) }
    var pendingGoogleSpeech by remember { mutableStateOf(false) }
    com.khabir.app.data.monetization.BlockWorkAds(showInAppCamera || showLensCamera || state.isOcrProcessing || state.isSaving || state.pendingPagePaths.isNotEmpty() || state.documentReview.isNotEmpty() || showContinuousDictation)
    val caseTypeOptions = listOf("مدني كلي", "مدني جزئي", "مدني مستأنف", "جنح", "أحوال شخصية", "شؤون الأسرة", "استئناف عالي", "قضاء إداري", "تنفيذ", "أخرى")

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (pendingLensCamera) showLensCamera = true else showInAppCamera = true
            pendingLensCamera = false
        }
        else scope.launch { snackbar.showSnackbar("يلزم السماح بالكاميرا لتصوير العريضة أو الحكم") }
    }

    val importImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val available = (10 - state.pendingPagePaths.size).coerceAtLeast(0)
        val files = uris.take(available).mapNotNull { uri -> copyImportedImageToCache(context, uri) }
        if (files.isNotEmpty()) viewModel.onDocumentPagesAdded(files)
        else if (uris.isNotEmpty()) scope.launch { snackbar.showSnackbar("تعذر استيراد الصور المختارة أو تم الوصول إلى 10 صور") }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                if (useAiVoice) viewModel.onVoiceRecognitionResult(text)
                else viewModel.onVoiceRecognitionResultWithoutAi(text)
            }
        }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingGoogleSpeech) {
            pendingGoogleSpeech = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "امْلِ بيانات القضية ثم راجع النص")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
            }
            runCatching { speechLauncher.launch(intent) }.onFailure { scope.launch { snackbar.showSnackbar("خدمة التعرف على الصوت غير متاحة على هذا الجهاز") } }
        } else if (!granted) {
            pendingGoogleSpeech = false
            scope.launch { snackbar.showSnackbar("يلزم السماح بالميكروفون للإدخال الصوتي") }
        }
    }

    fun launchCaseSpeech() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingGoogleSpeech = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "امْلِ بيانات القضية ثم راجع النص")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600000L)
        }
        runCatching { speechLauncher.launch(intent) }.onFailure { scope.launch { snackbar.showSnackbar("خدمة التعرف على الصوت غير متاحة على هذا الجهاز") } }
    }

    LaunchedEffect(state.voiceReviewText) {
        state.voiceReviewText?.let { text ->
            voiceReviewDraft = text
            reviewedParties = PetitionIntakeParser.parse(text).parties
        }
    }
    LaunchedEffect(state.voiceAppliedMessage) {
        state.voiceAppliedMessage?.let { snackbar.showSnackbar(it); viewModel.clearVoiceMessage() }
    }
    LaunchedEffect(state.ocrMessage) {
        state.ocrMessage?.let { snackbar.showSnackbar(it); viewModel.clearOcrMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(if (state.caseId == 0L) "تسجيل قضية" else "تعديل القضية المسجلة") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).imePadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KhabirCard(
                contentPadding = PaddingValues(14.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("استيراد بيانات القضية والخصوم", style = MaterialTheme.typography.titleMedium)
                    com.khabir.app.presentation.components.InlineHelp("مساعدة", "أضف حتى 10 صور. يفحص التطبيق الصور ويجمع صفحات كل مستند ثم يعزل أي مستند مختلف قبل تعبئة القضية.")
                    FilledTonalButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                showInAppCamera = true
                            } else {
                                pendingLensCamera = false
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !state.isOcrProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isOcrProcessing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isOcrProcessing) "جارٍ فحص الصور..." else "تصوير بالكاميرا")
                    }
                    OutlinedButton(
                        onClick = { importImagesLauncher.launch(arrayOf("image/*")) },
                        enabled = !state.isOcrProcessing && state.pendingPagePaths.size < 10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("استيراد صور من الجهاز")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    showLensCamera = true
                                } else {
                                    pendingLensCamera = true
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
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
                                if (state.pendingPagePaths.isNotEmpty()) {
                                    viewModel.analyzePendingDocumentsWithAi()
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    showInAppCamera = true
                                } else {
                                    pendingLensCamera = false
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !state.isOcrProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Spacer(Modifier.width(6.dp))
                            Text("AI Vision")
                        }
                    }
                    Text(
                        if (state.pendingPagePaths.isEmpty())
                            "AI Vision: صوّر من 1 إلى 10 صفحات أو استورد صورًا، ثم راجع النتيجة قبل الاعتماد."
                        else
                            "AI Vision جاهز لتحليل ${state.pendingPagePaths.size} صورة كمجموعة واحدة.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (state.pendingPagePaths.isNotEmpty()) {
                        HorizontalDivider()
                        Text("الصور المضافة: ${state.pendingPagePaths.size} من 10", style = MaterialTheme.typography.titleSmall)
                        state.pendingPagePaths.forEachIndexed { index, path ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TemporaryPagePreview(path, Modifier.size(64.dp))
                                Text("الصورة ${index + 1}", modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.movePendingPage(index, -1) }, enabled = index > 0) {
                                    Icon(Icons.Filled.ArrowUpward, "تحريك لأعلى")
                                }
                                IconButton(onClick = { viewModel.movePendingPage(index, 1) }, enabled = index < state.pendingPagePaths.lastIndex) {
                                    Icon(Icons.Filled.ArrowDownward, "تحريك لأسفل")
                                }
                                IconButton(onClick = { viewModel.removePendingPage(index) }) {
                                    Icon(Icons.Filled.Delete, "حذف الصورة")
                                }
                            }
                        }
                        state.aiFailureMessage?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(
                                onClick = viewModel::analyzePendingDocumentsLocally,
                                enabled = !state.isOcrProcessing,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("استخدام OCR المحلي بدلًا منه") }
                        }
                        TextButton(onClick = viewModel::clearPendingDocuments, modifier = Modifier.fillMaxWidth()) {
                            Text("حذف كل الصور المؤقتة")
                        }
                    }
                    OutlinedButton(
                        onClick = { showVoiceChoice = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Mic, null)
                        Spacer(Modifier.width(6.dp))
                        Text("إملاء بيانات القضية للمراجعة")
                    }
                }
            }

            Text("البيانات الأساسية للقضية", style = MaterialTheme.typography.titleLarge)
            com.khabir.app.presentation.components.InlineHelp("مساعدة", "هذه هي البيانات الأصلية التي يسحب منها التطبيق التقرير والإخطارات لاحقًا.")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(state.incomingNo, viewModel::onIncomingNoChanged, label = { Text("رقم الوارد") }, modifier = Modifier.weight(1f))
                KhabirTextField(
                    state.incomingDate.toString(), {}, readOnly = true, label = { Text("تاريخ الإحالة / الوارد") }, modifier = Modifier.weight(1f),
                    trailingIcon = { IconButton(onClick = { showIncomingDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار تاريخ الإحالة أو الوارد") } }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(state.caseNo, viewModel::onCaseNoChanged, label = { Text("رقم الدعوى") }, modifier = Modifier.weight(1f))
                KhabirTextField(
                    state.caseYear,
                    viewModel::onCaseYearChanged,
                    label = { Text(if (state.isJudicialYearType()) "السنة القضائية (ق)" else "السنة") },
                    placeholder = { Text(if (state.isJudicialYearType()) "مثال: 21ق" else "مثال: 2026") },
                    modifier = Modifier.weight(1f)
                )
            }
            KhabirTextField(state.court, viewModel::onCourtChanged, label = { Text("المحكمة / المأمورية") }, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = caseTypeMenuExpanded, onExpandedChange = { caseTypeMenuExpanded = it }) {
                KhabirTextField(
                    value = state.caseType,
                    onValueChange = viewModel::onCaseTypeChanged,
                    label = { Text("نوع الدعوى") },
                    placeholder = { Text("مدني كلي / جزئي / جنح / أحوال / استئناف...") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = caseTypeMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = caseTypeMenuExpanded, onDismissRequest = { caseTypeMenuExpanded = false }) {
                    caseTypeOptions.forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = {
                            viewModel.onCaseTypeChanged(if (type == "أخرى") "" else type)
                            caseTypeMenuExpanded = false
                        })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhabirTextField(
                    value = state.receiptDate?.toString().orEmpty(), onValueChange = {}, readOnly = true,
                    label = { Text("تاريخ استلام القضية") }, placeholder = { Text("اختياري") }, modifier = Modifier.weight(1f),
                    trailingIcon = { IconButton(onClick = { showReceiptDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار تاريخ استلام القضية") } }
                )
                KhabirTextField(
                    value = state.preliminaryJudgmentDate?.toString().orEmpty(), onValueChange = {}, readOnly = true,
                    label = { Text("تاريخ الحكم التمهيدي") }, placeholder = { Text("اختياري") }, modifier = Modifier.weight(1f),
                    trailingIcon = { IconButton(onClick = { showJudgmentDatePicker = true }) { Icon(Icons.Filled.CalendarMonth, "اختيار تاريخ الحكم التمهيدي") } }
                )
            }

            KhabirTextField(
                value = state.subjectOfCase,
                onValueChange = viewModel::onSubjectOfCaseChanged,
                label = { Text("موضوع الدعوى المستخرج") },
                supportingText = { Text("يُنقل تلقائيًا إلى بند الموضوع في التقرير") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            KhabirTextField(
                value = state.finalRequests,
                onValueChange = viewModel::onFinalRequestsChanged,
                label = { Text("الطلبات الختامية") },
                supportingText = { Text("تُستخرج من خاتمة العريضة وتظل قابلة للتعديل قبل الحفظ") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            KhabirTextField(
                value = state.preliminaryMission,
                onValueChange = viewModel::onPreliminaryMissionChanged,
                label = { Text("مأمورية الحكم التمهيدي") },
                supportingText = { Text("تُنقل تلقائيًا إلى بند المأمورية في التقرير") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("أسماء الخصوم وعناوينهم", style = MaterialTheme.typography.titleMedium)
            com.khabir.app.presentation.components.InlineHelp("مساعدة", "اختر جهة الخصم: مدعي أو مدعى عليه، ثم فعّل «بصفته» للشخص عند الحاجة. كل اسم وعنوان يبقى محفوظًا للإخطارات.")

            if (state.parties.isNotEmpty()) {
                KhabirCard(contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الأسماء المحفوظة (${state.parties.size})", style = MaterialTheme.typography.labelLarge)
                        state.parties.forEach { party ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        KhabirTextField(party.firstName, { value -> viewModel.updateParty(party.localId) { it.copy(firstName = value) } }, label = { Text("الاسم الأول") }, modifier = Modifier.weight(1f))
                                        KhabirTextField(party.restName, { value -> viewModel.updateParty(party.localId) { it.copy(restName = value) } }, label = { Text("باقي الاسم") }, modifier = Modifier.weight(1f))
                                    }
                                    KhabirTextField(if (party.role == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.city(party.address) else party.address, { value -> viewModel.updateParty(party.localId) { it.copy(address = value) } }, label = { Text(if (party.role == PartyRole.LAWYER) "مدينة المحامي" else "العنوان") }, modifier = Modifier.fillMaxWidth())
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(PartyRole.PLAINTIFF, PartyRole.DEFENDANT, PartyRole.LAWYER).forEach { role ->
                                            FilterChip(selected = party.role == role, onClick = { viewModel.updateParty(party.localId) { it.copy(role = role) } }, label = { Text(role.arabicLabel) })
                                        }
                                        FilterChip(selected = party.withCapacity, onClick = { viewModel.updateParty(party.localId) { it.copy(withCapacity = !it.withCapacity) } }, label = { Text("بصفته") })
                                    }
                                    KhabirTextField(party.claimKind, { value -> viewModel.updateParty(party.localId) { it.copy(claimKind = value) } }, label = { Text("الدعوى: أصلية / فرعية") }, modifier = Modifier.fillMaxWidth())
                                }
                                IconButton(onClick = { viewModel.removeParty(party.localId) }) {
                                    Icon(Icons.Filled.Delete, "حذف الاسم")
                                }
                            }
                        }
                    }
                }
            }

            var partyRoleMenuExpanded by remember { mutableStateOf(false) }
            KhabirCard(contentPadding = PaddingValues(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إضافة اسم", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KhabirTextField(
                            state.partyEntry.firstName,
                            { value -> viewModel.updatePartyEntry { it.copy(firstName = value) } },
                            label = { Text("الاسم الأول") },
                            modifier = Modifier.weight(1f)
                        )
                        KhabirTextField(
                            state.partyEntry.restName,
                            { value -> viewModel.updatePartyEntry { it.copy(restName = value) } },
                            label = { Text("باقي الاسم") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = partyRoleMenuExpanded,
                            onExpandedChange = { partyRoleMenuExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            KhabirTextField(
                                value = state.partyEntry.role.arabicLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("جهة الخصم") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyRoleMenuExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = partyRoleMenuExpanded, onDismissRequest = { partyRoleMenuExpanded = false }) {
                                PartyRole.entries.forEach { role ->
                                    DropdownMenuItem(text = { Text(role.arabicLabel) }, onClick = {
                                        viewModel.updatePartyEntry { it.copy(role = role, address = if (it.role != role && (it.role == PartyRole.LAWYER || role == PartyRole.LAWYER)) "" else it.address) }
                                        partyRoleMenuExpanded = false
                                    })
                                }
                            }
                        }
                        FilterChip(
                            selected = state.partyEntry.withCapacity,
                            onClick = { viewModel.updatePartyEntry { it.copy(withCapacity = !it.withCapacity) } },
                            label = { Text("بصفته") },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    KhabirTextField(
                        state.partyEntry.address,
                        { value -> viewModel.updatePartyEntry { it.copy(address = value) } },
                        label = { Text(if (state.partyEntry.role == PartyRole.LAWYER) "مدينة المحامي" else "العنوان") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilledTonalButton(onClick = viewModel::savePartyEntry, modifier = Modifier.fillMaxWidth()) {
                        Text("حفظ الاسم وإضافة اسم آخر")
                    }
                    com.khabir.app.presentation.components.InlineHelp("مساعدة", "بعد الحفظ يُمسح الاسم الأول فقط، وتبقى بقية الاسم والجهة و«بصفته» والعنوان للطرف التالي.")
                }
            }
            KhabirTextField(state.adminNotes, viewModel::onAdminNotesChanged, label = { Text("ملاحظات إدارية") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (state.validationErrors.isNotEmpty()) Text("أكمل رقم الدعوى والسنة والمحكمة", color = MaterialTheme.colorScheme.error)
            Button(onClick = { viewModel.onSave {} }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text(if (state.isSaving) "جارٍ الحفظ..." else "حفظ القضية في سجل القضايا") }
            if (showReportAction && state.caseId != 0L) OutlinedButton(onClick = { onOpenReport(state.caseId) }, modifier = Modifier.fillMaxWidth()) { Text("فتح تقرير من بيانات القضية") }
            if (state.caseId != 0L) OutlinedButton(onClick = { onOpenWorkMinutes(state.caseId) }, modifier = Modifier.fillMaxWidth()) { Text("محاضر الأعمال") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showIncomingDatePicker) {
        CaseDatePicker(state.incomingDate.toEpochDay(), { showIncomingDatePicker = false }) { viewModel.onIncomingDateChanged(it); showIncomingDatePicker = false }
    }
    if (showReceiptDatePicker) {
        CaseDatePicker(state.receiptDate?.toEpochDay(), { showReceiptDatePicker = false }) { viewModel.onReceiptDateChanged(it); showReceiptDatePicker = false }
    }
    if (showJudgmentDatePicker) {
        CaseDatePicker(state.preliminaryJudgmentDate?.toEpochDay(), { showJudgmentDatePicker = false }) { viewModel.onPreliminaryJudgmentDateChanged(it); showJudgmentDatePicker = false }
    }

    if (showVoiceChoice) {
        CaseVoiceChoiceDialog(
            onDismiss = { showVoiceChoice = false },
            onGoogleChosen = { useAiVoice = false; showVoiceChoice = false; launchCaseSpeech() },
            onContinuousChosen = { showVoiceChoice = false; showContinuousDictation = true },
            onAiChosen = { useAiVoice = true; showVoiceChoice = false; launchCaseSpeech() },
            onKeyboardChosen = { showVoiceChoice = false }
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
                if (text.isNotBlank()) viewModel.onVoiceRecognitionResult(text)
            }
        )
    }
    state.voiceReviewText?.let {
        VoiceReviewDialog(
            reviewSourceLabel = state.reviewSourceLabel,
            voiceReviewDraft = voiceReviewDraft,
            onVoiceReviewDraftChange = { voiceReviewDraft = it },
            reviewedParties = reviewedParties,
            onReviewedPartiesChange = { reviewedParties = it },
            onApply = { viewModel.applyVoiceReview(voiceReviewDraft, reviewedParties) },
            onCancel = viewModel::cancelVoiceReview
        )
    }

    if (state.documentReview.isNotEmpty() && state.voiceReviewText == null && state.selectedDocumentIds.isEmpty()) {
        DocumentGroupsReviewDialog(
            documents = state.documentReview,
            pagePaths = state.pendingPagePaths,
            onEdit = viewModel::editReviewedDocument,
            onUse = viewModel::useReviewedDocument,
            onExclude = viewModel::excludeReviewedDocument,
            onBack = viewModel::dismissDocumentReview
        )
    }

    if (showInAppCamera) {
        InAppCameraCapture(
            onDismiss = { showInAppCamera = false },
            onCaptured = { bitmap ->
                showInAppCamera = false
                viewModel.onPetitionPhotoCaptured(bitmap)
            },
            onPagesSelected = { pages ->
                showInAppCamera = false
                viewModel.onDocumentPagesAdded(pages)
            },
            onPagesAnalyze = { pages ->
                showInAppCamera = false
                viewModel.onDocumentPagesAdded(pages)
                viewModel.analyzePendingDocumentsWithAi()
            },
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } }
        )
    }

    if (showLensCamera) {
        InAppCameraCapture(
            onDismiss = { showLensCamera = false },
            onCaptured = {},
            lensOnly = true,
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } }
        )
    }

}

@Composable
private fun CaseVoiceChoiceDialog(
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
            TextButton(onClick = onAiChosen, modifier = Modifier.fillMaxWidth()) { Text("صوت AI — تنقيح بعد التسجيل") }
            TextButton(onClick = onKeyboardChosen, modifier = Modifier.fillMaxWidth()) { Text("لوحة المفاتيح — إدخال يدوي") }
        }
    }, confirmButton = {})
}

@Composable
private fun VoiceReviewDialog(
    reviewSourceLabel: String,
    voiceReviewDraft: String,
    onVoiceReviewDraftChange: (String) -> Unit,
    reviewedParties: List<PetitionIntakeParser.ParsedParty>,
    onReviewedPartiesChange: (List<PetitionIntakeParser.ParsedParty>) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val parsedPreview = remember(voiceReviewDraft) { PetitionIntakeParser.parse(voiceReviewDraft) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("مراجعة $reviewSourceLabel") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("راجع البيانات قبل اعتمادها. يمكنك تعديل النص، ثم إعادة التحليل، كما يمكنك تعديل كل اسم وعنوان وجهة الخصم و«بصفته» منفصلًا.")
                KhabirTextField(
                    value = voiceReviewDraft,
                    onValueChange = onVoiceReviewDraftChange,
                    label = { Text("النص المستخرج") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onReviewedPartiesChange(PetitionIntakeParser.parse(voiceReviewDraft).parties) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إعادة تحليل النص") }

                val basicSummary = listOfNotNull(
                    parsedPreview.caseNo?.let { "رقم الدعوى: $it" },
                    parsedPreview.caseYear?.let { "السنة: $it" },
                    parsedPreview.caseType?.let { "النوع: $it" },
                    parsedPreview.court?.let { "المحكمة: $it" }
                ).joinToString(" • ")
                if (basicSummary.isNotBlank()) {
                    Text(basicSummary, style = MaterialTheme.typography.bodySmall)
                }
                parsedPreview.subjectOfCase?.let { Text("موضوع الدعوى: $it", style = MaterialTheme.typography.bodySmall) }
                parsedPreview.finalRequests?.let { Text("الطلبات الختامية: $it", style = MaterialTheme.typography.bodySmall) }
                parsedPreview.preliminaryMission?.let { Text("المأمورية: $it", style = MaterialTheme.typography.bodySmall) }

                HorizontalDivider()
                Text("الخصوم المستخرجون", style = MaterialTheme.typography.titleSmall)
                if (reviewedParties.isEmpty()) {
                    com.khabir.app.presentation.components.InlineHelp("مساعدة", "لم يتم التعرف على أسماء خصوم. يمكنك تعديل النص ثم الضغط على «إعادة تحليل النص».")
                }
                reviewedParties.forEachIndexed { index, party ->
                    KhabirCard(contentPadding = PaddingValues(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KhabirTextField(
                                value = party.name,
                                onValueChange = { value ->
                                    onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(name = value) })
                                },
                                label = { Text("اسم الخصم ${index + 1}") },
                                modifier = Modifier.weight(1f)
                            )
                            KhabirTextField(
                                value = party.address,
                                onValueChange = { value ->
                                    onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(address = value) })
                                },
                                label = { Text("العنوان") },
                                modifier = Modifier.weight(1f)
                            )
                            }
                            KhabirTextField(
                                value = party.claimKind,
                                onValueChange = { value -> onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(claimKind = value) }) },
                                label = { Text("الدعوى: أصلية / فرعية") }, modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(PartyRole.PLAINTIFF, PartyRole.DEFENDANT, PartyRole.LAWYER).forEach { role ->
                                    FilterChip(
                                        selected = party.role == role,
                                        onClick = {
                                            onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(role = role) })
                                        },
                                        label = { Text(role.arabicLabel) }
                                    )
                                }
                                FilterChip(
                                    selected = party.withCapacity,
                                    onClick = {
                                        onReviewedPartiesChange(reviewedParties.toMutableList().also { list -> list[index] = party.copy(withCapacity = !party.withCapacity) })
                                    },
                                    label = { Text("بصفته") }
                                )
                            }
                            TextButton(
                                onClick = { onReviewedPartiesChange(reviewedParties.filterIndexed { i, _ -> i != index }) }
                            ) { Text("حذف هذا الاسم") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply) {
                Text("اعتماد البيانات ومتابعة الحفظ")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

@Composable
private fun DocumentGroupsReviewDialog(
    documents: List<ReviewedDocument>,
    pagePaths: List<String>,
    onEdit: (Int, String) -> Unit,
    onUse: (Int) -> Unit,
    onExclude: (Int) -> Unit,
    onBack: () -> Unit
) {
    var editingId by remember { mutableStateOf<Int?>(null) }
    var editedText by remember { mutableStateOf("") }
    if (editingId != null) {
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text("تعديل بيانات المستند وإضافة رقم الدعوى") },
            text = { KhabirTextField(editedText, { editedText = it },
                modifier = Modifier.heightIn(max = 480.dp).imePadding(),
                label = { Text("بيانات المستند") }) },
            confirmButton = { TextButton(onClick = { editingId?.let { onEdit(it, editedText) }; editingId = null }) { Text("اعتماد التعديل") } },
            dismissButton = { TextButton(onClick = { editingId = null }) { Text("رجوع") } }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("مراجعة المستندات المكتشفة") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("تم تقسيم الصور إلى ${documents.size} مستند. راجع سبب المطابقة قبل اختيار المستند المستخدم في القضية.")
                documents.forEach { document ->
                    KhabirCard(contentPadding = PaddingValues(10.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("المستند ${document.id}: ${document.type}", style = MaterialTheme.typography.titleSmall)
                            Text("الصفحات: ${document.pageNumbers.joinToString("، ")}")
                            document.pageNumbers.forEach { page ->
                                pagePaths.getOrNull(page - 1)?.let { TemporaryPagePreview(it, Modifier.fillMaxWidth().height(180.dp)) }
                            }
                            TextButton(onClick = { editingId = document.id; editedText = document.rawText }) { Text("تعديل البيانات / إضافة رقم الدعوى") }
                            Text("الحالة: ${document.status.arabicLabel}", color = when (document.status) {
                                DocumentMatchStatus.MATCHED -> MaterialTheme.colorScheme.primary
                                DocumentMatchStatus.DIFFERENT -> MaterialTheme.colorScheme.error
                                DocumentMatchStatus.UNCERTAIN -> MaterialTheme.colorScheme.tertiary
                            })
                            Text("السبب: ${document.reason}", style = MaterialTheme.typography.bodySmall)
                            if (document.caseNo.isNullOrBlank()) Text("رقم الدعوى غير موجود؛ يمكنك إضافته أثناء المراجعة أو استبعاد المستند")
                            Text("الخصوم: ${document.primaryPartyNames.joinToString("، ")}")
                            listOfNotNull(
                                document.caseNo?.let { "رقم الدعوى: $it" },
                                document.caseYear?.let { "السنة: $it" },
                                document.court?.let { "المحكمة: $it" }
                            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { onUse(document.id) }, modifier = Modifier.weight(1f)) {
                                    Text("مراجعة هذه القضية ومستنداتها المطابقة")
                                }
                                OutlinedButton(onClick = { onExclude(document.id) }, modifier = Modifier.weight(1f)) {
                                    Text("استبعاد")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onBack) { Text("رجوع للصور") } }
    )
}

private fun copyImportedImageToCache(context: android.content.Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val destination = File.createTempFile("khabir_import_", ".jpg", directory)
    context.contentResolver.openInputStream(uri)?.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    } ?: error("تعذر فتح الصورة")
    destination
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaseDatePicker(initialEpochDay: Long?, onDismiss: () -> Unit, onSelected: (java.time.LocalDate) -> Unit) {
    val initialMillis = initialEpochDay?.let { java.time.LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
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

@Composable
private fun TemporaryPagePreview(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "معاينة صفحة المستند", modifier) }
}


private fun CaseFormUiState.isJudicialYearType(): Boolean {
    val type = caseType.replace("أ", "ا")
    val courtName = court.replace("أ", "ا")
    return type.contains("استئناف عالي") || type.contains("قضاء اداري") ||
        courtName.contains("استئناف عالي") || courtName.contains("قضاء اداري")
}
