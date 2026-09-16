package com.khabir.app.domain.usecase.register

import android.net.Uri
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.RegisterFilter
import com.khabir.app.domain.repository.DocumentExportRepository
import javax.inject.Inject

class ExportRegisterToWordUseCase @Inject constructor(private val exportRepository: DocumentExportRepository) {
    suspend operator fun invoke(filter: RegisterFilter, cases: List<Case>): Uri {
        val columns = listOf("رقم الدعوى","السنة","المحكمة","النوع","تاريخ الوارد","الخصوم")
        val rows = cases.map { c -> listOf(c.caseNo,c.caseYear,c.court,c.caseType,c.incomingDate.toString(),c.parties.joinToString("، ") { "${it.fullName} (${it.role.arabicLabel})" }) }
        val subtitle = buildString { filter.caseType?.let { append("النوع: $it  ") }; filter.court?.let { append("المحكمة: $it  ") }; if (filter.fromDate != null || filter.toDate != null) append("الفترة: ${filter.fromDate ?: "—"} إلى ${filter.toDate ?: "—"}") }.ifBlank { null }
        return exportRepository.exportTitledTableAsWord("سجل_القضايا","سجل القضايا",subtitle,columns,rows)
    }
}

