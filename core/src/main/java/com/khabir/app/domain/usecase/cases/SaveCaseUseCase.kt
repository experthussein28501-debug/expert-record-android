package com.khabir.app.domain.usecase.cases

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.CaseValidationError
import com.khabir.app.domain.repository.CaseRepository
import javax.inject.Inject

class SaveCaseUseCase @Inject constructor(private val repository: CaseRepository) {
    sealed class Result { data class Success(val caseId: Long): Result(); data class Invalid(val errors: List<CaseValidationError>): Result() }
    suspend operator fun invoke(case: Case): Result {
        val errors = case.validate(); if (errors.isNotEmpty()) return Result.Invalid(errors)
        return Result.Success(repository.save(case))
    }
}

