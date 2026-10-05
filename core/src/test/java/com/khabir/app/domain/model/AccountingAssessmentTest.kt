package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class AccountingAssessmentTest {
    private val input=AccountingInput("عامل القضية","عمالي","مكافأة","2024-02-28","2024-03-01","100","","0.5","قرار مقدم صفحة ٣: نصف قيمة الوحدة لكل يوم")
    @Test fun leapYearInclusiveActualDays() {assertEquals("150.00",AccountingAssessment.calculate(input).amount.toPlainString())}
    @Test fun reviewedCountIsNotGuessedFromDates() {
        assertEquals("625.00",AccountingAssessment.calculate(input.copy(period=AccountingPeriod.COUNT,count="12.5")).amount.toPlainString())
    }
    @Test fun arabicNumbersSupported() {assertEquals("150.00",AccountingAssessment.calculate(input.copy(rate="١٠٠",factor="٠٫٥")).amount.toPlainString())}
    @Test fun missingBasisFails() { assertThrows(IllegalArgumentException::class.java) {AccountingAssessment.calculate(input.copy(source=""))} }
    @Test fun reversedDatesFail() {assertThrows(IllegalArgumentException::class.java) {AccountingAssessment.calculate(input.copy(end="2023-01-01"))}}
    @Test fun invalidDatesFail() {assertThrows(Exception::class.java) {AccountingAssessment.calculate(input.copy(start="2024-02-30"))}}
    @Test fun nonnumericRateFailsInsteadOfZero() {assertThrows(Exception::class.java) {AccountingAssessment.calculate(input.copy(rate=""))}}
    @Test fun negativeValuesFail() {assertThrows(IllegalArgumentException::class.java) {AccountingAssessment.calculate(input.copy(rate="-1"))}}
    @Test fun zeroFactorFails() {assertThrows(IllegalArgumentException::class.java) {AccountingAssessment.calculate(input.copy(factor="0"))}}
    @Test fun newWorkerClearsPrivateFacts() {
        val changed=AccountingAssessment.changePerson(input,"عامل آخر")
        assertEquals("عامل آخر",changed.person);assertEquals("",changed.start);assertEquals("",changed.end)
        assertEquals("",changed.rate);assertEquals("",changed.source);assertEquals("",changed.count)
        assertThrows(Exception::class.java) {AccountingAssessment.calculate(changed)}
    }
    @Test fun sameWorkerPreservesDraft() {assertEquals(input,AccountingAssessment.changePerson(input,input.person))}
    @Test fun caseScopedDraftRoundTrip() {assertEquals(input,AccountingAssessment.decode(AccountingAssessment.encode(input)))}
    @Test fun auditableCalculationIncludesRuleAndName() {
        val text=AccountingAssessment.calculate(input).explanation
        assertTrue(text.contains(input.person));assertTrue(text.contains(input.source));assertTrue(text.contains("2024-02-28"));assertTrue(text.contains("150.00"))
    }
    @Test fun accountingTemplatesPersistAndExposeCalculations() {
        ReportTemplateCatalog.accounting.forEach { template ->
            val decoded=ReportTemplateCodec.decode(template.id,template.name,ReportTemplateCodec.encode(template))
            assertEquals(template,decoded)
            assertTrue(decoded.sections.single {it.id=="calculations"}.enabled)
            assertFalse(decoded.sections.single {it.id=="inspection"}.enabled)
        }
    }
}
