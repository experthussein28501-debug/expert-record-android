package com.khabir.app.presentation.cases

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseListScreen(
    onBack: () -> Unit,
    onOpenCase: (Long) -> Unit,
    onNewCase: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenExpertProfile: () -> Unit,
    onOpenRegisters: () -> Unit,
    onOpenWorkMinutes: () -> Unit = {},
    showNotificationsAction: Boolean = true,
    viewModel: CaseListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    var statementTypeMenuExpanded by remember { mutableStateOf(false) }
    var pendingDeleteCaseId by remember { mutableStateOf<Long?>(null) }
    val importExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportExcel(uri)
    }
    LaunchedEffect(state.exportedFileUri) {
        val uri = state.exportedFileUri ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, CaseListViewModel.EXCEL_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        viewModel.onExportConsumed()
    }

    if (state.showStatementDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeStatementDialog,
            title = { Text("استخراج بيان القضايا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "اختر نوع القضايا والفترة. البيان يُرتب برقم الوارد، والنوع يظهر في عنوان البيان ولا يتكرر داخل الجدول.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenuBox(
                        expanded = statementTypeMenuExpanded,
                        onExpandedChange = { statementTypeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.statementType.menuLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("نوع البيان") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statementTypeMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = statementTypeMenuExpanded,
                            onDismissRequest = { statementTypeMenuExpanded = false }
                        ) {
                            CaseStatementType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.menuLabel) },
                                    onClick = {
                                        viewModel.onStatementTypeChanged(type)
                                        statementTypeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.statementFromDate.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("من تاريخ") },
                        trailingIcon = {
                            IconButton(onClick = { showFromDatePicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, "اختيار تاريخ البداية")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.statementToDate.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("حتى تاريخ") },
                        trailingIcon = {
                            IconButton(onClick = { showToDatePicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, "اختيار تاريخ النهاية")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        when (state.statementType) {
                            CaseStatementType.CIVIL -> "يشمل المدني الكلي والجزئي والمدني المستأنف."
                            CaseStatementType.FAMILY -> "يشمل الأحوال الشخصية وشؤون الأسرة وما يرتبط بها."
                            CaseStatementType.CRIMINAL -> "يشمل قضايا الجنح."
                            CaseStatementType.HIGH_APPEAL -> "الاستئناف العالي مستقل عن المدني المستأنف."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::onExportStatement, enabled = !state.isOfficeBusy) {
                    Text("استخراج Excel")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeStatementDialog) { Text("إلغاء") }
            }
        )
    }

    if (showFromDatePicker) {
        StatementDatePicker(state.statementFromDate, { showFromDatePicker = false }) {
            viewModel.onStatementFromDateChanged(it)
            showFromDatePicker = false
        }
    }
    if (showToDatePicker) {
        StatementDatePicker(state.statementToDate, { showToDatePicker = false }) {
            viewModel.onStatementToDateChanged(it)
            showToDatePicker = false
        }
    }

    if (state.showImportReview) {
        AlertDialog(
            onDismissRequest = viewModel::onCancelImport,
            title = { Text("مراجعة استيراد Excel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("جديدة: ${state.pendingImportCases.size} — مكررة: ${state.pendingImportDuplicates} — غير صالحة: ${state.pendingImportRejected}")
                    Text("لن يتم حفظ أي قضية قبل الضغط على اعتماد وحفظ.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.pendingImportCases.isNotEmpty()) {
                            item { Text("القضايا الجديدة", style = MaterialTheme.typography.titleSmall) }
                            items(
                                state.pendingImportCases,
                                key = { "new|${it.incomingNo}|${it.caseNo}|${it.caseYear}|${it.court}" }
                            ) { c ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("دعوى ${c.caseNo} لسنة ${c.caseYear}", style = MaterialTheme.typography.titleSmall)
                                        Text(c.court)
                                        if (c.caseType.isNotBlank()) Text(c.caseType, style = MaterialTheme.typography.bodySmall)
                                        if (c.incomingNo.isNotBlank()) {
                                            Text("وارد ${c.incomingNo} — ${c.incomingDate}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (c.parties.isNotEmpty()) {
                                            Text(c.parties.joinToString(" • ") { it.fullName }, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        if (state.pendingRejectedCases.isNotEmpty()) {
                            item { Text("القضايا غير الصالحة وسبب الرفض", style = MaterialTheme.typography.titleSmall) }
                            items(
                                state.pendingRejectedCases,
                                key = { "bad|${it.incomingNo}|${it.caseNo}|${it.caseYear}|${it.court}|${it.reasons.joinToString()}" }
                            ) { r ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            if (r.caseNo.isBlank()) "قضية بدون رقم" else "دعوى ${r.caseNo}${if (r.caseYear.isBlank()) "" else " لسنة ${r.caseYear}"}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (r.court.isNotBlank()) Text(r.court, style = MaterialTheme.typography.bodySmall)
                                        if (r.incomingNo.isNotBlank()) Text("وارد ${r.incomingNo}", style = MaterialTheme.typography.bodySmall)
                                        r.reasons.forEach { reason -> Text("• $reason", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                        if (state.pendingImportCases.isEmpty() && state.pendingRejectedCases.isEmpty()) {
                            item { Text("لا توجد قضايا جديدة جاهزة للحفظ.") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = if (state.pendingImportCases.isEmpty()) viewModel::onCancelImport else viewModel::onConfirmImport) {
                    Text(if (state.pendingImportCases.isEmpty()) "إغلاق" else "اعتماد وحفظ")
                }
            },
            dismissButton = {
                if (state.pendingImportCases.isNotEmpty()) {
                    TextButton(onClick = viewModel::onCancelImport) { Text("إلغاء") }
                }
            }
        )
    }


    pendingDeleteCaseId?.let { caseId ->
        val currentCase = state.cases.firstOrNull { it.id == caseId }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("تأكيد إزالة القضية") },
            text = {
                Text(
                    if (currentCase == null) "هل تريد إزالة هذه القضية؟"
                    else "هل تريد إزالة دعوى ${currentCase.caseNo} لسنة ${currentCase.caseYear}؟"
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.onDeleteCase(caseId)
                    pendingDeleteCaseId = null
                }) { Text("إزالة") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCaseId = null }) { Text("إلغاء") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بيانات القضايا") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
                actions = {
                    IconButton(onClick = viewModel::openStatementDialog, enabled = !state.isOfficeBusy) {
                        Icon(Icons.Filled.Description, "استخراج بيان القضايا")
                    }
                    IconButton(
                        onClick = { importExcel.launch(arrayOf(CaseListViewModel.EXCEL_MIME, "application/vnd.ms-excel")) },
                        enabled = !state.isOfficeBusy
                    ) {
                        Icon(Icons.Filled.Upload, "استيراد Excel")
                    }
                    IconButton(onClick = viewModel::onExportExcel, enabled = !state.isOfficeBusy) {
                        Icon(Icons.Filled.Download, "تصدير Excel")
                    }
                    IconButton(onClick = onOpenRegisters) { Icon(Icons.Filled.Description, "السجلات") }
                    IconButton(onClick = onOpenWorkMinutes) { Icon(Icons.AutoMirrored.Filled.Article, "محاضر الأعمال") }
                    if (showNotificationsAction) {
                        IconButton(onClick = onOpenNotifications) { Icon(Icons.Filled.Mail, "الإخطارات") }
                    }
                    IconButton(onClick = onOpenExpertProfile) { Icon(Icons.Filled.Person, "بيانات الخبير") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewCase, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("قضية جديدة") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isOfficeBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.officeMessage?.let { message ->
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::onOfficeMessageConsumed) { Text("إخفاء") }
                    }
                }
            }
            FilledTonalButton(
                onClick = viewModel::openStatementDialog,
                enabled = !state.isOfficeBusy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("استخراج بيان القضايا حسب النوع والفترة")
            }
            KhabirTextField(
                state.query,
                viewModel::onQueryChanged,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("رقم الدعوى أو الوارد أو المحكمة أو اسم الخصم") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.cases.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد قضايا") }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.cases, key = { it.id }) { c ->
                        KhabirCard(onClick = { onOpenCase(c.id) }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("دعوى ${c.caseNo} لسنة ${c.caseYear}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onOpenCase(c.id) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل القضية")
                                }
                                IconButton(onClick = { pendingDeleteCaseId = c.id }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "إزالة القضية")
                                }
                            }
                            Text(c.court)
                            Text(c.caseType, style = MaterialTheme.typography.bodySmall)
                            if (c.parties.isNotEmpty()) {
                                Text(c.parties.joinToString(" • ") { it.fullName }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementDatePicker(initialDate: LocalDate, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val initialMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
