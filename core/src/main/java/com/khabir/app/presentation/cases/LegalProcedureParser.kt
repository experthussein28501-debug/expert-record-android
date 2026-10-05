package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.*
import java.time.LocalDate

/** Validates the labelled extraction contract, keeping unknowns separate from confirmed fields. */
object LegalProcedureParser {
    private val missing = setOf("...", "…", "[غير واضح]", "غير مذكور", "غير موجود", "لا يوجد", "غير مؤكد")
    fun field(text: String, label: String): String = Regex("(?m)^[ \\t]*${Regex.escape(label)}[ \\t]*[:：][ \\t]*([^\\n\\r]*)")
        .find(text)?.groupValues?.get(1)?.trim()?.takeUnless { it in missing }.orEmpty()
    fun block(text: String, label: String): String {
        val start = Regex("(?m)^[ \\t]*${Regex.escape(label)}[ \\t]*[:：][ \\t]*").find(text) ?: return ""
        val tail = text.substring(start.range.last + 1)
        val end = Regex("(?m)^[ \\t]*(?:نوع المستند|الصفحات|سبب التجميع|رقم .*|سنة .*|المحكمة.*|محكمة .*|تاريخ .*|ترتيب الإجراء|مقدم الإجراء|صفة مقدم الإجراء|موضوع الدعوى|شرح الدعوى|الطلبات الختامية|مأمورية الحكم التمهيدي|المأمورية|منطوق الحكم|دليل الإحالة|دليل .*|الخصم|المدعي|المدعى عليه|المحامي|ملاحظات|مرجع .*|سجل مصادر الاستخراج)\\s*[:：]").find(tail)
        return tail.substring(0, end?.range?.first ?: tail.length).trim().takeUnless { it in missing }.orEmpty()
    }
    fun parseDate(raw: String): LocalDate? {
        val value = legalNormalize(raw)
        val iso = Regex("(?<![0-9])([0-9]{4})[-/]([0-9]{1,2})[-/]([0-9]{1,2})(?![0-9])").find(value)
        val dmy = Regex("(?<![0-9])([0-9]{1,2})[-/]([0-9]{1,2})[-/]([0-9]{4})(?![0-9])").find(value)
        return runCatching { when {
            iso != null -> LocalDate.of(iso.groupValues[1].toInt(),iso.groupValues[2].toInt(),iso.groupValues[3].toInt())
            dmy != null -> LocalDate.of(dmy.groupValues[3].toInt(),dmy.groupValues[2].toInt(),dmy.groupValues[1].toInt())
            else -> null
        } }.getOrNull()
    }
    fun parse(id: Int, pages: List<Int>, raw: String, title: String = field(raw,"نوع المستند")): LegalDocumentRecord {
        val data = PetitionIntakeParser.parse(raw)
        val type = LegalProcedureType.classify(title)
        val warnings = mutableListOf<String>()
        fun date(vararg labels: String): LocalDate? {
            val supplied = labels.flatMap { label -> Regex("(?m)^\\s*${Regex.escape(label)}\\s*[:：]([^\\n]*)").findAll(raw).map { it.groupValues[1] }.toList() }
                .filter { it.trim().isNotBlank() && it.trim() !in missing }
            val dates = supplied.mapNotNull(::parseDate).distinct()
            if (supplied.any { parseDate(it) == null } || dates.size > 1) {
                warnings += "تاريخ ${labels.first()} غير مقروء أو متعارض؛ راجعه يدويًا"; return null
            }
            return dates.singleOrNull()
        }
        fun identity(prefix: String): LegalCaseIdentity? {
            val number = field(raw,"رقم الدعوى $prefix")
            val year = field(raw,"سنة الدعوى $prefix")
            val court = field(raw,"المحكمة $prefix").ifBlank { field(raw,"محكمة الدعوى $prefix") }
            if (listOf(number,year,court).all(String::isBlank)) return null
            return LegalCaseIdentity(number,year,court,field(raw,"نوع الدعوى $prefix"))
        }
        val ordinary = data.caseNo?.let { LegalCaseIdentity(it,data.caseYear.orEmpty(),data.court.orEmpty(),data.caseType.orEmpty()) }
        val previous = identity("السابقة")
        val current = identity("الحالية")
        val proof = block(raw,"دليل الإحالة").takeUnless {
            legalNormalize(it).let { t -> Regex("(?:لم|لن|لا)\\s+(?:تحل|تحال|يحكم|تقض|تثبت)|طلب\\s+.*احالة|ربما|احتمال").containsMatchIn(t) }
        }.orEmpty()
        val procedureDate = date("تاريخ رفع الصحيفة","تاريخ تقديم الإجراء","تاريخ الإجراء")
        if (type.subsidiary && procedureDate == null) warnings += "تاريخ تقديم ${type.label} غير مؤكد؛ راجع ترتيبها يدويًا ولا تستعمل تاريخ الجلسة بدلًا منه"
        if (type == LegalProcedureType.REFERRAL && proof.isBlank()) warnings += "الإحالة غير مثبتة بنص؛ لا تعتمد الربط بين الرقمين تلقائيًا"
        val subject = block(raw,"موضوع الدعوى").ifBlank { block(raw,"شرح الدعوى") }.ifBlank { data.subjectOfCase.orEmpty() }
        val requests = PetitionSubjectExtraction.cleanRequests(block(raw,"الطلبات الختامية").ifBlank { data.finalRequests.orEmpty() })
        val fields = listOf("موضوع الدعوى","الطلبات الختامية","منطوق الحكم","مأمورية الحكم التمهيدي","دليل الإحالة")
        return LegalDocumentRecord(id,pages,type,procedureDate,date("تاريخ الإعلان"),date("تاريخ الحكم","تاريخ حكم الإحالة"),
            field(raw,"ترتيب الإجراء").let(::legalNormalize).toIntOrNull()?.takeIf { it >= 0 },
            PartySummaries.names(field(raw,"مقدم الإجراء").split(',','،','|')).ifBlank { PartySummaries.names(data.parties.filter { it.role.isPlaintiff }.map { it.name }) },
            field(raw,"صفة مقدم الإجراء"),ordinary,previous,current,proof,subject,requests,block(raw,"منطوق الحكم"),
            data.preliminaryMission.orEmpty(),raw,
            fields.mapNotNull { label -> block(raw,label).takeIf(String::isNotBlank)?.let { LegalSourceReference(id,pages,label,it) } },
            (warnings + if (type == LegalProcedureType.ORIGINAL || type.subsidiary) data.subjectWarnings else emptyList()).distinct())
    }
}
