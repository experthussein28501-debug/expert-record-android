package com.khabir.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** No statutory rates: every rate, factor and documentary basis is supplied and reviewed. */
enum class AccountingPeriod(val label:String) { DAYS("أيام فعلية شاملة البداية والنهاية"), COUNT("عدد مستحق مثبت بالمستند") }
data class AccountingInput(
    val person:String, val category:String, val benefit:String, val start:String, val end:String,
    val rate:String, val count:String, val factor:String, val source:String,
    val period:AccountingPeriod=AccountingPeriod.DAYS
)
data class AccountingResult(val amount:BigDecimal,val explanation:String)
object AccountingAssessment {
    const val DRAFT_KEY="_accounting_draft_v1"
    val categories=listOf("عمالي","ضرائب","كشف حساب","أحوال شخصية","حسابي آخر")
    fun calculate(input:AccountingInput):AccountingResult {
        require(input.person.isNotBlank() && input.benefit.isNotBlank()) {"حدد المستحق وبند الحساب"}
        require(input.category in categories) {"حدد نوع القضية الحسابية"}
        require(input.source.isNotBlank()) {"أدخل القرار أو المستند المعتمد وقاعدة احتساب البند"}
        val start=LocalDate.parse(input.start);val end=LocalDate.parse(input.end)
        require(!end.isBefore(start)) {"نهاية الفترة تسبق بدايتها"}
        fun number(value:String):BigDecimal {
            val normalized=buildString {value.trim().forEach {c -> append(if(c in '٠'..'٩') ('0'+(c-'٠')) else if(c=='٫') '.' else c)}}
            return normalized.toBigDecimal().also {require(it>=BigDecimal.ZERO) {"القيم السالبة غير مقبولة"}}
        }
        val rate=number(input.rate);val factor=number(input.factor)
        require(factor>BigDecimal.ZERO) {"معامل الحساب يجب أن يكون موجبًا"}
        val units=if(input.period==AccountingPeriod.DAYS) BigDecimal(ChronoUnit.DAYS.between(start,end)+1) else number(input.count)
        val amount=rate.multiply(units).multiply(factor).setScale(2,RoundingMode.HALF_UP)
        return AccountingResult(amount,"${input.category} — ${input.person} — ${input.benefit}. الفترة: $start إلى $end. الأساس: ${input.source}. ${input.period.label}: $units × قيمة الوحدة $rate × معامل $factor = $amount. القيم والقواعد معتمدة يدويًا من الخبير.")
    }
    fun encode(input:AccountingInput):String = ReportCustomSectionCodec.encode(mapOf(
        "person" to input.person,"category" to input.category,"benefit" to input.benefit,"start" to input.start,
        "end" to input.end,"rate" to input.rate,"count" to input.count,"factor" to input.factor,"source" to input.source,"period" to input.period.name))
    fun decode(value:String):AccountingInput {
        val m=ReportCustomSectionCodec.decode(value)
        return AccountingInput(m["person"].orEmpty(),m["category"] ?: categories.first(),m["benefit"].orEmpty(),m["start"].orEmpty(),m["end"].orEmpty(),m["rate"].orEmpty(),m["count"].orEmpty(),m["factor"] ?: "1",m["source"].orEmpty(),AccountingPeriod.entries.firstOrNull {it.name==m["period"]} ?: AccountingPeriod.DAYS)
    }
    /** A different worker must receive fresh dates/rates/documentary facts. */
    fun changePerson(input:AccountingInput,person:String):AccountingInput =
        if(person==input.person) input else AccountingInput(person,input.category,input.benefit,"","","","","1","",input.period)
}
