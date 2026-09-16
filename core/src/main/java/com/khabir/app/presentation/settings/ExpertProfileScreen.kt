package com.khabir.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.data.ai.AiProvider
import com.khabir.app.presentation.components.KhabirPrimaryButton
import com.khabir.app.presentation.components.KhabirSecondaryButton
import com.khabir.app.presentation.components.KhabirSectionHeader
import com.khabir.app.presentation.components.KhabirTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertProfileScreen(
    onBack: () -> Unit,
    onShowLoginAgain: () -> Unit = {},
    viewModel: ExpertProfileViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بيانات الخبير") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "تُكتب مرة واحدة وتُستورد تلقائيًا في الإخطارات والتقارير والمستخرجات",
                style = MaterialTheme.typography.bodyMedium
            )
            KhabirTextField(s.ministryOrSector, viewModel::onMinistryChanged, label = { Text("الوزارة / القطاع") })
            KhabirTextField(s.department, viewModel::onDepartmentChanged, label = { Text("الإدارة / المكتب") })
            KhabirTextField(s.expertName, viewModel::onNameChanged, label = { Text("اسم الخبير") })
            KhabirTextField(s.jobTitle, viewModel::onJobTitleChanged, label = { Text("الدرجة / الصفة الوظيفية") })
            KhabirTextField(s.specialization, viewModel::onSpecializationChanged, label = { Text("التخصص") })
            KhabirTextField(s.officeAddress, viewModel::onAddressChanged, label = { Text("عنوان المكتب") }, minLines = 2)
            KhabirTextField(
                s.attendancePhrase,
                viewModel::onAttendancePhraseChanged,
                label = { Text("عبارة الحضور إلى المكتب") },
                minLines = 2
            )
            KhabirSectionHeader("مزود الذكاء الاصطناعي")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = s.aiProvider == provider,
                        onClick = { viewModel.onAiProviderChanged(provider) },
                        label = { Text(provider.label) }
                    )
                }
            }
            KhabirTextField(
                s.personalAiKey,
                viewModel::onPersonalAiKeyChanged,
                label = { Text("مفتاح ${s.aiProvider.label} الشخصي — اختياري") },
                placeholder = { Text("يُحفظ مشفرًا على هذا الجهاز") },
                visualTransformation = PasswordVisualTransformation()
            )
            KhabirPrimaryButton(
                text = if (s.isTestingAiKey) "جارٍ اختبار المفتاح باتصال حقيقي..." else "اختبار وحفظ المفتاح",
                onClick = viewModel::testAndSaveAiKey,
                enabled = !s.isTestingAiKey
            )
            val providerCapabilities = when (s.aiProvider) {
                AiProvider.GEMINI -> "تحليل الصور، الصوت، ومساعد التقرير"
                AiProvider.OPENAI -> "تحليل الصور، الصوت، ومساعد التقرير"
                AiProvider.ANTHROPIC -> "تحليل الصور ومساعد التقرير؛ التفريغ الصوتي المباشر غير متاح"
                AiProvider.GROQ -> "تحليل الصور، الصوت، ومساعد التقرير"
            }
            Text(
                if (s.hasPersonalAiKey) {
                    "مفتاح ${s.aiProvider.label} محفوظ ومشفر؛ اكتب مفتاحًا جديدًا لاستبداله. القدرات: $providerCapabilities."
                } else {
                    "أضف مفتاح ${s.aiProvider.label}. القدرات: $providerCapabilities. يظل OCR المحلي متاحًا بدون مفتاح."
                },
                style = MaterialTheme.typography.bodySmall
            )
            val aiKeyMessage = s.aiKeyMessage
            if (aiKeyMessage != null) {
                val isPositive = aiKeyMessage.contains("صالح") || aiKeyMessage.contains("تم حذف")
                Text(
                    aiKeyMessage,
                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            if (s.hasPersonalAiKey) {
                KhabirSecondaryButton(text = "حذف المفتاح الشخصي", onClick = viewModel::clearPersonalAiKey)
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            KhabirSectionHeader("تعليمات إضافية للذكاء الاصطناعي — اختيارية")
            Text(
                "القواعد الأساسية لقراءة المستندات والتقرير موجودة داخل التطبيق. اكتب هنا فقط توجيهاتك الخاصة، مثل أسلوب صياغة التقرير أو مستندات تريد التركيز عليها.",
                style = MaterialTheme.typography.bodySmall
            )
            KhabirTextField(
                value = s.aiInstructions,
                onValueChange = viewModel::onAiInstructionsChanged,
                label = { Text("توجيهات خاصة بك") },
                placeholder = { Text("مثال: عند بحث المستندات اذكر تاريخ كل مستند وما يثبته، ولا تكتب رأيًا فنيًا قبل طلبه.") },
                minLines = 5
            )
            val validationMessage = s.validationMessage
            if (validationMessage != null) {
                Text(validationMessage, color = MaterialTheme.colorScheme.error)
            }
            KhabirPrimaryButton(
                text = if (s.isSaving) "جارٍ الحفظ..." else "حفظ بيانات الخبير",
                onClick = viewModel::onSave,
                enabled = !s.isSaving
            )
            if (s.savedJustNow) {
                Text("تم الحفظ", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            KhabirSecondaryButton(text = "إظهار شاشة تسجيل الدخول مرة أخرى", onClick = onShowLoginAgain)
        }
    }
}
