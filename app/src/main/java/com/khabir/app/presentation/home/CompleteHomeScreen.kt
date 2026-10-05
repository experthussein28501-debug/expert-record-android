package com.khabir.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khabir.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteHomeScreen(
    onOpenCases: () -> Unit,
    onNewCase: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenRegisters: () -> Unit,
    onOpenWorkMinutes: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenExpertProfile: () -> Unit,
    onOpenBackup: () -> Unit,
    notificationsEnabled: Boolean,
    reportsEnabled: Boolean
) {
    val monetization = com.khabir.app.monetization.LocalMonetization.current
    androidx.compose.runtime.DisposableEffect(monetization) {
        monetization?.onHomeVisible(true)
        onDispose { monetization?.onHomeVisible(false) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("سجل الخبير", fontWeight = FontWeight.Bold)
                        Text("النسخة المجمعة ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("اختصارات العمل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            com.khabir.app.presentation.components.InlineHelp("مساعدة", "كل وحدة رئيسية ظاهرة هنا مباشرة، والأجندة تجمع المواعيد ومحاضر الأعمال في تقويم واحد.")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeModuleCard("قضية جديدة", "إضافة قضية وخصومها", Icons.Filled.Add, onNewCase, Modifier.weight(1f))
                HomeModuleCard("القضايا", "بحث وتعديل القضايا", Icons.Filled.Folder, onOpenCases, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeModuleCard("محاضر الأعمال", "فتح وإنشاء محاضر الأعمال", Icons.Filled.Assignment, onOpenWorkMinutes, Modifier.weight(1f))
                HomeModuleCard("التقارير", "القوالب والتحرير والتصدير", Icons.Filled.Description, onOpenReports, Modifier.weight(1f), reportsEnabled)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeModuleCard("الإخطارات", "الإخطارات والسركي وهيئة قضايا الدولة", Icons.Filled.Mail, onOpenNotifications, Modifier.weight(1f), notificationsEnabled)
                HomeModuleCard("السجلات", "السجلات والمستخرجات", Icons.Filled.TableChart, onOpenRegisters, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeModuleCard("الأجندة", "تقويم حي ومواعيد القضايا", Icons.Filled.CalendarMonth, onOpenAgenda, Modifier.weight(1f))
                HomeModuleCard("بيانات الخبير", "بيانات المكتب والذكاء الاصطناعي", Icons.Filled.Person, onOpenExpertProfile, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeModuleCard("المهام", "غير مفعلة حاليًا", Icons.Filled.Lock, {}, Modifier.weight(1f), enabled = false)
                HomeModuleCard("النسخ الاحتياطي", "نسخة مشفرة واستعادة", Icons.Filled.Backup, onOpenBackup, Modifier.weight(1f))
            }

            com.khabir.app.monetization.HomeBanner()
            if (!notificationsEnabled) Text("ملاحظة: وحدة الإخطارات غير مفعلة في نمط البناء الحالي.", color = MaterialTheme.colorScheme.error)
            if (!reportsEnabled) Text("ملاحظة: وحدة التقارير غير مفعلة في نمط البناء الحالي.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun HomeModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PaddingValues(14.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = title)
            Text(title, fontWeight = FontWeight.Bold)
            com.khabir.app.presentation.components.InlineHelp(title, subtitle)
        }
    }
}
