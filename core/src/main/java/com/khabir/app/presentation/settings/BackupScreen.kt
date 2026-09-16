package com.khabir.app.presentation.settings

import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(viewModel::createBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreBackup)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("النسخ الاحتياطي والاسترجاع") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("نسخة مشفرة بالكامل", fontWeight = FontWeight.Bold) }
                    Text("تشمل القضايا والخصوم والتقارير والإخطارات وبيانات الخبير. يمكن حفظ الملف على الجهاز أو Google Drive أو OneDrive من نافذة اختيار الملفات.")
                }
            }

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("كلمة مرور النسخة الاحتياطية") },
                supportingText = { Text("لن يستطيع التطبيق استرجاع النسخة بدونها؛ احتفظ بها في مكان آمن.") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "إظهار أو إخفاء كلمة المرور") } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isBusy
            )

            Button(
                onClick = { createLauncher.launch("سجل_الخبير_${LocalDate.now()}.khabirbackup") },
                enabled = !state.isBusy && state.password.length >= 6,
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("إنشاء نسخة احتياطية مشفرة") }

            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = !state.isBusy && state.password.length >= 6,
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp)); Text("استرجاع نسخة سابقة") }

            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        }
    }

    if (state.restartRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("تم استرجاع البيانات") },
            text = { Text("سيعاد تشغيل التطبيق الآن لفتح قاعدة البيانات المسترجعة بأمان.") },
            confirmButton = {
                Button(onClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (launchIntent != null) context.startActivity(launchIntent)
                    Process.killProcess(Process.myPid())
                }) { Text("إعادة التشغيل") }
            }
        )
    }
}
