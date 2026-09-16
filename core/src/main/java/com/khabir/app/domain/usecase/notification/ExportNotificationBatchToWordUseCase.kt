package com.khabir.app.domain.usecase.notification

import android.net.Uri
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.DocumentExportRepository
import com.khabir.app.domain.repository.NotificationBatchRepository
import javax.inject.Inject

class ExportNotificationBatchToWordUseCase @Inject constructor(
    private val batchRepository: NotificationBatchRepository,
    private val caseRepository: CaseRepository,
    private val exportRepository: DocumentExportRepository
) {
    enum class OutputType { NOTIFICATIONS, SIRKIS, ENVELOPES, MAIL_COVER }

    sealed class Result {
        data class Success(val uri: Uri) : Result()
        data object BatchNotFound : Result()
    }

    suspend operator fun invoke(batchId: Long, outputType: OutputType = OutputType.NOTIFICATIONS): Result {
        return invoke(listOf(batchId), outputType)
    }

    suspend operator fun invoke(batchIds: List<Long>, outputType: OutputType = OutputType.NOTIFICATIONS): Result {
        if (batchIds.isEmpty()) return Result.BatchNotFound
        val batches = batchIds.distinct().map { id ->
            val stored = batchRepository.getById(id) ?: return Result.BatchNotFound
            enrichPartySides(stored)
        }
        val uri = when (outputType) {
            OutputType.NOTIFICATIONS -> exportRepository.exportNotificationBatchesAsWord(batches)
            OutputType.SIRKIS -> exportRepository.exportNotificationSirkisBatchesAsWord(batches)
            OutputType.ENVELOPES -> exportRepository.exportNotificationEnvelopeBatchesAsWord(batches)
            OutputType.MAIL_COVER -> exportRepository.exportNotificationMailCoverBatchesAsWord(batches)
        }
        return Result.Success(uri)
    }

    private suspend fun enrichPartySides(batch: NotificationBatch): NotificationBatch {
        val cases = batch.recipients.mapNotNull { it.caseId }.distinct().associateWith { caseRepository.getById(it) }
        return batch.copy(
            recipients = batch.recipients.map { recipient ->
                val linkedCase = recipient.caseId?.let { cases[it] }
                if (linkedCase == null) recipient else recipient.copy(
                    plaintiffsSummary = firstPartyOnly(linkedCase.parties
                        .filter { it.role == PartyRole.PLAINTIFF }
                        .map { it.reportDisplayName }),
                    defendantsSummary = firstPartyOnly(linkedCase.parties
                        .filter { it.role == PartyRole.DEFENDANT }
                        .map { it.reportDisplayName })
                )
            }
        )
    }

    private fun firstPartyOnly(names: List<String>): String = names.map(String::trim).filter(String::isNotBlank).let {
        when (it.size) { 0 -> ""; 1 -> it.first(); else -> "${it.first()} وآخرين" }
    }
}
