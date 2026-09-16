package com.khabir.app.presentation.workminutes

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.domain.model.WorkMinutesEntry
import com.khabir.app.domain.model.WorkMinutesPhrases
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirPrimaryButton
import com.khabir.app.presentation.components.KhabirTextField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkMinutesScreen(onBack: () -> Unit, viewModel: WorkMinutesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(state.exportedFileUri) {
        val uri = state.exportedFileUri ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
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
                    onRemove = { viewModel.onRemoveEntry(entry.number) }
                )
            }

            Spacer(Modifier.height(8.dp))
            KhabirPrimaryButton(
                text = if (state.isExporting) "جارٍ إنشاء Word..." else "تصدير محاضر الأعمال إلى Word",
                onClick = viewModel::onExport,
                enabled = !state.isExporting && state.entries.isNotEmpty()
            )
            if (state.autoSaveStatus.isNotBlank()) {
                Text(state.autoSaveStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkMinutesEntryCard(
    entry: WorkMinutesEntry,
    isExpanded: Boolean,
    expandedHeight: Dp,
    onExpand: () -> Unit,
    onChange: ((WorkMinutesEntry) -> WorkMinutesEntry) -> Unit,
    onRemove: () -> Unit
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

    KhabirCard(
        containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = if (isExpanded) Modifier.fillMaxWidth().height(expandedHeight) else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("محضر اعمال رقم (${entry.number})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkMinutesPhrases.quickPhrases.forEach { (label, phrase) ->
                    AssistChip(onClick = { onChange { e -> e.copy(bodyText = phrase) } }, label = { Text(label) })
                }
            }
            KhabirTextField(
                value = entry.bodyText,
                onValueChange = { onChange { e -> e.copy(bodyText = it) } },
                modifier = Modifier
                    .onFocusChanged { if (it.isFocused) onExpand() }
                    .let { if (isExpanded) it.weight(1f) else it },
                label = { Text("نص المحضر") },
                placeholder = { Text("لإثبات ...") },
                minLines = 3,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4
            )
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
                picker.selectedDateMillis?.let { millis ->
                    onSelected(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
                }
            }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) { DatePicker(picker) }
}
