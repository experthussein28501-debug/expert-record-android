package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.toArabicIndicDigits
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class CaseStatementType(
    val menuLabel: String,
    val titleLabel: String
) {
    CIVIL("القضايا المدنية", "القضايا المدنية"),
    FAMILY("قضايا الأحوال الشخصية", "قضايا الأحوال الشخصية"),
    CRIMINAL("قضايا الجنح", "قضايا الجنح"),
    HIGH_APPEAL("قضايا الاستئناف العالي", "قضايا الاستئناف العالي");

    fun matches(caseType: String): Boolean {
        val normalized = caseType.trim()
        return when (this) {
            // "مدني مستأنف" يظل ضمن بيان القضايا المدنية.
            // الاستئناف العالي فئة مستقلة ولا يُجمع معه المدني المستأنف.
            CIVIL -> normalized.contains("مدني", ignoreCase = true) &&
                !normalized.contains("استئناف عالي", ignoreCase = true) &&
                !normalized.contains("استئناف عالى", ignoreCase = true)
            FAMILY -> listOf("أحوال", "شؤون الأسرة", "شئون الأسرة", "أسرة", "وصاية", "مال")
                .any { normalized.contains(it, ignoreCase = true) }
            CRIMINAL -> normalized.contains("جنح", ignoreCase = true)
            HIGH_APPEAL -> normalized.contains("استئناف عالي", ignoreCase = true) ||
                normalized.contains("استئناف عالى", ignoreCase = true)
        }
    }
}

data class CaseStatementTable(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
) {
    fun asExcelRows(): List<List<String>> = buildList {
        add(listOf(title))
        add(emptyList())
        add(headers)
        addAll(rows)
    }
}

object CaseStatementTableBuilder {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun build(
        cases: List<Case>,
        type: CaseStatementType,
        fromDate: LocalDate,
        toDate: LocalDate
    ): CaseStatementTable {
        require(!toDate.isBefore(fromDate)) { "تاريخ النهاية يجب ألا يسبق تاريخ البداية" }

        val filtered = cases
            .asSequence()
            .filter { type.matches(it.caseType) }
            .filter { !it.incomingDate.isBefore(fromDate) && !it.incomingDate.isAfter(toDate) }
            .sortedWith(
                compareBy<Case> { incomingSortNumber(it.incomingNo) }
                    .thenBy { it.incomingNo }
                    .thenBy { it.incomingDate }
                    .thenBy { it.caseNo }
            )
            .toList()

        val title = "بيان ${type.titleLabel} طرف السيد الخبير مرتبة بترتيب الوارد من تاريخ ${fromDate.format(dateFormatter).toArabicIndicDigits()} حتى تاريخ ${toDate.format(dateFormatter).toArabicIndicDigits()}"
        val headers = listOf(
            "م",
            "رقم الوارد",
            "تاريخ الإحالة / الوارد",
            "رقم الدعوى",
            "السنة",
            "المحكمة / المأمورية",
            "أسماء الخصوم",
            "العناوين",
            "تاريخ استلام القضية",
            "تاريخ الحكم التمهيدي"
        )
        val rows = filtered.mapIndexed { index, case ->
            val orderedParties = case.parties.sortedBy { it.orderIndex }
            val plaintiffs = orderedParties.filter { it.role.isPlaintiff && it.fullName.isNotBlank() }
            val defendants = orderedParties.filter { it.role.isDefendant && it.fullName.isNotBlank() }
            val partyNames = listOf(
                partySideSummary("المدعي", plaintiffs),
                partySideSummary("المدعى عليه", defendants)
            ).filter(String::isNotBlank).joinToString(" | ")
            val addresses = orderedParties.joinToString(" | ") { party ->
                val name = party.fullName.ifBlank { party.role.arabicLabel }
                if (party.address.isBlank()) name else "$name: ${party.address}"
            }
            listOf(
                (index + 1).toString().toArabicIndicDigits(),
                case.incomingNo.toArabicIndicDigits(),
                case.incomingDate.format(dateFormatter).toArabicIndicDigits(),
                case.caseNo.toArabicIndicDigits(),
                case.caseYear.toArabicIndicDigits(),
                case.court,
                partyNames,
                addresses,
                case.receiptDate?.format(dateFormatter).orEmpty().toArabicIndicDigits(),
                case.preliminaryJudgmentDate?.format(dateFormatter).orEmpty().toArabicIndicDigits()
            )
        }

        return CaseStatementTable(title, headers, rows)
    }

    private fun partySideSummary(label: String, parties: List<com.khabir.app.domain.model.Party>): String {
        if (parties.isEmpty()) return ""
        val first = parties.first().reportDisplayName
        val name = if (parties.size > 1 && !first.contains("وآخرين")) "$first وآخرين" else first
        return "$label: $name"
    }

    private fun incomingSortNumber(value: String): Long {
        val western = value.map { ch ->
            when (ch) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        }.joinToString("")
        return Regex("[0-9]+").find(western)?.value?.toLongOrNull() ?: Long.MAX_VALUE
    }
}

