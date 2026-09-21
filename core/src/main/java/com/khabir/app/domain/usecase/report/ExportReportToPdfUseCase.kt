package com.khabir.app.domain.usecase.report

import android.net.Uri
import com.khabir.app.domain.model.Report
import com.khabir.app.domain.model.ReportCoverFields
import com.khabir.app.domain.model.ReportCalculationCodec
import com.khabir.app.domain.model.orderedTemplateSections
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.DocumentExportRepository
import com.khabir.app.domain.repository.ExpertProfileRepository
import java.io.File
import javax.inject.Inject

class ExportReportToPdfUseCase @Inject constructor(
    private val caseRepository: CaseRepository,
    private val expertProfileRepository: ExpertProfileRepository,
    private val exportRepository: DocumentExportRepository
) {
    sealed class Result {
        data class Success(val uri: Uri) : Result()
        data object MissingCaseData : Result()
        data object MissingExpertProfile : Result()
    }

    suspend operator fun invoke(report: Report): Result {
        val profile = expertProfileRepository.get() ?: return Result.MissingExpertProfile
        val linkedCase = report.caseId?.let { caseRepository.getById(it) }
        val cover = when {
            linkedCase != null -> com.khabir.app.domain.model.ReportCaseSnapshot.cover(report, linkedCase, profile)
            report.caseNo.isNotBlank() && report.caseYear.isNotBlank() && report.court.isNotBlank() -> ReportCoverFields.from(report, profile)
            else -> return Result.MissingCaseData
        }

        val wording = reportCoverWording(cover, report.templateId, report.templateName)
        val descriptor = reportCaseDescriptor(cover)
        val caseIdentity = reportCaseIdentity(cover)
        val runningHeader = if (linkedCase != null) {
            "تقرير في $caseIdentity"
        } else {
            cover.manualHeader.trim()
        }

        val coverFields = buildList {
            add("الوزارة" to "وزارة العدل")
            add("القطاع" to cover.ministryOrSector)
            add("الإدارة" to cover.department)
            add("الخبير" to cover.expertName)
            add("رقم الدعوى" to "${cover.caseNo} لسنة ${cover.caseYear}")
            add("المحكمة" to descriptor)
            add("صيغة رقم القضية" to wording.caseIntro)
            add("تسمية الطرف الأول" to wording.firstPartyLabel)
            add("تسمية الطرف الثاني" to wording.secondPartyLabel)
            add("نمط الغلاف" to if (wording.compactCover) "مختصر" else "كامل")
            if (runningHeader.isNotBlank()) add("رأس التقرير" to runningHeader)

            val firstParty = if (wording.criminal) "النيابة العامة" else cover.plaintiffsSummary
            val secondParty = if (wording.criminal) {
                cover.defendantsSummary.ifBlank { cover.partiesSummary }
            } else {
                cover.defendantsSummary
            }

            if (firstParty.isNotBlank()) add("المرفوعة من" to firstParty)
            if (secondParty.isNotBlank()) add("ضد" to secondParty)
            if (cover.incomingNo.isNotBlank()) {
                val incoming = if (cover.incomingYear.isNotBlank()) {
                    "رقم ${cover.incomingNo} لسنة ${cover.incomingYear}"
                } else {
                    "رقم ${cover.incomingNo}"
                }
                add("الوارد" to incoming)
            }
            if (!wording.criminal && cover.plaintiffsSummary.isBlank() && cover.defendantsSummary.isBlank() && cover.partiesSummary.isNotBlank()) {
                add("المرفوعة من" to cover.partiesSummary)
            }
        }

        val sections = report.orderedTemplateSections(reportFinalResultHeading(cover)) + listOf(
            "تاريخ إيداع التقرير" to (report.depositDate?.toString() ?: ""),
            "هذه نتيجة أعمالنا نقدمها لعدالة المحكمة" to "الخبير / ${cover.expertName}"
        ).filter { it.second.isNotBlank() }

        val safeCaseNo = cover.caseNo.ifBlank { "جديد" }
        val safeCaseYear = cover.caseYear.ifBlank { "بدون_سنة" }
        return Result.Success(
            exportRepository.exportReportAsPdf(
                "تقرير_قضية_${safeCaseNo}_${safeCaseYear}",
                "تقريــــــــــر",
                coverFields,
                sections,
                File(report.siteSketchPath).takeIf { report.siteSketchPath.isNotBlank() && it.isFile }
            )
        )
    }
}
