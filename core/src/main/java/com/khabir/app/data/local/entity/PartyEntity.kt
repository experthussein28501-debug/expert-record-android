package com.khabir.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parties",
    foreignKeys = [ForeignKey(entity = CaseEntity::class, parentColumns = ["id"], childColumns = ["caseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("caseId")]
)
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val caseId: Long,
    val firstName: String,
    val restName: String,
    val role: String,
    val address: String,
    val orderIndex: Int
)

