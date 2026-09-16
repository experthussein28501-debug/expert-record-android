package com.khabir.app.presentation.registers

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.presentation.components.KhabirCard
import com.khabir.app.presentation.components.KhabirPrimaryButton
import com.khabir.app.presentation.components.KhabirTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, viewModel: RegisterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.exportedFileUri) {
        state.exportedFileUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
            viewModel.onExportEventConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("السجلات والمستخرجات") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                KhabirPrimaryButton(
                    text = if (state.isExporting) "جارٍ إنشاء Word..." else "تصدير البيان إلى Word",
                    onClick = viewModel::onExport,
                    enabled = state.cases.isNotEmpty() && !state.isExporting,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("استخراج مستقل حسب النوع والفترة والمحكمة", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            KhabirTextField(
                state.filter.caseType.orEmpty(),
                viewModel::onCaseTypeChanged,
                label = { Text("نوع الدعوى") }
            )
            Spacer(Modifier.height(8.dp))
            KhabirTextField(
                state.filter.court.orEmpty(),
                viewModel::onCourtChanged,
                label = { Text("المحكمة") }
            )
            Spacer(Modifier.height(12.dp))
            Text("النتائج: ${state.cases.size}")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.cases, key = { it.id }) { c ->
                    KhabirCard {
                        Text("${c.caseNo} لسنة ${c.caseYear}")
                        Text(c.court, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
