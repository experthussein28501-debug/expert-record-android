package com.khabir.app.domain.model

import java.time.LocalDate

data class Report(
    val id: Long = 0L,
    val caseId: Long? = null,
    val caseNo: String = "",
    val caseYear: String = "",
    val court: String = "",
    val manualHeader: String = "",
    val templateId: String = ReportTemplateCatalog.civil.id,
    val templateName: String = ReportTemplateCatalog.civil.name,
    val templateSectionsSpec: String = "",
    val customSectionContentsSpec: String = "",
    val partiesSummary: String = "",
    val subjectOfCase: String = "",
    val assignment: String = "",
    val proceedings: String = "",
    val partyStatements: String = "",
    val witnessStatements: String = "",
    val inspection: String = "",
    val documentsSubmitted: String = "",
    val facts: String = "",
    val research: String = "",
    val technicalOpinion: String = "",
    val calculationsTable: String = "",
    /** App-private PNG created by the location-sketch editor, if the user attached one. */
    val siteSketchPath: String = "",
    val conclusion: String = "",
    val attachmentsNote: String = "",
    val depositDate: LocalDate? = null,
    val updatedAt: Long = 0L
)

fun Report.orderedTemplateSections(finalResultHeading: String = "النتيجة النهائية"): List<Pair<String, String>> {
    val template = ReportTemplateCodec.decode(templateId, templateName, templateSectionsSpec)
    val customContents = ReportCustomSectionCodec.decode(customSectionContentsSpec)
    return template.orderedSections().filter { it.enabled }.mapNotNull { section ->
        val content = when (section.id) {
            "subject" -> subjectOfCase
            "assignment" -> assignment
            "proceedings" -> proceedings
            "statements" -> partyStatements
            "witnesses" -> witnessStatements
            "inspection" -> inspection
            "documents" -> documentsSubmitted
            "facts" -> facts
            "research" -> research
            "calculations" -> ReportCalculationCodec.exportText(calculationsTable)
            "conclusion" -> conclusion
            "attachments" -> attachmentsNote
            else -> customContents[section.id].orEmpty()
        }
        // The selected template is the report's structure. Keep every enabled
        // heading even before its body is written, so Word/PDF mirrors its
        // section order instead of silently collapsing parts of the template.
        val title = if (section.id == "conclusion" && section.title == "النتيجة النهائية") finalResultHeading else section.title
        title to (section.headingLines.filter(String::isNotBlank) + content).joinToString("\n")
    }
}

data class ReportCoverFields(
    val court: String,
    val caseNo: String,
    val caseYear: String,
    val incomingNo: String,
    val incomingYear: String,
    val expertName: String,
    val ministryOrSector: String,
    val department: String,
    val plaintiffsSummary: String,
    val defendantsSummary: String,
    val partiesSummary: String,
    val caseType: String = "",
    val manualHeader: String = ""
) {
    companion object {
        fun from(case: Case, profile: ExpertProfile): ReportCoverFields {
            val plaintiffs = case.parties.filter { it.role.isPlaintiff }
            val defendants = case.parties.filter { it.role.isDefendant }
            val estateOrGuardianship = isEstateOrGuardianship(case.caseType, case.court)

            return ReportCoverFields(
                court = case.court,
                caseNo = case.caseNo,
                caseYear = case.caseYear,
                incomingNo = case.incomingNo,
                incomingYear = case.incomingDate.year.toString(),
                expertName = profile.expertName,
                ministryOrSector = profile.ministryOrSector,
                department = profile.department,
                plaintiffsSummary = if (estateOrGuardianship) fullPartySummary(plaintiffs) else coverPartySummary(plaintiffs),
                defendantsSummary = if (estateOrGuardianship) fullPartySummary(defendants) else coverPartySummary(defendants),
                partiesSummary = case.parties.filter { it.role != PartyRole.LAWYER }.joinToString("، ") { "${it.reportDisplayName} (${it.role.arabicLabel})" },
                caseType = case.caseType,
                manualHeader = ""
            )
        }

        /**
         * الغلاف العادي يعرض أول اسم فقط من كل جهة، ثم «وآخرين» عند وجود باقي الخصوم.
         * إذا كان أول اسم فقط «بصفته» تظهر بعد اسمه. صفة أي اسم تالٍ لا تظهر في الغلاف.
         */
        internal fun coverPartySummary(parties: List<Party>): String {
            val ordered = parties.sortedBy { it.orderIndex }.filter { it.fullName.isNotBlank() }
            if (ordered.isEmpty()) return ""
            val first = ordered.first().reportDisplayName
            if (ordered.size == 1 || first.contains("وآخرين")) return first
            return "$first وآخرين"
        }

        /** Estate/guardianship covers keep their specific all-names behavior. */
        internal fun fullPartySummary(parties: List<Party>): String =
            parties.sortedBy { it.orderIndex }.map { it.reportDisplayName }.filter { it.isNotBlank() }.joinToString("، ")

        internal fun isEstateOrGuardianship(caseType: String, court: String): Boolean {
            val source = "$caseType $court".lowercase()
            return listOf(
                "تركة", "تركات", "ميراث",
                "وصاية", "وصايه", "قاصر", "قصر",
                "نيابة شؤون الأسرة", "نيابة شئون الأسرة",
                "نيابة شؤون الاسرة", "نيابة شئون الاسرة"
            ).any(source::contains)
        }

        fun from(report: Report, profile: ExpertProfile): ReportCoverFields = ReportCoverFields(
            court = report.court,
            caseNo = report.caseNo,
            caseYear = report.caseYear,
            incomingNo = "",
            incomingYear = "",
            expertName = profile.expertName,
            ministryOrSector = profile.ministryOrSector,
            department = profile.department,
            plaintiffsSummary = report.partiesSummary,
            defendantsSummary = "",
            partiesSummary = report.partiesSummary,
            caseType = report.court,
            manualHeader = report.manualHeader
        )
    }
}
