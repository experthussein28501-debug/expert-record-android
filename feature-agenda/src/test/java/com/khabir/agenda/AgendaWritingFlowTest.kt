package com.khabir.agenda

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgendaWritingFlowTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 9, 20)

    @Test fun tappingPenBoardSavesASingleDot() {
        var saved = emptyList<AgendaStroke>()
        compose.setContent { MaterialTheme { AgendaDayDialog(AgendaDaySummary(date), {}, { _, strokes, _, _ -> saved = strokes }) } }
        compose.onNodeWithTag("agenda-writing-board").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithText("حفظ اليوم", substring = true).performClick()
        compose.runOnIdle { assertEquals(1, saved.size); assertEquals(1, saved.single().points.size) }
    }

    @Test fun penToolsAreHiddenUntilRequestedAndNoShapesAreOffered() {
        compose.setContent { MaterialTheme { AgendaDayDialog(AgendaDaySummary(date), {}, { _, _, _, _ -> }) } }
        compose.onNodeWithText("رفيع").assertDoesNotExist()
        compose.onNodeWithText("أدوات القلم").performClick()
        compose.onNodeWithText("رفيع").assertIsDisplayed()
        compose.onNodeWithText("عريض").assertIsDisplayed()
        listOf("مثلث", "مربع", "دائرة", "سهم").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun expandedEditorReturnsTextToDayAndSavesIt() {
        var saved = ""
        compose.setContent { MaterialTheme { AgendaDayDialog(AgendaDaySummary(date), {}, { text, _, _, _ -> saved = text }) } }
        compose.onNodeWithText("اضغط للكتابة في صفحة كاملة").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("مراجعة الدعوى يوم الأحد")
        compose.onNodeWithText("تم — العودة لليوم").performClick()
        compose.onNodeWithText("مراجعة الدعوى يوم الأحد").assertIsDisplayed()
        compose.onNodeWithText("حفظ اليوم", substring = true).performClick()
        compose.runOnIdle { assertEquals("مراجعة الدعوى يوم الأحد", saved) }
    }

    @Test fun fullPageEditorHasVisibleSaveAndKeepsText() {
        var saved = ""
        compose.setContent { MaterialTheme { AgendaDayDialog(AgendaDaySummary(date), {}, { text, _, _, _ -> saved = text }) } }
        compose.onNodeWithText("اضغط للكتابة في صفحة كاملة").performClick()
        compose.onNodeWithTag("agenda-ruled-editor").performTextInput("المستند الأول\nالمستند الثاني")
        compose.onNodeWithTag("agenda-save").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("المستند الأول\nالمستند الثاني", saved) }
    }

    @Test fun closingDirtyDayRequiresExplicitDecision() {
        var closed = false
        compose.setContent { MaterialTheme { AgendaDayDialog(AgendaDaySummary(date), { closed = true }, { _, _, _, _ -> }) } }
        compose.onNodeWithText("اضغط للكتابة في صفحة كاملة").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("ملاحظة غير محفوظة")
        compose.onNodeWithText("تم — العودة لليوم").performClick()
        compose.onNodeWithText("إغلاق").performClick()
        compose.onNodeWithText("حفظ تغييرات اليوم؟").assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, closed) }
        compose.onNodeWithText("تجاهل التغييرات").performClick()
        compose.runOnIdle { assertEquals(true, closed) }
    }
}
