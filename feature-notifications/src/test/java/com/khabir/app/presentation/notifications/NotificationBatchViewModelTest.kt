package com.khabir.app.presentation.notifications

import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.usecase.notification.RecipientSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * اختبارات لمنطق التحقق من بيانات المخاطَب اليدوي ومفتاح منع التكرار —
 * كانت وحدة الإخطارات بدون أي تغطية اختبارية سابقًا رغم أنها كانت مصدر
 * أكثر من باج فعلي في هذا التطبيق قبل كده.
 */
class NotificationBatchViewModelTest {

    @Test
    fun `complete manual recipient data passes validation`() {
        val state = NotificationScreenUiState(
            manualCaseNo = "123",
            manualCaseYear = "2026",
            manualCourt = "محكمة أسوان الابتدائية",
            manualFirstName = "أحمد",
            manualRestName = "محمد علي"
        )
        assertNull(NotificationBatchViewModel.manualRecipientValidationError(state))
    }

    @Test
    fun `missing case number is rejected`() {
        val state = NotificationScreenUiState(
            manualCaseNo = "",
            manualCaseYear = "2026",
            manualCourt = "محكمة أسوان الابتدائية",
            manualFirstName = "أحمد"
        )
        assertNotNull(NotificationBatchViewModel.manualRecipientValidationError(state))
    }

    @Test
    fun `missing both name parts is rejected even with complete case info`() {
        val state = NotificationScreenUiState(
            manualCaseNo = "123",
            manualCaseYear = "2026",
            manualCourt = "محكمة أسوان الابتدائية",
            manualFirstName = "",
            manualRestName = ""
        )
        assertNotNull(NotificationBatchViewModel.manualRecipientValidationError(state))
    }

    @Test
    fun `only a rest name with no first name still passes`() {
        // يعكس سلوك الشاشة: أول الاسم يُمسح بعد كل حفظ بينما تكمل بقية الاسم -
        // فلازم التحقق يقبل حالة "بقية الاسم موجودة والأول فاضي".
        val state = NotificationScreenUiState(
            manualCaseNo = "123",
            manualCaseYear = "2026",
            manualCourt = "محكمة أسوان الابتدائية",
            manualFirstName = "",
            manualRestName = "محمد علي"
        )
        assertNull(NotificationBatchViewModel.manualRecipientValidationError(state))
    }

    @Test
    fun `recipient key ignores case and extra whitespace so duplicates are caught`() {
        val a = manualRecipient(firstName = "أحمد", restName = "  محمد   علي ")
        val b = manualRecipient(firstName = "أحمد", restName = "محمد علي")
        assertEquals(
            NotificationBatchViewModel.recipientKey(a),
            NotificationBatchViewModel.recipientKey(b)
        )
    }

    @Test
    fun `recipient key differs when role differs`() {
        val plaintiff = manualRecipient(role = PartyRole.PLAINTIFF)
        val defendant = manualRecipient(role = PartyRole.DEFENDANT)
        org.junit.Assert.assertNotEquals(
            NotificationBatchViewModel.recipientKey(plaintiff),
            NotificationBatchViewModel.recipientKey(defendant)
        )
    }

    private fun manualRecipient(
        firstName: String = "أحمد",
        restName: String = "محمد علي",
        role: PartyRole = PartyRole.DEFENDANT,
        address: String = "أسوان"
    ) = RecipientSelection.Manual(
        caseNo = "123",
        caseYear = "2026",
        court = "محكمة أسوان الابتدائية",
        firstName = firstName,
        restName = restName,
        role = role,
        address = address
    )
}
