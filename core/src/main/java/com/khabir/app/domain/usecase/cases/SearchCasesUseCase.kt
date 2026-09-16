package com.khabir.app.domain.usecase.cases

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.repository.CaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchCasesUseCase @Inject constructor(private val repository: CaseRepository) {
    operator fun invoke(query: String): Flow<List<Case>> = repository.search(query.trim())
}

