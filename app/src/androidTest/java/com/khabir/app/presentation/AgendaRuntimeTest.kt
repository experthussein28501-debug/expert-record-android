package com.khabir.app.presentation

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khabir.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AgendaRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun fullPageWritingSavesAndReopensOnDevice() {
        compose.onNodeWithText("كود التفعيل").assertDoesNotExist()
        snapshot("01-login")
        compose.onNodeWithText("فتح النسخة التجريبية").performClick()
        compose.onNodeWithText("الأجندة").performScrollTo().performClick()
        compose.onNodeWithContentDescription("اليوم").performClick()
        snapshot("02-agenda-day")
        compose.onNodeWithText("اضغط للكتابة في صفحة كاملة").performClick()
        compose.onNodeWithTag("agenda-ruled-editor").performClick().performTextInput("مراجعة المستندات\nتدوين نتيجة المعاينة\nمتابعة موعد الجلسة")
        compose.onNodeWithTag("agenda-save").assertIsDisplayed()
        snapshot("03-agenda-keyboard")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("agenda-ruled-editor").assertTextContains("مراجعة المستندات", substring = true)
        compose.onNodeWithTag("agenda-save").assertIsDisplayed()
        compose.onNodeWithTag("agenda-save").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithTag("agenda-save").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("اليوم").performClick()
        compose.onNode(hasText("مراجعة المستندات", substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        snapshot("04-agenda-saved")
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }
}
