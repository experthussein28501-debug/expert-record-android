package com.khabir.app.domain.usecase.profile

import com.khabir.app.domain.model.ExpertProfile
import com.khabir.app.domain.repository.ExpertProfileRepository
import javax.inject.Inject

class SaveExpertProfileUseCase @Inject constructor(private val repository: ExpertProfileRepository) {
    suspend operator fun invoke(profile: ExpertProfile) = repository.save(profile)
}

