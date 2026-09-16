package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.Case
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

        val title = "بيان ${type.titleLabel} طرف السيد الخبير مرتبة بترتيب الوارد من تاريخ ${fromDate.format(dateFormatter)} حتى تاريخ ${toDate.format(dateFormatter)}"
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
            val partyNames = orderedParties.joinToString(" | ") { party ->
                val side = party.role.arabicLabel
                val capacity = if (party.withCapacity) " بصفته" else ""
                "$side: ${party.fullName}$capacity"
            }
            val addresses = orderedParties.joinToString(" | ") { party ->
                val name = party.fullName.ifBlank { party.role.arabicLabel }
                if (party.address.isBlank()) name else "$name: ${party.address}"
            }
            listOf(
                (index + 1).toString(),
                case.incomingNo,
                case.incomingDate.format(dateFormatter),
                case.caseNo,
                case.caseYear,
                case.court,
                partyNames,
                addresses,
                case.receiptDate?.format(dateFormatter).orEmpty(),
                case.preliminaryJudgmentDate?.format(dateFormatter).orEmpty()
            )
        }

        return CaseStatementTable(title, headers, rows)
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

