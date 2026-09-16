package com.khabir.app.domain.model

import java.time.LocalDate

data class NotificationRecipient(
    val id: Long = 0L,
    val caseId: Long?,
    val partyId: Long?,
    val caseNo: String,
    val caseYear: String,
    val court: String,
    val partyFirstName: String,
    val partyRestName: String,
    val partyRole: PartyRole,
    val partyAddress: String,
    val plaintiffsSummary: String = "",
    val defendantsSummary: String = "",
    val subjectOfCase: String = "",
    val preliminaryJudgmentDate: LocalDate? = null,
    val isAuthorityNotice: Boolean = false,
    val incomingNo: String = "",
    val withCapacity: Boolean = false
) {
    val rawFullName: String get() = "$partyFirstName $partyRestName".trim()
    val fullName: String get() = if (withCapacity && rawFullName.isNotBlank()) "$rawFullName بصفته" else rawFullName
    val storedRoleValue: String get() = if (withCapacity) "${partyRole.arabicLabel}|بصفته" else partyRole.arabicLabel
    val needsStateLawsuitsAuthorityNotice: Boolean get() = GOVERNMENT_MARKERS.any(fullName::contains)
    companion object {
        private val GOVERNMENT_MARKERS = listOf("وزير", "وزارة", "مدير", "مديرية", "محافظ", "هيئة", "مصلحة", "جامعة", "رئيس مجلس", "وحدة محلية")
    }
}

data class NotificationBatch(
    val id: Long = 0L,
    val appointmentDate: LocalDate,
    val appointmentTime: String,
    val appointmentLocation: String,
    val requestedDocuments: String = "",
    val expertName: String,
    val officeAddress: String,
    val ministryOrSector: String = "وزارة العدل",
    val department: String = "إدارة خبراء أسوان",
    val expertJobTitle: String = "الخبير المحالة إليه المأمورية",
    val attendancePhrase: String = "الرجاء الحضور إلى مكتب خبراء وزارة العدل",
    val recipients: List<NotificationRecipient> = emptyList(),
    val isReprint: Boolean = false,
    val sourceBatchId: Long? = null,
    val createdAt: Long = 0L
) {
    val sheetsNeeded: Int get() = (recipients.count { !it.isAuthorityNotice } + 3) / 4 + recipients.count { it.isAuthorityNotice }
}
