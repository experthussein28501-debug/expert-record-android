package com.khabir.app.domain.model

import java.nio.charset.StandardCharsets
import java.util.Base64

enum class ReportTemplateKind {
    CIVIL,
    MISDEMEANOR,
    APPEAL,
    HIGH_APPEAL,
    FAMILY,
    CUSTOM,
    FREE
}

data class ReportSectionDefinition(
    val id: String,
    val title: String,
    val headingLines: List<String> = emptyList(),
    val enabled: Boolean = true,
    val removable: Boolean = true,
    val orderIndex: Int = 0
)

data class ReportTemplate(
    val id: String,
    val name: String,
    val kind: ReportTemplateKind,
    val sections: List<ReportSectionDefinition>,
    val isBuiltIn: Boolean,
    val verifiedFromUserReports: Boolean,
    val referenceReport: String = ""
) {
    fun orderedSections(): List<ReportSectionDefinition> = sections.sortedBy { it.orderIndex }

    fun renameSection(sectionId: String, title: String): ReportTemplate = copy(
        sections = sections.map { section ->
            if (section.id == sectionId) section.copy(title = title.trim()) else section
        }
    )

    fun addHeadingLine(sectionId: String, value: String = ""): ReportTemplate = copy(
        sections = sections.map { section ->
            if (section.id == sectionId) section.copy(headingLines = section.headingLines + value) else section
        }
    )

    fun updateHeadingLine(sectionId: String, index: Int, value: String): ReportTemplate = copy(
        sections = sections.map { section ->
            if (section.id != sectionId || index !in section.headingLines.indices) section
            else section.copy(headingLines = section.headingLines.toMutableList().also { it[index] = value })
        }
    )

    fun removeHeadingLine(sectionId: String, index: Int): ReportTemplate = copy(
        sections = sections.map { section ->
            if (section.id != sectionId || index !in section.headingLines.indices) section
            else section.copy(headingLines = section.headingLines.filterIndexed { lineIndex, _ -> lineIndex != index })
        }
    )

    fun setSectionEnabled(sectionId: String, enabled: Boolean): ReportTemplate = copy(
        sections = sections.map { if (it.id == sectionId) it.copy(enabled = enabled) else it }
    )

    fun deleteSection(sectionId: String): ReportTemplate = copy(
        sections = sections.filterNot { it.id == sectionId && it.removable }.reindex()
    )

    fun addSection(title: String, afterSectionId: String? = null): ReportTemplate {
        val ordered = orderedSections().toMutableList()
        val insertionIndex = afterSectionId
            ?.let { id -> ordered.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
            ?: ordered.size
        val uniqueId = "custom_${System.nanoTime()}"
        ordered.add(insertionIndex, ReportSectionDefinition(uniqueId, title.trim(), orderIndex = insertionIndex))
        return copy(sections = ordered.reindex())
    }

    fun moveSection(sectionId: String, targetIndex: Int): ReportTemplate {
        val ordered = orderedSections().toMutableList()
        val currentIndex = ordered.indexOfFirst { it.id == sectionId }
        if (currentIndex < 0) return this
        val section = ordered.removeAt(currentIndex)
        ordered.add(targetIndex.coerceIn(0, ordered.size), section)
        return copy(sections = ordered.reindex())
    }

    fun duplicateAsCustom(newId: String, newName: String): ReportTemplate = copy(
        id = newId,
        name = newName.trim(),
        kind = ReportTemplateKind.CUSTOM,
        sections = orderedSections().mapIndexed { index, section ->
            section.copy(id = "${newId}_$index", orderIndex = index)
        },
        isBuiltIn = false,
        verifiedFromUserReports = false,
        referenceReport = referenceReport
    )
}

private fun List<ReportSectionDefinition>.reindex(): List<ReportSectionDefinition> =
    mapIndexed { index, section -> section.copy(orderIndex = index) }

object ReportTemplateCatalog {
    val civil: ReportTemplate = ReportTemplate(
        id = "built_in_civil",
        name = "مدني",
        kind = ReportTemplateKind.CIVIL,
        sections = commonLegalSections(),
        isBuiltIn = true,
        verifiedFromUserReports = true,
        referenceReport = "الدعوى رقم 146 لسنة 2025 مدني جزئي نصر النوبة — نموذج Drive"
    )

    val family: ReportTemplate = ReportTemplate(
        "drive_family_estate",
        "أحوال شخصية / شئون الأسرة — تركات وقصر",
        ReportTemplateKind.FAMILY,
        listOf(
            ReportSectionDefinition("subject", "الموضوع"),
            ReportSectionDefinition("assignment", "المأمورية"),
            ReportSectionDefinition("proceedings", "مباشرة المأمورية"),
            ReportSectionDefinition("statements", "الأقوال", listOf("أقوال الوصي/الوصية أو الولي", "أقوال باقي أصحاب الشأن")),
            ReportSectionDefinition("inspection", "المعاينة على الطبيعة"),
            ReportSectionDefinition(
                "documents",
                "بحث المستندات",
                listOf("المستندات المرفقة بملف الدعوى", "المستندات المقدمة من الوصي/الولي", "الاطلاع لدى الجهات")
            ),
            ReportSectionDefinition("witnesses", "أقوال الشهود", enabled = false),
            ReportSectionDefinition("facts", "بيان عناصر التركة والورثة", enabled = false),
            ReportSectionDefinition("research", "البحث"),
            ReportSectionDefinition("calculations", "حساب نصيب القصر والريع", enabled = false),
            ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false)
        ).reindex(),
        isBuiltIn = true,
        verifiedFromUserReports = true,
        referenceReport = "نموذج تركات/قصر من تقارير المستخدم على Drive"
    )

    val misdemeanor: ReportTemplate = ReportTemplate(
        "drive_misdemeanor", "جنح — من تقارير الخبير", ReportTemplateKind.MISDEMEANOR,
        listOf(
            ReportSectionDefinition("subject", "الموضوع"),
            ReportSectionDefinition("assignment", "المأمورية"),
            ReportSectionDefinition("proceedings", "مباشرة المأمورية"),
            ReportSectionDefinition("statements", "أقوال المتهم"),
            ReportSectionDefinition("facts", "أقوال محرر محضر المخالفة"),
            ReportSectionDefinition("inspection", "المعاينة على الطبيعة"),
            ReportSectionDefinition("witnesses", "أقوال الشهود"),
            ReportSectionDefinition("documents", "فحص المستندات", enabled = false),
            ReportSectionDefinition("research", "البحث", enabled = false),
            ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false)
        ).reindex(), true, true,
        referenceReport = "تقارير الجنح المرجعية على Drive — غلاف مختصر ومسار أقوال/معاينة"
    )
    val appeal: ReportTemplate = ReportTemplate(
        "drive_civil_appeal", "مدني مستأنف — من تقارير الخبير", ReportTemplateKind.APPEAL,
        listOf(
            ReportSectionDefinition("subject", "الموضوع"),
            ReportSectionDefinition("assignment", "المأمورية"),
            ReportSectionDefinition("proceedings", "مباشرة المأمورية"),
            ReportSectionDefinition("statements", "أقوال طرفي الخصومة", listOf("أقوال المستأنفين", "أقوال المستأنف ضدهم")),
            ReportSectionDefinition("facts", "الحكم المستأنف والتقارير السابقة"),
            ReportSectionDefinition("inspection", "المعاينة على الطبيعة"),
            ReportSectionDefinition(
                "documents",
                "بحث المستندات",
                listOf("المستندات المرفقة بملف الدعوى", "المستندات المقدمة من المستأنفين", "المستندات المقدمة من المستأنف ضدهم")
            ),
            ReportSectionDefinition("witnesses", "أقوال الشهود", enabled = false),
            ReportSectionDefinition("research", "البحث والرد على المأمورية"),
            ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false)
        ).reindex(), true, true,
        referenceReport = "رقم 305 لسنة 33ق مدني استئناف أسوان — نموذج Drive"
    )

    // تقارير الاستئناف العالي المرجعية تُظهر أن مسار الطعن والإعادة
    // والتقارير السابقة جزء أصيل من البنية، لذلك له قالب مستقل.
    val highAppeal: ReportTemplate = ReportTemplate(
        "drive_high_appeal", "استئناف عالٍ — من تقارير الخبير", ReportTemplateKind.HIGH_APPEAL,
        listOf(
            ReportSectionDefinition("subject", "الموضوع"),
            ReportSectionDefinition("assignment", "المأمورية"),
            ReportSectionDefinition("proceedings", "مباشرة المأمورية"),
            ReportSectionDefinition("statements", "أقوال طرفي الخصومة", listOf("أقوال المستأنفين", "أقوال المستأنف ضدهم")),
            ReportSectionDefinition("facts", "الحكم المستأنف ومسار الطعن والتقارير السابقة"),
            ReportSectionDefinition("inspection", "المعاينة على الطبيعة"),
            ReportSectionDefinition(
                "documents",
                "بحث المستندات والتقارير السابقة",
                listOf("مستندات المستأنفين", "مستندات المستأنف ضدهم", "ما ثبت من التقارير السابقة")
            ),
            ReportSectionDefinition("witnesses", "أقوال الشهود", enabled = false),
            ReportSectionDefinition("research", "البحث والرد على المأمورية"),
            ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false)
        ).reindex(), true, true,
        referenceReport = "الدعوى رقم 871 لسنة 34ق استئناف عالي قنا مأمورية أسوان — نموذج Drive"
    )

    val free: ReportTemplate = ReportTemplate(
        id = "built_in_free",
        name = "تقرير حر",
        kind = ReportTemplateKind.FREE,
        sections = listOf(ReportSectionDefinition("facts", "نص التقرير الحر", removable = false)),
        isBuiltIn = true,
        verifiedFromUserReports = true
    )

    val detailedCivil: ReportTemplate = ReportTemplate(
        id = "library_civil_detailed",
        name = "مدني مفصل — من تقارير الخبير",
        kind = ReportTemplateKind.CIVIL,
        sections = listOf(
            ReportSectionDefinition("subject", "الموضوع"),
            ReportSectionDefinition("assignment", "المأمورية"),
            ReportSectionDefinition("proceedings", "مباشرة المأمورية"),
            ReportSectionDefinition("statements", "أقوال طرفي الخصومة", listOf("أقوال المدعي", "أقوال المدعى عليهم")),
            ReportSectionDefinition("inspection", "المعاينة على الطبيعة"),
            ReportSectionDefinition("documents", "بحث المستندات", listOf("مستندات المدعي", "مستندات المدعى عليهم")),
            ReportSectionDefinition("witnesses", "أقوال الشهود"),
            ReportSectionDefinition("research", "البحث"),
            ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false)
        ).reindex(),
        isBuiltIn = true,
        verifiedFromUserReports = true,
        referenceReport = "الدعوى رقم 146 لسنة 2025 مدني جزئي نصر النوبة — نموذج Drive"
    )
    val conciseCivil: ReportTemplate = detailedCivil.copy(
        id = "library_civil_concise",
        name = "مدني مختصر — من تقارير الخبير",
        sections = detailedCivil.sections.filter { it.id in setOf("subject", "assignment", "proceedings", "statements", "conclusion") }.reindex()
    )
    val all: List<ReportTemplate> = listOf(civil, detailedCivil, conciseCivil, family, misdemeanor, appeal, highAppeal, free)

    private fun commonLegalSections(): List<ReportSectionDefinition> = listOf(
        ReportSectionDefinition("subject", "الموضوع"),
        ReportSectionDefinition("assignment", "المأمورية"),
        ReportSectionDefinition("proceedings", "مباشرة المأمورية"),
        ReportSectionDefinition("statements", "أقوال طرفي التداعي", listOf("أقوال المدعي", "أقوال المدعى عليهم")),
        ReportSectionDefinition("witnesses", "سماع الشهود", enabled = false),
        ReportSectionDefinition("inspection", "المعاينة على الطبيعة"),
        ReportSectionDefinition("documents", "بحث المستندات"),
        ReportSectionDefinition("facts", "الوقائع والملاحظات", enabled = false),
        ReportSectionDefinition("research", "البحث"),
        ReportSectionDefinition("calculations", "الحسابات والجداول", enabled = false),
        ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false),
        ReportSectionDefinition("attachments", "ملاحظات المرفقات", enabled = false)
    ).reindex()
}

