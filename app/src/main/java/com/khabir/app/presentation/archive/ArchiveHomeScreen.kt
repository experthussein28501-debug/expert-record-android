package com.khabir.app.presentation.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.presentation.home.HomeViewModel

private val ArchiveNavy = Color(0xFF071B33)
private val ArchiveGold = Color(0xFFD6B45A)

@Composable
fun ArchiveHomeScreen(
    onNewCase: () -> Unit,
    onOpenCases: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArchiveNavy)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = ArchiveGold)
        Text(
            text = "أرشيف القضايا",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "الوحدة الأولى — نسخة اختبار مستقلة",
            style = MaterialTheme.typography.bodyLarge,
            color = ArchiveGold
        )
        Text(
            text = "القضايا المسجلة: ${state.casesCount}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Spacer(Modifier.height(6.dp))
        ArchiveActionCard(
            title = "تسجيل قضية جديدة",
            description = "رقم الوارد والدعوى والخصوم والعناوين وبيانات القضية",
            icon = Icons.Filled.AddCircle,
            onClick = onNewCase
        )
        ArchiveActionCard(
            title = "سجل القضايا",
            description = "بحث وعرض وتعديل بيانات القضايا المحفوظة",
            icon = Icons.Filled.ListAlt,
            onClick = onOpenCases
        )
        ArchiveActionCard(
            title = "السجل الجامع",
            description = "عرض السجل المتفق عليه وتصديره",
            icon = Icons.Filled.Folder,
            onClick = onOpenRegister
        )
        ArchiveActionCard(
            title = "تصدير أو استعادة بيانات الوحدة",
            description = "نقل نسخة مشفرة بين أجهزة الاختبار لحين إضافة المزامنة",
            icon = Icons.Filled.Backup,
            onClick = onOpenBackup
        )

        Spacer(Modifier.weight(1f))
        Text(
            text = "هذه النسخة لا تحتوي على الإخطارات أو التقارير أو السيركيس.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun ArchiveActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ArchiveNavy)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = ArchiveNavy)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArchiveNavy.copy(alpha = 0.72f)
                )
            }
        }
    }
}
