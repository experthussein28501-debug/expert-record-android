package com.khabir.app.domain.usecase.workminutes

import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.repository.WorkMinutesRepository
import javax.inject.Inject

class SaveWorkMinutesUseCase @Inject constructor(private val repository: WorkMinutesRepository) {
    suspend operator fun invoke(record: WorkMinutesRecord): Long = repository.save(record)
}
