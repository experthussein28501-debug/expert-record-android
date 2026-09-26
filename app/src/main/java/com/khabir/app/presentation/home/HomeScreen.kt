package com.khabir.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// هوية "سجل الخبير": نحاسي فاتح كخلفية شارة، أزرق ليلي (Ink) كنص/رمز أساسي،
// نحاسي غامق كلون ثانوي — مطابقة لـ KhabirTheme بدل بالتة منفصلة هنا.
private val BadgeTint = Color(0xFFF1E6CE)
private val AccentInk = Color(0xFF14213D)
private val AccentInkDeep = Color(0xFF0C1730)
private val AccentBrass = Color(0xFF9C7A3C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCases: () -> Unit,
    onNewCase: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenRegisters: () -> Unit,
    onOpenExpertProfile: () -> Unit,
    onOpenBackup: () -> Unit,
    stageOneOnly: Boolean = false,
    combinedMode: Boolean = false,
    notificationsEnabled: Boolean = false,
    reportsEnabled: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BadgeTint,
                    titleContentColor = AccentInkDeep
                ),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("سجل الخبير", fontWeight = FontWeight.Bold)
                            Text("إدارة أعمال الخبرة القضائية", style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(
                            color = AccentInk,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                when {
                                    combinedMode -> "مجمعة 0.9.11"
                                    reportsEnabled -> "التقارير 0.8.2"
                                    notificationsEnabled -> "الإخطارات"
                                    else -> "الوحدة الأولى"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCase,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("قضية جديدة") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DashboardHero(state, stageOneOnly, combinedMode, notificationsEnabled, reportsEnabled)

            SectionTitle("اختصارات العمل", "اختَر القسم وابدأ مباشرة")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tablet = maxWidth >= 700.dp
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("القضايا", "إضافة وبحث", Icons.Filled.Folder, onOpenCases, Modifier.weight(1f))
                        QuickAction("السجلات", "بيان القضايا والتصدير", Icons.Filled.TableChart, onOpenRegisters, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("الإخطارات", if(!notificationsEnabled) "الوحدة الثانية — غير مفعلة" else "إخطارات ٢×٢", Icons.Filled.Mail, onOpenNotifications, Modifier.weight(1f), enabled=notificationsEnabled)
                        QuickAction("التقارير", if(!reportsEnabled) "المرحلة الثالثة — غير مفعلة" else "القوالب والتحرير", Icons.Filled.Description, onOpenReports, Modifier.weight(1f), enabled=reportsEnabled)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("سركي الإخطارات", if(!notificationsEnabled) "الوحدة الثانية — غير مفعلة" else "تجميع وإجراءات دفعة", Icons.Filled.LocalPostOffice, onOpenNotifications, Modifier.weight(1f), enabled=notificationsEnabled)
                        if (!tablet) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SectionTitle("ملخص العمل", "الأرقام المسجلة على الجهاز")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tablet = maxWidth >= 700.dp
                if (stageOneOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("القضايا", state.casesCount.toString(), Icons.Filled.Folder, Modifier.weight(1f))
                        StatCard("بيانات الخبير", if(state.expertName.isBlank()) "غير مكتملة" else "محفوظة", Icons.Filled.Person, Modifier.weight(1f))
                    }
                } else if (combinedMode) {
                    if (tablet) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("القضايا", state.casesCount.toString(), Icons.Filled.Folder, Modifier.weight(1f))
                            StatCard("التقارير", state.reportsCount.toString(), Icons.Filled.Description, Modifier.weight(1f))
                            StatCard("دفعات الإخطارات", state.notificationBatchesCount.toString(), Icons.Filled.Mail, Modifier.weight(1f))
                            StatCard("المخاطبون", state.recipientsCount.toString(), Icons.Filled.Groups, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard("القضايا", state.casesCount.toString(), Icons.Filled.Folder, Modifier.weight(1f))
                                StatCard("التقارير", state.reportsCount.toString(), Icons.Filled.Description, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard("دفعات الإخطارات", state.notificationBatchesCount.toString(), Icons.Filled.Mail, Modifier.weight(1f))
                                StatCard("المخاطبون", state.recipientsCount.toString(), Icons.Filled.Groups, Modifier.weight(1f))
                            }
                        }
                    }
                } else if (reportsEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("القضايا المرتبطة", state.casesCount.toString(), Icons.Filled.Folder, Modifier.weight(1f))
                        StatCard("التقارير", state.reportsCount.toString(), Icons.Filled.Description, Modifier.weight(1f))
                    }
                } else if (tablet) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("القضايا", state.casesCount.toString(), Icons.Filled.Folder, Modifier.weight(1f))
                        StatCard("التقارير", state.reportsCount.toString(), Icons.Filled.Description, Modifier.weight(1f))
                        StatCard("دفعات الإخطارات", state.notificationBatchesCount.toString(), Icons.Filled.Mail, Modifier.weight(1f))
                        StatCard("المخاطبون", state.recipientsCount.toString(), Icons.Filled.Groups, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("القضايا", state.casesCount.toString(), Icons.Filled.Folder, Modifier.weight(1f))
                            StatCard("التقارير", state.reportsCount.toString(), Icons.Filled.Description, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("دفعات الإخطارات", state.notificationBatchesCount.toString(), Icons.Filled.Mail, Modifier.weight(1f))
                            StatCard("المخاطبون", state.recipientsCount.toString(), Icons.Filled.Groups, Modifier.weight(1f))
                        }
                    }
                }
            }

            SectionTitle("تفاصيل الأقسام", "كل أدوات المكتب في مكان واحد")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tabletLayout = maxWidth >= 700.dp
                if (tabletLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SystemCard("بيانات القضايا", "الوارد، الدعوى، المحكمة، النوع، الخصوم والعناوين", Icons.Filled.Folder, "فتح القضايا", onOpenCases, Modifier.weight(1f))
                            SystemCard("الإخطارات والمراسلات", "إخطارات وأظرف وحافظة بريد من نفس بيانات الخصوم", Icons.Filled.Mail, if(!notificationsEnabled) "غير مفعلة في هذه الوحدة" else "فتح الإخطارات", onOpenNotifications, Modifier.weight(1f), enabled=notificationsEnabled)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SystemCard("التقارير", "تقارير مستقلة أو مرتبطة بقضية مع Word وPDF وExcel", Icons.Filled.Description, if(!reportsEnabled) "غير مفعلة في هذه المرحلة" else "فتح التقارير", onOpenReports, Modifier.weight(1f), enabled=reportsEnabled)
                            SystemCard("السجلات والمستخرجات", "جداول وبيانات قابلة للتجهيز والتصدير", Icons.Filled.TableChart, "فتح السجلات", onOpenRegisters, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SystemCard("سركي الإخطارات والإجراءات الجماعية", "تجميع القضايا المختارة في سركي إخطارات وإجراءات دفعة", Icons.Filled.LocalPostOffice, if(!notificationsEnabled) "غير مفعلة في هذه الوحدة" else "فتح سركي الإخطارات", onOpenNotifications, Modifier.weight(1f), enabled=notificationsEnabled)
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SystemCard("بيانات القضايا", "الوارد، الدعوى، المحكمة، النوع، الخصوم والعناوين", Icons.Filled.Folder, "فتح القضايا", onOpenCases)
                        SystemCard("الإخطارات والمراسلات", "إخطارات وأظرف وحافظة بريد من نفس بيانات الخصوم", Icons.Filled.Mail, if(!notificationsEnabled) "غير مفعلة في هذه الوحدة" else "فتح الإخطارات", onOpenNotifications, enabled=notificationsEnabled)
                        SystemCard("التقارير", "تقارير مستقلة أو مرتبطة بقضية مع Word وPDF وExcel", Icons.Filled.Description, if(!reportsEnabled) "غير مفعلة في هذه المرحلة" else "فتح التقارير", onOpenReports, enabled=reportsEnabled)
                        SystemCard("سركي الإخطارات والإجراءات الجماعية", "تجميع القضايا المختارة في سركي إخطارات وإجراءات دفعة", Icons.Filled.LocalPostOffice, if(!notificationsEnabled) "غير مفعلة في هذه الوحدة" else "فتح سركي الإخطارات", onOpenNotifications, enabled=notificationsEnabled)
                        SystemCard("السجلات والمستخرجات", "جداول وبيانات قابلة للتجهيز والتصدير", Icons.Filled.TableChart, "فتح السجلات", onOpenRegisters)
                    }
                }
            }

            WhatsNewCard(stageOneOnly, combinedMode, reportsEnabled)

            SectionTitle("الإعداد والحماية", "بيانات المكتب والنسخة المشفرة")
            UtilityCard("بيانات الخبير", "اسم الخبير والإدارة والعنوان المستخدم تلقائيًا في المستندات", Icons.Filled.Person, onOpenExpertProfile)
            UtilityCard("نسخ احتياطي مشفر", "إنشاء أو استرجاع نسخة كاملة من القضايا والتقارير والإخطارات", Icons.Filled.Backup, onOpenBackup)

            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("الحفظ المحلي يعمل", fontWeight = FontWeight.Bold)
                        Text(when {
                            reportsEnabled -> "القضايا المرتبطة والتقارير محفوظة محليًا، ويمكن تصدير نسخة مشفرة."
                            notificationsEnabled -> "القضايا والإخطارات محفوظة محليًا، ويمكن تصدير نسخة مشفرة."
                            else -> "بيانات الخبير والقضايا محفوظة محليًا، ويمكن تصدير نسخة مشفرة."
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    AssistChip(onClick = {}, label = { Text("جاهز") })
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun DashboardHero(state: HomeUiState, stageOneOnly: Boolean, combinedMode: Boolean, notificationsEnabled: Boolean, reportsEnabled: Boolean) {
    val gradient = Brush.horizontalGradient(
        listOf(AccentInkDeep, AccentInk, AccentBrass)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                Icon(Icons.Filled.Balance, null, modifier = Modifier.padding(12.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    state.department.ifBlank { "إدارة خبراء أسوان" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    state.expertName.ifBlank { "اضغط بيانات الخبير لإضافة الاسم" },
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
        Text(
            when {
                state.isLoading -> "جارٍ تحميل ملخص العمل..."
                stageOneOnly -> "الوحدة الأولى: بيانات الخبير والقضايا والبحث."
                combinedMode -> "نسخة مجمعة: القضايا والإخطارات والسركي والتقارير والسجلات في تطبيق واحد."
                reportsEnabled -> "وحدة التقارير: ${state.reportsCount} تقرير، مستقلة أو مرتبطة بقضية."
                notificationsEnabled -> "وحدة الإخطارات: ${state.notificationBatchesCount} دفعة و${state.recipientsCount} مخاطب."
                else -> "سجل الخبير"
            },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AccentInkDeep, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = BadgeTint)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(color = if(enabled) AccentInk else Color.Gray, shape = RoundedCornerShape(14.dp)) {
                Icon(icon, null, Modifier.padding(10.dp).size(28.dp), tint = Color.White)
            }
            Text(title, fontWeight = FontWeight.Bold, color = AccentInkDeep)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AccentInk)
        }
    }
}

@Composable
private fun WhatsNewCard(stageOneOnly: Boolean, combinedMode: Boolean, reportsEnabled: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = BadgeTint)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NewReleases, null, tint = AccentBrass)
                Spacer(Modifier.width(8.dp))
                Text(if(stageOneOnly) "المتاح في المرحلة الأولى" else "الجديد في سجل الخبير", fontWeight = FontWeight.Bold, color = AccentInkDeep)
            }
            if(stageOneOnly){
                Text("• بيانات الخبير ومفتاح Gemini المشفر واختباره فعليًا", style = MaterialTheme.typography.bodySmall)
                Text("• تجميع صور المستندات وعزل المستند المختلف قبل الحفظ", style = MaterialTheme.typography.bodySmall)
                Text("• البحث وبيان القضايا والتصدير والنسخة الاحتياطية", style = MaterialTheme.typography.bodySmall)
            } else if (combinedMode) {
                Text("• القضايا والإخطارات والسركي والتقارير والسجلات تعمل معًا", style = MaterialTheme.typography.bodySmall)
                Text("• تقارير الأسرة والمدني والجنح والاستئناف مع قوالب Word شخصية", style = MaterialTheme.typography.bodySmall)
                Text("• AI وOCR والصوت والخريطة مع مراجعة قبل الاعتماد", style = MaterialTheme.typography.bodySmall)
                Text("• نسخة اختبار مجمعة مستقلة عن النسخ القديمة", style = MaterialTheme.typography.bodySmall)
            } else if (reportsEnabled) {
                Text("• محرر تقارير مرن مع قوالب Word قابلة لإعادة الاستخدام", style = MaterialTheme.typography.bodySmall)
                Text("• كاميرا وصوت بجوار البنود النصية مع مراجعة قبل الاعتماد", style = MaterialTheme.typography.bodySmall)
                Text("• مساعد AI واقتراحات إدراج أو نسخ أو رفض", style = MaterialTheme.typography.bodySmall)
                Text("• تعلّم محلي من الصياغة بعد موافقة الخبير", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("• دفعة إخطارات لعدة قضايا مع الاحتفاظ باختياراتك", style = MaterialTheme.typography.bodySmall)
                Text("• إخطار رسمي ٢×٢ مع السركي والظرف وحافظة البريد", style = MaterialTheme.typography.bodySmall)
                Text("• قراءة الصور واستخراج البيانات بـ OCR عربي", style = MaterialTheme.typography.bodySmall)
                Text("• نسخة احتياطية مشفرة واسترجاع كامل للبيانات", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SystemCard(title: String, subtitle: String, icon: ImageVector, actionLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ElevatedCard(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(icon, null, modifier = Modifier.padding(9.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            Text(actionLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun UtilityCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
