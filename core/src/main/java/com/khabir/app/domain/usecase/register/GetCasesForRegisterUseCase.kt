package com.khabir.app.domain.usecase.register

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.RegisterFilter
import com.khabir.app.domain.repository.CaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCasesForRegisterUseCase @Inject constructor(private val repository: CaseRepository) {
    operator fun invoke(filter: RegisterFilter): Flow<List<Case>> = repository.filterForRegister(filter.caseType, filter.court, filter.fromDate?.toEpochDay(), filter.toDate?.toEpochDay())
}

