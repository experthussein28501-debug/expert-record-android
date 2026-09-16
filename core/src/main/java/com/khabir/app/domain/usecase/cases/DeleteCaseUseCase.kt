package com.khabir.app.domain.usecase.cases

import com.khabir.app.domain.repository.CaseRepository
import javax.inject.Inject

class DeleteCaseUseCase @Inject constructor(private val repository: CaseRepository) {
    suspend operator fun invoke(caseId: Long) = repository.moveToTrash(caseId)
}

