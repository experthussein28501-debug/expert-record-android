package com.khabir.app.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHubScreen(
    onBack: () -> Unit,
    onOpenRegisteredReport: (Long) -> Unit,
    onOpenSavedReport: (Long) -> Unit,
    onStartIndependentReport: () -> Unit,
    viewModel: ReportsHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showRegisteredCases by remember { mutableStateOf(false) }
    var pendingDeleteReportId by remember { mutableStateOf<Long?>(null) }

    pendingDeleteReportId?.let { reportId ->
        val report = state.reports.firstOrNull { it.id == reportId }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("تأكيد إزالة التقرير") },
            text = { Text(if (report == null) "هل تريد إزالة هذا التقرير؟" else "هل تريد إزالة التقرير الخاص بالدعوى ${report.caseNo.ifBlank { "غير المسجلة" }}؟") },
            confirmButton = { Button(onClick = { viewModel.onDeleteReport(reportId); pendingDeleteReportId = null }) { Text("إزالة") } },
            dismissButton = { TextButton(onClick = { pendingDeleteReportId = null }) { Text("إلغاء") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("التقارير", fontWeight = FontWeight.Bold); Text("تقرير جديد أو من دعوى مسجلة", style = MaterialTheme.typography.labelMedium) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("إضافة تقرير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("لو الدعوى غير موجودة في سجل القضايا اختر «دعوى جديدة». ولو مسجلة افتح القائمة واسحب بياناتها مباشرة.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = onStartIndependentReport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("دعوى جديدة")
                            }
                            FilledTonalButton(onClick = { showRegisteredCases = !showRegisteredCases }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("الدعاوى المسجلة")
                            }
                        }
                    }
                }
            }

            if (showRegisteredCases) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        label = { Text("ابحث برقم الدعوى أو المحكمة أو اسم الخصم") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!state.isLoading && state.cases.isEmpty()) {
                    item { ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.size(10.dp)); Text("لا توجد قضايا مطابقة.") } } }
                }
                items(state.cases, key = { "case-${it.id}" }) { case ->
                    ElevatedCard(onClick = { onOpenRegisteredReport(case.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, null, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("الدعوى ${case.caseNo} لسنة ${case.caseYear}", fontWeight = FontWeight.Bold)
                                Text(listOf(case.caseType, case.court).filter { it.isNotBlank() }.joinToString(" — "), style = MaterialTheme.typography.bodyMedium)
                                if (case.parties.isNotEmpty()) Text(case.parties.take(2).joinToString(" • ") { it.reportDisplayName }, style = MaterialTheme.typography.bodySmall)
                                val intakeDates = buildList {
                                    case.receiptDate?.let { add("الاستلام: $it") }
                                    case.preliminaryJudgmentDate?.let { add("الحكم التمهيدي: $it") }
                                }
                                if (intakeDates.isNotEmpty()) Text(intakeDates.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                            }
                            Text("اختيار", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item { HorizontalDivider() }
            }

            item {
                Text("التقارير المحفوظة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("إعادة فتح التقرير المحفوظ تكمل على نفس البيانات دون إنشاء نسخة جديدة.", style = MaterialTheme.typography.bodySmall)
            }

            if (!state.isLoading && state.reports.isEmpty()) {
                item { ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.size(10.dp)); Text("لا توجد تقارير محفوظة مطابقة.") } } }
            }

            items(state.reports, key = { "report-${it.id}" }) { report ->
                ElevatedCard(onClick = { onOpenSavedReport(report.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            val title = if (report.caseNo.isBlank()) "تقرير دعوى جديدة" else "الدعوى ${report.caseNo} لسنة ${report.caseYear}"
                            Text(title, fontWeight = FontWeight.Bold)
                            if (report.court.isNotBlank()) Text(report.court, style = MaterialTheme.typography.bodyMedium)
                            val preview = report.subjectOfCase.ifBlank { report.partiesSummary }
                            if (preview.isNotBlank()) Text(preview.take(120), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onOpenSavedReport(report.id) }) { Icon(Icons.Filled.Edit, "تعديل التقرير") }
                        IconButton(onClick = { pendingDeleteReportId = report.id }) { Icon(Icons.Filled.Delete, "إزالة التقرير") }
                    }
                }
            }
        }
    }
}

