package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_batches")
data class NotificationBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appointmentDateEpochDay: Long,
    val appointmentTime: String,
    val appointmentLocation: String,
    val requestedDocuments: String = "",
    val expertNameSnapshot: String,
    val officeAddressSnapshot: String,
    val ministryOrSectorSnapshot: String = "وزارة العدل",
    val departmentSnapshot: String = "إدارة خبراء أسوان",
    val expertJobTitleSnapshot: String = "الخبير المحالة إليه المأمورية",
    val attendancePhraseSnapshot: String = "الرجاء الحضور إلى مكتب خبراء وزارة العدل",
    val createdAtEpochMillis: Long,
    val isReprint: Boolean = false,
    val sourceBatchId: Long? = null
)
