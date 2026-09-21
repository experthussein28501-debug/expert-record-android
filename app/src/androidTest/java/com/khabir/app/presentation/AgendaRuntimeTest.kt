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
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop com.android.launcher3").close()
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
        compose.onNodeWithTag("agenda-text-preview").assertIsDisplayed()
        compose.onNode(hasText("مراجعة المستندات", substring = true) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("agenda-ruled-editor").performTextReplacement((1..24).joinToString("\n") { "سطر المراجعة رقم $it" })
        compose.onNodeWithText("تم — العودة لليوم").performClick()
        compose.onNodeWithTag("agenda-text-preview").performTouchInput { swipeUp() }
        compose.onNodeWithTag("agenda-ruled-editor").assertDoesNotExist()
        snapshot("05-agenda-preview-scrolled")
        compose.onNodeWithTag("agenda-text-preview").performTouchInput { click(center) }
        compose.onNodeWithTag("agenda-ruled-editor").assertExists()
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val root = automation.rootInActiveWindow
        check(root?.findAccessibilityNodeInfosByText("isn't responding").isNullOrEmpty()) { "System ANR dialog obstructs the screenshot" }
        val image = checkNotNull(automation.takeScreenshot())
        try {
            val resolver = instrumentation.targetContext.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/expert-record-ui")
            }
            val uri = checkNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            checkNotNull(resolver.openOutputStream(uri)).use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
    }
}
