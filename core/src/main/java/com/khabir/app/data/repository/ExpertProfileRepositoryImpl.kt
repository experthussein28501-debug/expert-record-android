package com.khabir.app.data.repository

import com.khabir.app.data.local.dao.ExpertProfileDao
import com.khabir.app.data.local.entity.ExpertProfileEntity
import com.khabir.app.domain.model.ExpertProfile
import com.khabir.app.domain.repository.ExpertProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpertProfileRepositoryImpl @Inject constructor(private val dao: ExpertProfileDao) : ExpertProfileRepository {
    override fun observe(): Flow<ExpertProfile?> = dao.observe().map { it?.toDomain() }
    override suspend fun get(): ExpertProfile? = dao.get()?.toDomain()
    override suspend fun save(profile: ExpertProfile) {
        dao.upsert(
            ExpertProfileEntity(
                ministryOrSector = profile.ministryOrSector,
                department = profile.department,
                expertName = profile.expertName,
                specialization = profile.specialization,
                officeAddress = profile.officeAddress,
                jobTitle = profile.jobTitle,
                attendancePhrase = profile.attendancePhrase,
                phone = profile.phone,
                email = profile.email,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }
}

private fun ExpertProfileEntity.toDomain() = ExpertProfile(
    ministryOrSector = ministryOrSector,
    department = department,
    expertName = expertName,
    specialization = specialization,
    officeAddress = officeAddress,
    jobTitle = jobTitle,
    attendancePhrase = attendancePhrase,
    phone = phone,
    email = email
)
