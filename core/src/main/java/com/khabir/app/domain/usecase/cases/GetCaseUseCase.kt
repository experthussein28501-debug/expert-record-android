package com.khabir.app.domain.usecase.cases

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.repository.CaseRepository
import javax.inject.Inject

class GetCaseUseCase @Inject constructor(private val repository: CaseRepository) {
    suspend operator fun invoke(caseId: Long): Case? = repository.getById(caseId)
}

