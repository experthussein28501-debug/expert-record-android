package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.CaseValidationError
import org.junit.Assert.assertEquals
import org.junit.Test

class CaseListViewModelTest {
    @Test
    fun `validation errors have clear Arabic import reasons`() {
        assertEquals("رقم الدعوى غير موجود", CaseListViewModel.caseValidationReason(CaseValidationError.MissingCaseNo))
        assertEquals("سنة الدعوى غير موجودة", CaseListViewModel.caseValidationReason(CaseValidationError.MissingCaseYear))
        assertEquals("سنة الدعوى يجب أن تكون أربعة أرقام صحيحة", CaseListViewModel.caseValidationReason(CaseValidationError.InvalidCaseYear))
        assertEquals("المحكمة غير موجودة", CaseListViewModel.caseValidationReason(CaseValidationError.MissingCourt))
    }
}
