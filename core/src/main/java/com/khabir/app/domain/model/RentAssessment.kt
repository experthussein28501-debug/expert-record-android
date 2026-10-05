package com.khabir.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RentRatePeriod(val label:String) { MONTH("قيمة شهرية"), YEAR("قيمة سنوية") }
data class RentAssessmentInput(val claimedStart:String,val claimedEnd:String,val actualStart:String,val actualEnd:String,
    val rate:String,val quantity:String="1",val shareNumerator:String="1",val shareDenominator:String="1",val period:RentRatePeriod=RentRatePeriod.YEAR,val basis:String="")
data class RentAssessmentResult(val start:LocalDate,val end:LocalDate,val units:BigDecimal,val amount:BigDecimal,val explanation:String)
object RentAssessment {
    fun calculate(input:RentAssessmentInput):RentAssessmentResult {
        fun date(s:String)=requireNotNull(DocumentExamination.date(s)) { "تاريخ غير صالح" }
        fun number(s:String):BigDecimal { val normalized=legalNormalize(s).replace("٫",".").replace("٬","").replace(",","");return requireNotNull(normalized.toBigDecimalOrNull()) { "قيمة حسابية غير صالحة" } }
        val cs=date(input.claimedStart);val ce=date(input.claimedEnd);val actualStart=date(input.actualStart);val actualEnd=date(input.actualEnd)
        require(cs<=ce && actualStart<=actualEnd) { "بداية المدة بعد نهايتها" }
        val start=maxOf(cs,actualStart);val end=minOf(ce,actualEnd)
        require(start<=end) { "لا توجد مدة مشتركة بين المطالبة ووضع اليد المثبت" }
        val rate=number(input.rate);val quantity=number(input.quantity);val numerator=number(input.shareNumerator);val denominator=number(input.shareDenominator)
        require(rate>=BigDecimal.ZERO && quantity>BigDecimal.ZERO && numerator>=BigDecimal.ZERO && denominator>BigDecimal.ZERO && numerator<=denominator) { "القيمة أو الكمية أو النصيب غير صالح" }
        require(input.basis.isNotBlank()) { "اذكر مستند أو أساس القيمة والمدة والنصيب قبل اعتماد الحساب" }
        val exclusive=end.plusDays(1)
        val unit=if(input.period==RentRatePeriod.MONTH) ChronoUnit.MONTHS else ChronoUnit.YEARS
        val whole=unit.between(start,exclusive)
        val cursor=if(input.period==RentRatePeriod.MONTH) start.plusMonths(whole) else start.plusYears(whole)
        val next=if(input.period==RentRatePeriod.MONTH) cursor.plusMonths(1) else cursor.plusYears(1)
        val remainder=ChronoUnit.DAYS.between(cursor,exclusive)
        val units=whole.toBigDecimal()+remainder.toBigDecimal().divide(ChronoUnit.DAYS.between(cursor,next).toBigDecimal(),12,RoundingMode.HALF_UP)
        val amount=rate.multiply(quantity).multiply(units).multiply(numerator).divide(denominator,2,RoundingMode.HALF_UP)
        val explanation="المدة المشتركة: $start إلى $end شاملًا الطرفين. ${input.period.label}: ${rate.toPlainString()} × الكمية ${quantity.toPlainString()} × عدد الفترات ${units.stripTrailingZeros().toPlainString()} × النصيب ${numerator.toPlainString()}/${denominator.toPlainString()} = ${amount.toPlainString()}. كسر الشهر/السنة بنسبة الأيام إلى أيام الفترة التقويمية؛ يلزم اعتماد ملاءمة هذا الأساس. أساس البيانات: ${input.basis}"
        return RentAssessmentResult(start,end,units,amount,explanation)
    }
}
