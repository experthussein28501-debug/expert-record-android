package com.khabir.app.presentation.workminutes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkMinutesHubScreen(
    onBack: () -> Unit,
    onOpenRegisteredRecord: (Long) -> Unit,
    onOpenSavedRecord: (Long) -> Unit,
    onStartIndependentRecord: () -> Unit,
    viewModel: WorkMinutesHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showRegisteredCases by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("محاضر الأعمال", fontWeight = FontWeight.Bold); Text("مجموعة جديدة أو من دعوى مسجلة", style = MaterialTheme.typography.labelMedium) } },
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
                KhabirCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("إضافة محاضر أعمال", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("لو الدعوى غير موجودة في سجل القضايا اختر «دعوى جديدة». ولو مسجلة افتح القائمة واسحب بياناتها مباشرة.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = onStartIndependentRecord, modifier = Modifier.weight(1f)) {
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
                    KhabirTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        label = { Text("ابحث برقم الدعوى أو المحكمة أو اسم الخصم") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true
                    )
                }
                if (!state.isLoading && state.cases.isEmpty()) {
                    item { KhabirCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.size(10.dp)); Text("لا توجد قضايا مطابقة.") } } }
                }
                items(state.cases, key = { "case-${it.id}" }) { case ->
                    KhabirCard(onClick = { onOpenRegisteredRecord(case.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, null, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("الدعوى ${case.caseNo} لسنة ${case.caseYear}", fontWeight = FontWeight.Bold)
                                Text(listOf(case.caseType, case.court).filter { it.isNotBlank() }.joinToString(" — "), style = MaterialTheme.typography.bodyMedium)
                                if (case.parties.isNotEmpty()) Text(case.parties.take(2).joinToString(" • ") { it.reportDisplayName }, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("اختيار", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item { HorizontalDivider() }
            }

            item {
                Text("محاضر أعمال محفوظة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("إعادة فتح المجموعة المحفوظة تكمل على نفس المحاضر دون إنشاء نسخة جديدة.", style = MaterialTheme.typography.bodySmall)
            }

            if (!state.isLoading && state.records.isEmpty()) {
                item { KhabirCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.size(10.dp)); Text("لا توجد محاضر أعمال محفوظة مطابقة.") } } }
            }

            items(state.records, key = { "wm-${it.id}" }) { record ->
                KhabirCard(onClick = { onOpenSavedRecord(record.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            val title = if (record.caseNo.isBlank()) "محاضر دعوى جديدة" else "الدعوى ${record.caseNo} لسنة ${record.caseYear}"
                            Text(title, fontWeight = FontWeight.Bold)
                            if (record.court.isNotBlank()) Text(record.court, style = MaterialTheme.typography.bodyMedium)
                            Text("${record.entries.size} محضر", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("متابعة", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
