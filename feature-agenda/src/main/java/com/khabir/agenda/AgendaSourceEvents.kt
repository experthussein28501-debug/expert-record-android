package com.khabir.agenda

import com.khabir.app.domain.model.AutomaticWorkMinutes
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.WorkMinutesRecord

/** Real appointments and dated minutes; print copies never create extra appointments. */
object AgendaSourceEvents {
    private fun label(number:String,year:String):String = when {
        number.isNotBlank() && year.isNotBlank()->"الدعوى $number لسنة $year"
        number.isNotBlank()->"الدعوى $number"
        else->"موعد قضية"
    }
    fun from(cases:List<Case>,batches:List<NotificationBatch>,minutes:List<WorkMinutesRecord>):List<AgendaEvent> = buildList {
        addAll(cases.mapNotNull {it.toHearingAgendaEventOrNull()})
        batches.filterNot {it.isReprint}.forEach {batch ->
            batch.recipients.filterNot {it.isAuthorityNotice}.map {Triple(it.caseNo,it.caseYear,it.court)}
                .filter {it.first.isNotBlank() || it.second.isNotBlank() || it.third.isNotBlank()}.distinct().forEach {(number,year,court)->
                    add(AgendaEvent(batch.appointmentDate,label(number,year),batch.appointmentTime,batch.appointmentLocation,
                        listOf(court,batch.requestedDocuments.takeIf(String::isNotBlank)?.let {"مستندات: $it"}).filterNotNull().filter(String::isNotBlank).joinToString(" — "),
                        AgendaEventSource.NOTIFICATION_APPOINTMENT,"notification:${batch.id}:$number:$year:$court"))
                }
        }
        minutes.forEach {record -> record.entries.forEach {entry ->
            val id=entry.automaticSourceKey.ifBlank {entry.number.toString()}
            entry.openingDate?.let {date -> add(AgendaEvent(date,label(record.caseNo,record.caseYear),entry.openingTime,record.court,
                "محضر أعمال رقم ${entry.number} — ${entry.bodyText.take(100)}",AgendaEventSource.WORK_MINUTES,"minutes:${record.id}:$id:opened"))}
            entry.scheduledFollowUpDate?.takeUnless { date ->
                entry.automaticSourceKey.startsWith("notification:") && (AutomaticWorkMinutes.isUnchanged(entry) || batches.any {batch ->
                    entry.automaticSourceKey=="notification:${batch.id}" && !batch.isReprint && batch.recipients.any {it.caseId==record.caseId} &&
                        date==batch.appointmentDate && entry.scheduledFollowUpTime==batch.appointmentTime && entry.scheduledFollowUpLocation==batch.appointmentLocation
                })
            }?.let {date ->
                add(AgendaEvent(date,label(record.caseNo,record.caseYear),entry.scheduledFollowUpTime,entry.scheduledFollowUpLocation.ifBlank {record.court},
                    "موعد تالٍ مثبت بمحضر الأعمال رقم ${entry.number}",AgendaEventSource.WORK_MINUTES,"minutes:${record.id}:$id:followup"))
            }
        }}
    }.distinctBy {it.importKey()}
}
