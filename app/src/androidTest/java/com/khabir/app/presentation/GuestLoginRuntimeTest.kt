package com.khabir.app.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khabir.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestLoginRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun localModeExplainsPersistenceAndCanBeCancelled() {
        compose.onNodeWithText("تسجيل الدخول باستخدام Google").assertIsDisplayed()
        compose.onNodeWithText("فتح النسخة التجريبية").assertDoesNotExist()
        compose.onNodeWithText("استخدام بدون حساب — بدون مدة انتهاء").performClick()
        compose.onNodeWithText("استخدام محلي بدون حساب").assertIsDisplayed()
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("تسجيل الدخول باستخدام Google").assertIsDisplayed()
        compose.onNodeWithText("القضايا").assertDoesNotExist()
    }
}
