package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expert_profile")
data class ExpertProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val ministryOrSector: String = "",
    val department: String = "",
    val expertName: String = "",
    val specialization: String = "",
    val officeAddress: String = "",
    val jobTitle: String = "",
    val attendancePhrase: String = "الرجاء الحضور إلى مكتب خبراء وزارة العدل",
    val phone: String = "",
    val email: String = "",
    val updatedAtEpochMillis: Long = 0L
) {
    companion object { const val SINGLE_ROW_ID = 1 }
}
