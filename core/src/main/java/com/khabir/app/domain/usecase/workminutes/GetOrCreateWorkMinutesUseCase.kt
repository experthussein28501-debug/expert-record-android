package com.khabir.app.domain.usecase.workminutes

import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.WorkMinutesRepository
import javax.inject.Inject

class GetOrCreateWorkMinutesUseCase @Inject constructor(
    private val workMinutesRepository: WorkMinutesRepository,
    private val caseRepository: CaseRepository
) {
    suspend operator fun invoke(recordId: Long, caseId: Long?): WorkMinutesRecord {
        val selected=recordId.takeIf {it>0L}?.let {workMinutesRepository.getById(it)}
        if(selected!=null && selected.caseId==null) return selected
        val linkedId=selected?.caseId ?: caseId
        if(linkedId!=null && linkedId>0L) {
            val existing=selected ?: workMinutesRepository.getForCase(linkedId)
            val case=caseRepository.getById(linkedId) ?: return existing ?: WorkMinutesRecord()
            val generated=com.khabir.app.domain.model.AutomaticWorkMinutes.receipt(case,existing)
            if(generated!=existing && (existing!=null || generated.entries.isNotEmpty())) {
                return generated.copy(id=workMinutesRepository.save(generated))
            }
            return generated
        }
        return selected ?: WorkMinutesRecord()
    }
}
