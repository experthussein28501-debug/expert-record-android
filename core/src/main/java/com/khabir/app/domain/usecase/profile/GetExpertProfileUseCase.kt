package com.khabir.app.domain.usecase.profile

import com.khabir.app.domain.model.ExpertProfile
import com.khabir.app.domain.repository.ExpertProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExpertProfileUseCase @Inject constructor(private val repository: ExpertProfileRepository) {
    operator fun invoke(): Flow<ExpertProfile?> = repository.observe()
}

