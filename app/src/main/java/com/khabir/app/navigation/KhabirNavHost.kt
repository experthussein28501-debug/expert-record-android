package com.khabir.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.khabir.agenda.AgendaScreen
import com.khabir.app.BuildConfig
import com.khabir.app.data.auth.EntryGateStore
import com.khabir.app.presentation.archive.ArchiveHomeScreen
import com.khabir.app.presentation.auth.LoginScreen
import com.khabir.app.presentation.cases.CaseFormScreen
import com.khabir.app.presentation.cases.CaseListScreen
import com.khabir.app.presentation.home.CompleteHomeScreen
import com.khabir.app.presentation.notifications.CompleteNotificationBatchScreen
import com.khabir.app.presentation.registers.RegisterScreen
import com.khabir.app.presentation.reports.ReportsHubScreen
import com.khabir.app.presentation.settings.BackupScreen
import com.khabir.app.presentation.settings.ExpertProfileScreen
import com.khabir.app.presentation.templates.TemplateAwareReportScreen
import com.khabir.app.presentation.templates.TemplateAwareWorkMinutesScreen
import com.khabir.app.presentation.workminutes.WorkMinutesHubScreen

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CASE_LIST = "cases"
    const val CASE_FORM = "cases/form?caseId={caseId}"
    const val NOTIFICATIONS = "notifications"
    const val REGISTERS = "registers"
    const val AGENDA = "agenda"
    const val EXPERT_PROFILE = "settings/expert-profile"
    const val BACKUP = "settings/backup"
    const val REPORTS_HUB = "reports"
    const val REPORT_EDITOR = "reports/edit?reportId={reportId}&caseId={caseId}"
    const val WORK_MINUTES_HUB = "work-minutes"
    const val WORK_MINUTES_EDITOR = "work-minutes/edit?recordId={recordId}&caseId={caseId}"

    fun caseForm(caseId: Long) = "cases/form?caseId=$caseId"
    fun reportForCase(caseId: Long) = "reports/edit?reportId=0&caseId=$caseId"
    fun independentReport(reportId: Long = 0L) = "reports/edit?reportId=$reportId&caseId=0"
    fun workMinutesForCase(caseId: Long) = "work-minutes/edit?recordId=0&caseId=$caseId"
    fun independentWorkMinutes(recordId: Long = 0L) = "work-minutes/edit?recordId=$recordId&caseId=0"
}

@Composable
fun KhabirNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val entryGate = remember(context) { EntryGateStore(context.applicationContext) }
    val moduleMode = BuildConfig.MODULE_MODE
    val archiveOnly = moduleMode == "ARCHIVE"
    val notificationsEnabled = notificationsEnabledFor(moduleMode)
    val reportsEnabled = reportsEnabledFor(moduleMode)
    val startDestination = remember { if (entryGate.hasPassedGate()) Routes.HOME else Routes.LOGIN }

    fun enterApp(markPassed: () -> Unit) {
        markPassed()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.LOGIN) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onSkip = { enterApp(entryGate::markSkipped) },
                onGoogleSuccess = { enterApp(entryGate::markGoogleSignedIn) },
                onActivationSuccess = { enterApp(entryGate::markActivated) }
            )
        }

        composable(Routes.HOME) {
            if (archiveOnly) {
                ArchiveHomeScreen(
                    onNewCase = { navController.navigate(Routes.caseForm(0L)) },
                    onOpenCases = { navController.navigate(Routes.CASE_LIST) },
                    onOpenRegister = { navController.navigate(Routes.REGISTERS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) }
                )
            } else {
                CompleteHomeScreen(
                    onOpenCases = { navController.navigate(Routes.CASE_LIST) },
                    onNewCase = { navController.navigate(Routes.caseForm(0L)) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenReports = { navController.navigate(Routes.REPORTS_HUB) },
                    onOpenRegisters = { navController.navigate(Routes.REGISTERS) },
                    onOpenWorkMinutes = { navController.navigate(Routes.WORK_MINUTES_HUB) },
                    onOpenAgenda = { navController.navigate(Routes.AGENDA) },
                    onOpenExpertProfile = { navController.navigate(Routes.EXPERT_PROFILE) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    notificationsEnabled = notificationsEnabled,
                    reportsEnabled = reportsEnabled
                )
            }
        }

        composable(Routes.CASE_LIST) {
            CaseListScreen(
                onBack = { navController.popBackStack() },
                onOpenCase = { id -> navController.navigate(Routes.caseForm(id)) },
                onNewCase = { navController.navigate(Routes.caseForm(0L)) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenExpertProfile = { navController.navigate(Routes.EXPERT_PROFILE) },
                onOpenRegisters = { navController.navigate(Routes.REGISTERS) },
                onOpenWorkMinutes = { navController.navigate(Routes.WORK_MINUTES_HUB) },
                showNotificationsAction = notificationsEnabled
            )
        }

        composable(
            route = Routes.CASE_FORM,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType; defaultValue = 0L })
        ) {
            CaseFormScreen(
                onBack = { navController.popBackStack() },
                onOpenReport = { savedCaseId -> navController.navigate(Routes.reportForCase(savedCaseId)) },
                onOpenWorkMinutes = { savedCaseId -> navController.navigate(Routes.workMinutesForCase(savedCaseId)) },
                showReportAction = reportsEnabled
            )
        }

        if (notificationsEnabled) {
            composable(Routes.NOTIFICATIONS) {
                CompleteNotificationBatchScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.AGENDA) { AgendaScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.EXPERT_PROFILE) {
            ExpertProfileScreen(
                onBack = { navController.popBackStack() },
                onShowLoginAgain = {
                    entryGate.showGateAgain()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.BACKUP) { BackupScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.REGISTERS) { RegisterScreen(onBack = { navController.popBackStack() }) }

        if (reportsEnabled) {
            composable(Routes.REPORTS_HUB) {
                ReportsHubScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRegisteredReport = { caseId -> navController.navigate(Routes.reportForCase(caseId)) },
                    onOpenSavedReport = { reportId -> navController.navigate(Routes.independentReport(reportId)) },
                    onStartIndependentReport = { navController.navigate(Routes.independentReport()) }
                )
            }
            composable(
                route = Routes.REPORT_EDITOR,
                arguments = listOf(
                    navArgument("reportId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("caseId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) {
                TemplateAwareReportScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.WORK_MINUTES_HUB) {
            WorkMinutesHubScreen(
                onBack = { navController.popBackStack() },
                onOpenRegisteredRecord = { caseId -> navController.navigate(Routes.workMinutesForCase(caseId)) },
                onOpenSavedRecord = { recordId -> navController.navigate(Routes.independentWorkMinutes(recordId)) },
                onStartIndependentRecord = { navController.navigate(Routes.independentWorkMinutes()) }
            )
        }

        composable(
            route = Routes.WORK_MINUTES_EDITOR,
            arguments = listOf(
                navArgument("recordId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("caseId") { type = NavType.LongType; defaultValue = 0L }
            )
        ) {
            TemplateAwareWorkMinutesScreen(onBack = { navController.popBackStack() })
        }
    }
}

internal fun notificationsEnabledFor(moduleMode: String): Boolean =
    moduleMode in setOf("COMBINED", "NOTIFICATIONS", "SIRKIS")

internal fun reportsEnabledFor(moduleMode: String): Boolean =
    moduleMode in setOf("COMBINED", "REPORTS")
