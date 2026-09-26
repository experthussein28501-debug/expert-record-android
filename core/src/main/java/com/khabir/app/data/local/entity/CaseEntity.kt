package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val incomingNo: String,
    val incomingDateEpochDay: Long,
    val caseNo: String,
    val caseYear: String,
    val court: String,
    val caseType: String,
    val subjectOfCase: String = "",
    val finalRequests: String = "",
    val preliminaryMission: String = "",
    val adminNotes: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val syncVersion: Long = 0L,
    val receiptDateEpochDay: Long? = null,
    val preliminaryJudgmentDateEpochDay: Long? = null,
    val hearingDateEpochDay: Long? = null,
    val hearingTime: String = ""
)
