package com.khabir.app.presentation.templates

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.presentation.reports.ReportScreen
import com.khabir.app.presentation.reports.ReportViewModel
import com.khabir.app.presentation.workminutes.WorkMinutesScreen
import com.khabir.app.presentation.workminutes.WorkMinutesViewModel

private const val WORD_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun TemplateAwareReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val addTemplateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.onSelectWordTemplate(uri)
    }

    Column(Modifier.fillMaxSize()) {
        WordTemplatePanel(
            title = "قالب التقرير",
            hasSavedTemplate = state.savedWordTemplateUri.isNotBlank(),
            busy = state.isExporting,
            onAddTemplate = { addTemplateLauncher.launch(arrayOf(WORD_MIME)) },
            onUseSavedTemplate = viewModel::onUseSavedWordTemplate,
            onForgetTemplate = viewModel::onForgetWordTemplate
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ReportScreen(onBack = onBack, viewModel = viewModel)
        }
    }
}

@Composable
fun TemplateAwareWorkMinutesScreen(
    onBack: () -> Unit,
    viewModel: WorkMinutesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val addTemplateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.onSelectWordTemplate(uri)
    }

    Column(Modifier.fillMaxSize()) {
        WordTemplatePanel(
            title = "قالب محاضر الأعمال",
            hasSavedTemplate = state.savedWordTemplateUri.isNotBlank(),
            busy = state.isExporting,
            onAddTemplate = { addTemplateLauncher.launch(arrayOf(WORD_MIME)) },
            onUseSavedTemplate = viewModel::onUseSavedWordTemplate,
            onForgetTemplate = viewModel::onForgetWordTemplate
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            WorkMinutesScreen(onBack = onBack, viewModel = viewModel)
        }
    }
}

@Composable
private fun WordTemplatePanel(
    title: String,
    hasSavedTemplate: Boolean,
    busy: Boolean,
    onAddTemplate: () -> Unit,
    onUseSavedTemplate: () -> Unit,
    onForgetTemplate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "أضف ملف DOCX كقالب شخصي. الزر متاح دائمًا حتى قبل إدخال بيانات التقرير أو المحاضر.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(7.dp))
            Button(
                onClick = onAddTemplate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  إضافة قالب Word")
            }
            if (hasSavedTemplate) {
                Spacer(Modifier.height(5.dp))
                OutlinedButton(
                    onClick = onUseSavedTemplate,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Text("  استخدام القالب المحفوظ")
                }
                TextButton(
                    onClick = onForgetTemplate,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حذف القالب المحفوظ")
                }
            }
        }
    }
}
