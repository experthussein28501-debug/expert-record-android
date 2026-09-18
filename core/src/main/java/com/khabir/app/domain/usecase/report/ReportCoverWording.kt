package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.ReportCoverFields

internal enum class ReportCoverKind {
    CRIMINAL,
    ESTATE,
    GUARDIANSHIP,
    PROSECUTION,
    CIVIL
}

internal data class ReportCoverWording(
    val kind: ReportCoverKind,
    val caseIntro: String,
    val headerCaseLabel: String,
    val firstPartyLabel: String,
    val secondPartyLabel: String,
    val criminal: Boolean = false,
    val compactCover: Boolean = false
)

/**
 * Wording rules for the first page only. The case-number line is intentionally unified
 * as "فى الدعوى رقم" for every report type. The actual classification (جنح، مدني كلي،
 * مدني جزئي، مدني مستأنف، شؤون الأسرة...) is written after the number and year from
 * the case data saved at intake.
 */
internal fun reportCoverWording(cover: ReportCoverFields, templateId: String = "", templateName: String = ""): ReportCoverWording {
    val source = "${cover.caseType} ${cover.court} $templateId $templateName".lowercase()
    val criminal = listOf("جنح", "جنحة", "الجنحه", "الجنحة").any(source::contains)
    val estate = listOf("تركة", "تركات", "ميراث").any(source::contains)
    val guardianship = listOf(
        "وصاية", "وصايه", "قاصر", "قصر",
        "نيابة شؤون الأسرة", "نيابة شئون الأسرة",
        "نيابة شؤون الاسرة", "نيابة شئون الاسرة"
    ).any(source::contains)
    val prosecution = source.contains("نيابة") && !criminal && !estate && !guardianship

    return when {
        criminal -> ReportCoverWording(
            kind = ReportCoverKind.CRIMINAL,
            caseIntro = "فى الدعوى رقم",
            headerCaseLabel = "الدعوى",
            firstPartyLabel = "المرفوعــة من",
            secondPartyLabel = "ضـــــد",
            criminal = true,
            compactCover = true
        )
        guardianship -> ReportCoverWording(
            kind = ReportCoverKind.GUARDIANSHIP,
            caseIntro = "فى الدعوى رقم",
            headerCaseLabel = "الدعوى",
            firstPartyLabel = "تركة المرحوم",
            secondPartyLabel = "بوصاية",
            compactCover = true
        )
        estate -> ReportCoverWording(
            kind = ReportCoverKind.ESTATE,
            caseIntro = "فى الدعوى رقم",
            headerCaseLabel = "الدعوى",
            firstPartyLabel = "تركة المرحوم",
            secondPartyLabel = "ضـــــد",
            compactCover = true
        )
        prosecution -> ReportCoverWording(
            kind = ReportCoverKind.PROSECUTION,
            caseIntro = "فى الدعوى رقم",
            headerCaseLabel = "الدعوى",
            firstPartyLabel = "المرفوعــة من",
            secondPartyLabel = "ضـــــد",
            compactCover = true
        )
        else -> ReportCoverWording(
            kind = ReportCoverKind.CIVIL,
            caseIntro = "فى الدعوى رقم",
            headerCaseLabel = "الدعوى",
            firstPartyLabel = "المرفوعــة من",
            secondPartyLabel = "ضـــــد",
            compactCover = false
        )
    }
}

/**
 * Produces the suffix printed after case number/year using the intake data itself.
 * Examples: "جنح مركز كوم أمبو", "مدني جزئي أسوان", "مدني كلي كوم أمبو",
 * "مدني مستأنف أسوان", "شؤون الأسرة نصر النوبة".
 */
internal fun reportCaseDescriptor(cover: ReportCoverFields): String {
    val type = cover.caseType.trim()
    val court = cover.court.trim()
    if (type.isBlank()) return court
    if (court.isBlank()) return type

    val normalizedType = type.replace("محكمة", "").trim()
    val normalizedCourt = court.replace("محكمة", "").trim()
    return when {
        normalizedCourt.contains(normalizedType, ignoreCase = true) -> normalizedCourt
        normalizedType.contains(normalizedCourt, ignoreCase = true) -> normalizedType
        else -> "$normalizedType $normalizedCourt".trim()
    }
}

/** One authoritative case identity reused in the running header and final result heading. */
internal fun reportCaseIdentity(cover: ReportCoverFields): String {
    val descriptor = reportCaseDescriptor(cover)
    return "الدعوى رقم ${cover.caseNo} لسنة ${cover.caseYear}${if (descriptor.isBlank()) "" else " $descriptor"}"
}

internal fun reportFinalResultHeading(cover: ReportCoverFields): String =
    "النتيجة النهائية في ${reportCaseIdentity(cover)}"

