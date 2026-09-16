package com.khabir.app.domain.model

data class ExpertProfile(
    val ministryOrSector: String = "",
    val department: String = "",
    val expertName: String = "",
    val specialization: String = "",
    val officeAddress: String = "",
    val jobTitle: String = "",
    val attendancePhrase: String = "الرجاء الحضور إلى مكتب خبراء وزارة العدل",
    val phone: String = "",
    val email: String = ""
) {
    val isComplete: Boolean
        get() = expertName.isNotBlank() &&
            jobTitle.isNotBlank() &&
            department.isNotBlank() &&
            officeAddress.isNotBlank()
}
