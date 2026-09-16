package com.khabir.notifications

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
import androidx.navigation.compose.*
import com.khabir.app.presentation.notifications.NotificationBatchScreen
import com.khabir.app.presentation.settings.ExpertProfileScreen
import com.khabir.app.presentation.settings.BackupScreen
import com.khabir.app.presentation.theme.KhabirTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhabirTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val nav = rememberNavController()
                Surface(Modifier.fillMaxSize()) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("وحدة الإخطارات", style = MaterialTheme.typography.headlineMedium)
                                Text("نسخة مستقلة للتجربة • 0.7.4")
                                Text("ابدأ بحفظ بيانات الخبير، ثم أدخل بيانات الدعوى والمخاطبين أو استورد نسخة بيانات محفوظة.")
                                Button(onClick = { nav.navigate("profile") }, modifier = Modifier.fillMaxWidth()) { Text("بيانات الخبير") }
                                Button(onClick = { nav.navigate("notices") }, modifier = Modifier.fillMaxWidth()) { Text("الإخطارات والسركي") }
                                OutlinedButton(onClick = { nav.navigate("backup") }, modifier = Modifier.fillMaxWidth()) { Text("استيراد وتصدير البيانات") }
                            }
                        }
                        composable("profile") { ExpertProfileScreen(onBack = { nav.popBackStack() }, onShowLoginAgain = { nav.popBackStack() }) }
                        composable("notices") { NotificationBatchScreen(onBack = { nav.popBackStack() }) }
                        composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            } }
        }
    }
}
