package com.khabir.app.domain.usecase.workminutes

import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.repository.WorkMinutesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ListWorkMinutesUseCase @Inject constructor(private val repository: WorkMinutesRepository) {
    operator fun invoke(): Flow<List<WorkMinutesRecord>> = repository.observeAll()
}
