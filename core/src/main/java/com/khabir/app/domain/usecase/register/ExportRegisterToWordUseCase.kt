package com.khabir.app.domain.usecase.register

import android.net.Uri
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.RegisterFilter
import com.khabir.app.domain.model.toArabicIndicDigits
import com.khabir.app.domain.repository.DocumentExportRepository
import javax.inject.Inject

class ExportRegisterToWordUseCase @Inject constructor(private val exportRepository: DocumentExportRepository) {
    suspend operator fun invoke(filter: RegisterFilter, cases: List<Case>): Uri {
        val columns = listOf("رقم الدعوى","السنة","المحكمة","النوع","تاريخ الوارد","الخصوم")
        val rows = cases.map { c ->
            val ordered = c.parties.sortedBy { it.orderIndex }
            val plaintiffs = ordered.filter { it.role.isPlaintiff && it.fullName.isNotBlank() }
            val defendants = ordered.filter { it.role.isDefendant && it.fullName.isNotBlank() }
            val parties = listOf(
                sideSummary("المدعي", plaintiffs),
                sideSummary("المدعى عليه", defendants)
            ).filter(String::isNotBlank).joinToString(" | ")
            listOf(
                c.caseNo.toArabicIndicDigits(),
                c.caseYear.toArabicIndicDigits(),
                c.court,
                c.caseType,
                c.incomingDate.toString().toArabicIndicDigits(),
                parties
            )
        }
        val subtitle = buildString {
            filter.caseType?.let { append("النوع: $it  ") }
            filter.court?.let { append("المحكمة: $it  ") }
            if (filter.fromDate != null || filter.toDate != null) {
                append("الفترة: ${filter.fromDate?.toString()?.toArabicIndicDigits() ?: "—"} إلى ${filter.toDate?.toString()?.toArabicIndicDigits() ?: "—"}")
            }
        }.ifBlank { null }
        return exportRepository.exportTitledTableAsWord("سجل_القضايا","سجل القضايا",subtitle,columns,rows)
    }

    private fun sideSummary(label: String, parties: List<com.khabir.app.domain.model.Party>): String {
        if (parties.isEmpty()) return ""
        val first = parties.first().reportDisplayName
        val name = if (parties.size > 1 && !first.contains("وآخرين")) "$first وآخرين" else first
        return "$label: $name"
    }
}

