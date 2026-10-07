package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class RentAssessmentTest {
    private fun input()=RentAssessmentInput("01/01/2010","31/12/2020","01/01/2015","31/12/2017","100","2","1","2",RentRatePeriod.YEAR,"القيمة من مستند معتمد، المدة من المعاينة، والنصيب من إعلام الوراثة")
    @Test fun annualRentUsesActualShorterPeriodAreaAndShare() {
        val result=RentAssessment.calculate(input())
        assertEquals("2015-01-01",result.start.toString());assertEquals("2017-12-31",result.end.toString());assertEquals(0,result.units.compareTo(java.math.BigDecimal("3")));assertEquals("300.00",result.amount.toPlainString())
    }
    @Test fun monthlyBuildingRentUsesClaimedIntersectionNotAllOccupation() {
        val i=input().copy(claimedStart="01/01/2020",claimedEnd="31/12/2020",actualStart="01/01/2019",actualEnd="30/06/2020",rate="1000",quantity="1",shareNumerator="1",shareDenominator="1",period=RentRatePeriod.MONTH)
        assertEquals("6000.00",RentAssessment.calculate(i).amount.toPlainString())
    }
    @Test fun arabicDigitsAndLeapYearAndFractionAreExplicit() {
        val i=input().copy(claimedStart="١/١/٢٠٢٤",claimedEnd="٣١/١٢/٢٠٢٤",actualStart="١/١/٢٠٢٤",actualEnd="٣١/١٢/٢٠٢٤",rate="١٢٠٠",quantity="١",shareNumerator="١",shareDenominator="٣")
        assertEquals("400.00",RentAssessment.calculate(i).amount.toPlainString())
        val result=RentAssessment.calculate(i.copy(claimedEnd="30/06/2024",actualEnd="30/06/2024"))
        assertTrue(result.explanation.contains("كسر الشهر/السنة"));assertTrue(result.amount<java.math.BigDecimal("400"))
    }
    @Test(expected=IllegalArgumentException::class) fun noPeriodOverlapIsNotInvented() {RentAssessment.calculate(input().copy(actualStart="01/01/2024",actualEnd="31/12/2024"))}
    @Test(expected=IllegalArgumentException::class) fun missingBasisDoesNotGenerateCompletedCalculation() {RentAssessment.calculate(input().copy(basis=""))}
    @Test(expected=IllegalArgumentException::class) fun missingRateIsNotZeroByDefault() {RentAssessment.calculate(input().copy(rate=""))}
    @Test(expected=IllegalArgumentException::class) fun invalidShareDoesNotIncreaseTotal() {RentAssessment.calculate(input().copy(shareNumerator="3",shareDenominator="2"))}
    @Test(expected=IllegalArgumentException::class) fun impossibleDateIsRejected() {RentAssessment.calculate(input().copy(claimedStart="31/02/2010"))}
}
