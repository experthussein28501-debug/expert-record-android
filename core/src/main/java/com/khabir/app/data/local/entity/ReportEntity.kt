package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reports",
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
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val caseId: Long? = null,
    val caseNo: String = "",
    val caseYear: String = "",
    val court: String = "",
    val manualHeader: String = "",
    val templateId: String = "built_in_civil",
    val templateName: String = "مدني",
    val templateSectionsSpec: String = "",
    val customSectionContentsSpec: String = "",
    val partiesSummary: String = "",
    val subjectOfCase: String = "",
    val assignment: String = "",
    val proceedings: String = "",
    val partyStatements: String = "",
    val witnessStatements: String = "",
    val inspection: String = "",
    val documentsSubmitted: String = "",
    val facts: String = "",
    val research: String = "",
    val technicalOpinion: String = "",
    val calculationsTable: String = "",
    val siteSketchPath: String = "",
    val conclusion: String = "",
    val attachmentsNote: String = "",
    val depositDateEpochDay: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