/** Stores the exact editable section snapshot with each report without a JSON dependency. */
object ReportTemplateCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(template: ReportTemplate): String = template.orderedSections().joinToString("\n") { section ->
        listOf(
            encodeText(section.id),
            encodeText(section.title),
            section.enabled.toString(),
            section.removable.toString(),
            section.orderIndex.toString(),
            section.headingLines.joinToString(",", transform = ::encodeText)
        ).joinToString("\t")
    }

    fun decode(templateId: String, templateName: String, spec: String): ReportTemplate {
        val base = ReportTemplateCatalog.all.firstOrNull { it.id == templateId }
            ?: ReportTemplate(templateId.ifBlank { "custom_saved" }, templateName.ifBlank { "قالب خاص" }, ReportTemplateKind.CUSTOM, emptyList(), false, false)
        if (spec.isBlank()) return base.copy(name = templateName.ifBlank { base.name })
        val sections = runCatching {
            spec.lineSequence().filter(String::isNotBlank).map { line ->
                val parts = line.split('\t')
                require(parts.size >= 6)
                ReportSectionDefinition(
                    id = decodeText(parts[0]),
                    title = decodeText(parts[1]),
                    enabled = parts[2].toBooleanStrict(),
                    removable = parts[3].toBooleanStrict(),
                    orderIndex = parts[4].toInt(),
                    headingLines = parts[5].takeIf(String::isNotBlank)?.split(',')?.map(::decodeText).orEmpty()
                )
            }.toList()
        }.getOrElse { return base }
        // "الرأي الفني" is not an approved report item. Strip it from old saved
        // templates as well as from newly created reports.
        return base.copy(
            name = templateName.ifBlank { base.name },
            sections = sections.filterNot { it.id == "opinion" || it.title.trim() == "الرأي الفني" }.reindex()
        )
    }

    private fun encodeText(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decodeText(value: String): String = String(decoder.decode(value), StandardCharsets.UTF_8)
}

/** Stores text belonging to user-created report sections. */
object ReportCustomSectionCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(values: Map<String, String>): String = values.entries
        .filter { it.key.isNotBlank() }
        .joinToString("\n") { (key, value) -> "${encodeText(key)}\t${encodeText(value)}" }

    fun decode(spec: String): Map<String, String> = runCatching {
        spec.lineSequence().filter(String::isNotBlank).associate { line ->
            val parts = line.split('\t', limit = 2)
            require(parts.size == 2)
            decodeText(parts[0]) to decodeText(parts[1])
        }
    }.getOrDefault(emptyMap())

    private fun encodeText(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decodeText(value: String): String = String(decoder.decode(value), StandardCharsets.UTF_8)
}
