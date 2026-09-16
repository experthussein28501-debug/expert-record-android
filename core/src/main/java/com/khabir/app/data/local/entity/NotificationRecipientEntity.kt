package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_recipients",
    foreignKeys = [ForeignKey(entity = NotificationBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("batchId")]
)
data class NotificationRecipientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: Long,
    val caseId: Long?,
    val partyId: Long?,
    val caseNoSnapshot: String,
    val caseYearSnapshot: String,
    val courtSnapshot: String,
    val partyFirstNameSnapshot: String,
    val partyRestNameSnapshot: String,
    val partyRoleSnapshot: String,
    val partyAddressSnapshot: String,
    val plaintiffsSummarySnapshot: String = "",
    val defendantsSummarySnapshot: String = "",
    val subjectOfCaseSnapshot: String = "",
    val preliminaryJudgmentDateEpochDay: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val isAuthorityNotice: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''") val incomingNoSnapshot: String = "",
    val orderInBatch: Int
)
