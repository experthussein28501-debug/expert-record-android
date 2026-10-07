package com.khabir.app.domain.model

import java.time.LocalDate
import java.security.MessageDigest
import java.util.Base64

enum class LegalProcedureType(val label: String) {
    ORIGINAL("صحيفة أصلية"), COUNTERCLAIM("دعوى فرعية"), INCIDENTAL("طلب عارض"),
    INTERVENTION("تدخل هجومي"), REFERRAL("حكم إحالة"), APPEAL("حكم استئناف"),
    PRELIMINARY("حكم تمهيدي"), PREVIOUS_JUDGMENT("حكم محكمة سابقة"), EVIDENCE("مستند إثبات"), UNKNOWN("غير محدد");
    val subsidiary: Boolean get() = this in setOf(COUNTERCLAIM, INCIDENTAL, INTERVENTION)
    companion object {
        fun classify(title: String): LegalProcedureType = when {
            title.contains("تدخل") && (title.contains("هجومي") || title.contains("هجومى") || title.contains("هجومياً")) -> INTERVENTION
            title.contains("فرعية") -> COUNTERCLAIM
            title.contains("عارض") -> INCIDENTAL
            title.contains("تمهيدي") -> PRELIMINARY
            title.contains("حكم") && (title.contains("استئناف") || title.contains("طعن")) -> APPEAL
            title.contains("حكم") && (title.contains("إحالة") || title.contains("احالة") || title.contains("عدم اختصاص")) -> REFERRAL
            title.contains("حكم") -> PREVIOUS_JUDGMENT
            title.contains("صحيفة") || title.contains("عريضة") -> ORIGINAL
            title.contains("مستند") -> EVIDENCE
            else -> UNKNOWN
        }
    }
}

data class LegalCaseIdentity(val number: String, val year: String, val court: String, val type: String = "") {
    val complete: Boolean get() = number.isNotBlank() && year.isNotBlank() && court.isNotBlank()
    fun matches(other: LegalCaseIdentity): Boolean = complete && other.complete &&
        listOf(number, year, court).map(::legalNormalize) == listOf(other.number, other.year, other.court).map(::legalNormalize)
}

data class LegalSourceReference(val documentId: Int, val pages: List<Int>, val field: String, val quote: String)

data class LegalDocumentRecord(
    val id: Int, val pages: List<Int>, val type: LegalProcedureType,
    val procedureDate: LocalDate? = null, val serviceDate: LocalDate? = null,
    val judgmentDate: LocalDate? = null, val manualOrder: Int? = null,
    val claimant: String = "", val claimantRole: String = "",
    val identity: LegalCaseIdentity? = null, val previousIdentity: LegalCaseIdentity? = null,
    val currentIdentity: LegalCaseIdentity? = null, val referralProof: String = "",
    val explanation: String = "", val requests: String = "", val dispositive: String = "",
    val assignment: String = "", val rawText: String = "",
    val sources: List<LegalSourceReference> = emptyList(), val warnings: List<String> = emptyList()
) {
    // Page numbers and upload order do not turn a second photograph into a new procedure.
    val fingerprint: String get() = legalFingerprint(listOf(type.name, procedureDate, serviceDate,
        judgmentDate, claimant, claimantRole, identity, previousIdentity, currentIdentity, referralProof, explanation, requests, dispositive, assignment,
        if(listOf(explanation,requests,dispositive,assignment).all(String::isBlank)) rawText.lines().filterNot { it.trim().startsWith("الصفحات:") }.joinToString("\n") else "").joinToString("\u001f"))
    val provenReferral: Boolean get() = type == LegalProcedureType.REFERRAL && referralProof.isNotBlank()
}

fun legalNormalize(value: String): String = value.map { c -> when(c) {
    in '٠'..'٩' -> '0' + (c - '٠')
    in '۰'..'۹' -> '0' + (c - '۰')
    else -> c
} }.joinToString("").replace(Regex("[\u064B-\u065Fـ]"), "")
    .replace(Regex("[أإآ]"), "ا").replace('ى','ي').replace(Regex("\\s+"), " ").trim()
fun legalFingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Audit stays within the case's existing notes; no database migration or preference change. */
object LegalSourceAudit {
    const val LABEL = "سجل مصادر الاستخراج"
    fun userNotes(notes: String): String = notes.lineSequence().filterNot { it.trimStart().startsWith("$LABEL:") }.joinToString("\n")
    fun withUserNotes(notes: String, edited: String): String = (listOf(edited.trim()) + notes.lineSequence().filter { it.trimStart().startsWith("$LABEL:") }.toList()).filter(String::isNotBlank).joinToString("\n")
    fun encode(records: List<LegalDocumentRecord>): String = Base64.getEncoder().encodeToString(
        records.joinToString("\n\n") { r -> buildString {
            appendLine("[[SOURCE ${r.id}]]")
            appendLine("الصفحات: ${r.pages.joinToString()}")
            appendLine("نوع الإجراء: ${r.type.label}")
            appendLine("تاريخ الإجراء: ${r.procedureDate ?: "غير مؤكد"}")
            appendLine("تاريخ الإعلان: ${r.serviceDate ?: "غير مؤكد"}")
            appendLine("تاريخ الحكم: ${r.judgmentDate ?: "غير مؤكد"}")
            appendLine("بصمة المصدر: ${r.fingerprint}")
            appendLine("النص المراجع الأصلي:")
            append(r.rawText)
        } }.toByteArray(Charsets.UTF_8))
    fun decode(value: String): String = runCatching { String(Base64.getDecoder().decode(value), Charsets.UTF_8) }.getOrDefault("")
}
