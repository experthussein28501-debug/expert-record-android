package com.khabir.app.domain.repository

import com.khabir.app.domain.model.ExpertProfile
import kotlinx.coroutines.flow.Flow

interface ExpertProfileRepository {
    fun observe(): Flow<ExpertProfile?>
    suspend fun get(): ExpertProfile?
    suspend fun save(profile: ExpertProfile)
}

