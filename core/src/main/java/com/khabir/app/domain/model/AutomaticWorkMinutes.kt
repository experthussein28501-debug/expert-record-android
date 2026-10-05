package com.khabir.app.domain.model

import java.security.MessageDigest
import java.time.LocalDate

/** Idempotent source-linked entries; any expert change protects the whole entry. */
object AutomaticWorkMinutes {
    private fun fingerprint(entry:WorkMinutesEntry):String = MessageDigest.getInstance("SHA-256")
        .digest(WorkMinutesCodec.encode(listOf(entry.copy(automaticSourceKey="",automaticSnapshot=""))).toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun isUnchanged(entry:WorkMinutesEntry):Boolean = entry.automaticSourceKey.isNotBlank() && entry.automaticSnapshot==fingerprint(entry)
    fun base(case:Case,existing:WorkMinutesRecord?):WorkMinutesRecord = existing ?: WorkMinutesRecord(
        caseId=case.id,caseNo=case.caseNo,caseYear=case.caseYear,
        court=listOf(case.caseType,case.court).filter(String::isNotBlank).distinct().joinToString(" "),
        plaintiffsSummary=ReportCoverFields.coverPartySummary(case.parties.filter {it.role.isPlaintiff}),
        defendantsSummary=ReportCoverFields.coverPartySummary(case.parties.filter {it.role.isDefendant}))
    private fun upsert(record:WorkMinutesRecord,key:String,candidate:WorkMinutesEntry?):WorkMinutesRecord {
        val old=record.entries.firstOrNull {it.automaticSourceKey==key}
        if(old!=null && !isUnchanged(old)) return record
        if(candidate==null) return record.copy(entries=record.entries.filterNot {it.automaticSourceKey==key})
        val numbered=candidate.copy(number=old?.number ?: ((record.entries.maxOfOrNull {it.number} ?: 0)+1),automaticSourceKey=key)
        val generated=numbered.copy(automaticSnapshot=fingerprint(numbered))
        return record.copy(entries=if(old==null) record.entries+generated else record.entries.map {if(it.automaticSourceKey==key) generated else it})
    }
    fun receipt(case:Case,existing:WorkMinutesRecord?):WorkMinutesRecord {
        val record=base(case,existing)
        val notes=LegalSourceAudit.userNotes(case.adminNotes).replace("أ","ا").replace("إ","ا")
        val redistributed=Regex("اعادة\\s+توزيع|بعد\\s+توزيع").containsMatchIn(notes)
        val entry=case.receiptDate?.let {WorkMinutesEntry(0,openingDate=it,bodyText=if(redistributed) WorkMinutesPhrases.RECEIVED_AFTER_REASSIGNMENT_DEFERRED else WorkMinutesPhrases.RECEIVED_FILE_DEFERRED)}
        return upsert(record,"receipt:${case.id}",entry)
    }
    fun scheduling(record:WorkMinutesRecord,batch:NotificationBatch,issuedDate:LocalDate):WorkMinutesRecord = upsert(record,"notification:${batch.id}",
        WorkMinutesEntry(0,openingDate=issuedDate,expertName=batch.expertName,
            bodyText="لإثبات تحديد يوم ${batch.appointmentDate} الساعة ${batch.appointmentTime.ifBlank { "غير محددة" }} ${batch.appointmentLocation} لمباشرة المأمورية، طبقًا للإخطار المحرر.",
            scheduledFollowUpDate=batch.appointmentDate,scheduledFollowUpTime=batch.appointmentTime,scheduledFollowUpLocation=batch.appointmentLocation))
}
