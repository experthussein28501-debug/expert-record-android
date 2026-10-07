package com.khabir.agenda

import java.time.LocalDate

data class AgendaNoteCandidate(val date:LocalDate,val appointment:AgendaManualAppointment,val usedSelectedDate:Boolean=false)
data class AgendaNoteReview(val candidates:List<AgendaNoteCandidate>,val warnings:List<String>)

/** Only explicit dated/timed lines; no guessing appointments from general prose or drawings. */
object AgendaNoteAppointments {
    private val datePattern=Regex("(?<![0-9])(?:([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})|([0-9]{1,2})/([0-9]{1,2})(?:/([0-9]{4}))?)(?![0-9/])")
    private val timePattern=Regex("(?<![0-9])(?:الساعة\\s*:?\\s*)?([0-9]{1,2})(?::([0-9]{2}))?\\s*(صباح[ًاا]*|مساء[ًاا]*|ص|م|AM|PM)(?![\\p{L}\\p{N}])",RegexOption.IGNORE_CASE)
    private val clockPattern=Regex("(?<![0-9])(?:الساعة\\s*:?\\s*)?([0-9]{1,2}):([0-9]{2})(?![0-9])")
    private val bareTimePattern=Regex("الساعة\\s*:?\\s*([0-9]{1,2})(?![0-9])")
    fun parse(text:String,selected:LocalDate):AgendaNoteReview {
        val candidates=mutableListOf<AgendaNoteCandidate>();val warnings=mutableListOf<String>()
        text.lines().filter(String::isNotBlank).forEach { raw ->
            val line=raw.map {c -> if(c in '٠'..'٩') '0'+(c-'٠') else c}.joinToString("")
            val dates=datePattern.findAll(line).toList()
            if(dates.size>1) {warnings+="السطر به أكثر من تاريخ؛ حدده يدويًا: $raw";return@forEach}
            val match=dates.singleOrNull();val time=timePattern.find(line) ?: clockPattern.find(line) ?: bareTimePattern.find(line)
            if(match==null && time==null) return@forEach
            val date=if(match==null) selected else runCatching {
                if(match.groupValues[1].isNotBlank()) LocalDate.of(match.groupValues[1].toInt(),match.groupValues[2].toInt(),match.groupValues[3].toInt())
                else LocalDate.of(match.groupValues[6].toIntOrNull() ?: selected.year,match.groupValues[5].toInt(),match.groupValues[4].toInt())
            }.getOrNull()
            if(date==null) {warnings+="تاريخ غير صالح: $raw";return@forEach}
            if(time!=null) {
                val hour=time.groupValues[1].toInt();val minute=time.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val meridian=time.groupValues.getOrNull(3).orEmpty()
                if(minute !in 0..59 || hour !in (if(meridian.isBlank()) 0..23 else 1..12)) {warnings+="وقت غير صالح: $raw";return@forEach}
            }
            var title=raw
            listOfNotNull(match?.range,time?.range).sortedByDescending {it.first}.forEach {title=title.removeRange(it)}
            title=title.trim(' ','،','-','—','•',':').ifBlank {"موعد من الملاحظات"}
            candidates+=AgendaNoteCandidate(date,AgendaManualAppointment(title,time?.range?.let {raw.substring(it)}.orEmpty(),details=raw),match==null || (match.groupValues[1].isBlank() && match.groupValues[6].isBlank()))
        }
        return AgendaNoteReview(candidates.distinct(),warnings)
    }
    fun transferRecords(primary:AgendaDayNote,existing:Map<Long,AgendaDayNote>,transfers:List<AgendaNoteCandidate>):List<AgendaDayNote> {
        val grouped=transfers.groupBy {it.date}
        val targets=grouped.filterKeys {it!=primary.date}.map { (date,items) ->
            val old=existing[date.toEpochDay()] ?: AgendaDayNote(date)
            old.copy(manualAppointments=merge(old.manualAppointments,items.map {it.appointment}),updatedAt=primary.updatedAt)
        }
        val main=primary.copy(manualAppointments=merge(primary.manualAppointments,grouped[primary.date].orEmpty().map {it.appointment}))
        return targets+main
    }
    fun merge(existing:List<AgendaManualAppointment>,incoming:List<AgendaManualAppointment>):List<AgendaManualAppointment> = (existing+incoming).distinct()
}

/** Arabic calendar: swipe left goes forward, swipe right goes backward. */
internal fun agendaMonthSwipe(deltaX:Float,thresholdPx:Float):Int = when {
    deltaX<=-thresholdPx -> 1
    deltaX>=thresholdPx -> -1
    else -> 0
}
