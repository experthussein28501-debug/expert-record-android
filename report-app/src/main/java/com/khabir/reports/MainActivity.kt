package com.khabir.reports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.khabir.app.presentation.reports.ReportScreen
import com.khabir.app.presentation.reports.ReportsHubScreen
import com.khabir.app.presentation.settings.BackupScreen
import com.khabir.app.presentation.settings.ExpertProfileScreen
import com.khabir.app.presentation.theme.KhabirTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KhabirTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val nav = rememberNavController()
            Surface(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("وحدة التقارير", style = MaterialTheme.typography.headlineMedium)
                            Text("نسخة مستقلة للتجربة • 0.8.2")
                            Text("قوالب المدني والجنح والمدني المستأنف والاستئناف العالي، مع قالب Word خاص ومعاينة تفاعلية.")
                            Button(onClick = { nav.navigate("reports") }, modifier = Modifier.fillMaxWidth()) { Text("فتح التقارير") }
                            OutlinedButton(onClick = { nav.navigate("profile") }, modifier = Modifier.fillMaxWidth()) { Text("بيانات الخبير") }
                            OutlinedButton(onClick = { nav.navigate("backup") }, modifier = Modifier.fillMaxWidth()) { Text("استيراد وتصدير البيانات") }
                        }
                    }
                    composable("reports") { ReportsHubScreen(
                        onBack = { nav.popBackStack() },
                        onOpenRegisteredReport = { nav.navigate("edit?reportId=0&caseId=$it") },
                        onOpenSavedReport = { nav.navigate("edit?reportId=$it&caseId=0") },
                        onStartIndependentReport = { nav.navigate("edit?reportId=0&caseId=0") }
                    ) }
                    composable("edit?reportId={reportId}&caseId={caseId}", arguments = listOf(
                        navArgument("reportId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("caseId") { type = NavType.LongType; defaultValue = 0L }
                    )) { ReportScreen(onBack = { nav.popBackStack() }) }
                    composable("profile") { ExpertProfileScreen(onBack = { nav.popBackStack() }, onShowLoginAgain = { nav.popBackStack() }) }
                    composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
                }
            }
        } } }
    }
}
