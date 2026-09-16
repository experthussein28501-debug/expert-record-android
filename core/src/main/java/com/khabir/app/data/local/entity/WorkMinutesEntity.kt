package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_minutes",
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("caseId", unique = true)]
)
data class WorkMinutesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val caseId: Long? = null,
    val caseNo: String = "",
    val caseYear: String = "",
    val court: String = "",
    val plaintiffsSummary: String = "",
    val defendantsSummary: String = "",
    val entriesSpec: String = "",
    val copiesCount: Int = 1,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L
)
