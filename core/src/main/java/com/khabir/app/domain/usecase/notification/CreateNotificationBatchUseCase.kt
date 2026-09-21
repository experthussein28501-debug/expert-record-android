package com.khabir.app.domain.usecase.notification

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.NotificationRecipient
import com.khabir.app.domain.model.Party
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.repository.ExpertProfileRepository
import com.khabir.app.domain.repository.NotificationBatchRepository
import java.time.LocalDate
import javax.inject.Inject

sealed class RecipientSelection {
    data class FromCase(val case: Case, val party: Party) : RecipientSelection()
    data class Manual(
        val caseNo: String,
        val caseYear: String,
        val court: String,
        val firstName: String,
        val restName: String,
        val role: PartyRole,
        val address: String,
        val withCapacity: Boolean = false
    ) : RecipientSelection()
}

class CreateNotificationBatchUseCase @Inject constructor(
    private val expertProfileRepository: ExpertProfileRepository,
    private val batchRepository: NotificationBatchRepository
) {
    sealed class Result {
        data class Success(val batchId: Long) : Result()
        data object EmptySelection : Result()
        data object MissingExpertProfile : Result()
        data object MissingLawyerCity : Result()
    }

    suspend operator fun invoke(
        appointmentDate: LocalDate,
        appointmentTime: String,
        appointmentLocation: String,
        requestedDocuments: String,
        selections: List<RecipientSelection>,
        reusedFrom: Long? = null,
        authorityNotices: List<AuthorityNoticeDraft> = emptyList(),
        existingBatchId: Long? = null
    ): Result {
        if (selections.isEmpty()) return Result.EmptySelection
        if (selections.any { selection ->
            val (role, address) = when (selection) {
                is RecipientSelection.FromCase -> selection.party.role to selection.party.address
                is RecipientSelection.Manual -> selection.role to selection.address
            }
            role == PartyRole.LAWYER && com.khabir.app.domain.model.LawyerNotification.address(address).isBlank()
        }) return Result.MissingLawyerCity
        val profile = expertProfileRepository.get() ?: return Result.MissingExpertProfile

        val manualSelections = selections.filterIsInstance<RecipientSelection.Manual>()
        val manualCaseSummaries = manualSelections.groupBy { ManualCaseKey(it.caseNo.trim(), it.caseYear.trim(), it.court.trim()) }
            .mapValues { (_, members) ->
                val plaintiffs = firstPartyOnly(members.filter { it.role == PartyRole.PLAINTIFF }.map { it.displayName })
                val defendants = firstPartyOnly(members.filter { it.role == PartyRole.DEFENDANT }.map { it.displayName })
                PartySummaries(plaintiffs, defendants)
            }

        val recipients = selections.map { selection ->
            when (selection) {
                is RecipientSelection.FromCase -> {
                    val plaintiffs = firstPartyOnly(selection.case.parties
                        .filter { it.role == PartyRole.PLAINTIFF }
                        .map { it.reportDisplayName })
                    val defendants = firstPartyOnly(selection.case.parties
                        .filter { it.role == PartyRole.DEFENDANT }
                        .map { it.reportDisplayName })
                    NotificationRecipient(
                        caseId = selection.case.id,
                        partyId = selection.party.id,
                        caseNo = selection.case.caseNo,
                        caseYear = selection.case.caseYear,
                        court = selection.case.court,
                        partyFirstName = selection.party.firstName,
                        partyRestName = selection.party.restName,
                        partyRole = selection.party.role,
                        partyAddress = if (selection.party.role == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.address(selection.party.address) else selection.party.address,
                        plaintiffsSummary = plaintiffs,
                        defendantsSummary = defendants,
                        subjectOfCase = selection.case.subjectOfCase,
                        preliminaryJudgmentDate = selection.case.preliminaryJudgmentDate,
                        withCapacity = selection.party.withCapacity
                    )
                }
                is RecipientSelection.Manual -> {
                    val key = ManualCaseKey(selection.caseNo.trim(), selection.caseYear.trim(), selection.court.trim())
                    val summaries = manualCaseSummaries[key] ?: PartySummaries("", "")
                    NotificationRecipient(
                        caseId = null,
                        partyId = null,
                        caseNo = selection.caseNo.trim(),
                        caseYear = selection.caseYear.trim(),
                        court = selection.court.trim(),
                        partyFirstName = selection.firstName.trim(),
                        partyRestName = selection.restName.trim(),
                        partyRole = selection.role,
                        partyAddress = if (selection.role == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.address(selection.address) else selection.address.trim(),
                        plaintiffsSummary = summaries.plaintiffs,
                        defendantsSummary = summaries.defendants,
                        withCapacity = selection.withCapacity
                    )
                }
            }
        }
        return Result.Success(
            batchRepository.save(
                NotificationBatch(
                    id = existingBatchId ?: 0L,
                    appointmentDate = appointmentDate,
                    appointmentTime = appointmentTime,
                    appointmentLocation = appointmentLocation,
                    requestedDocuments = requestedDocuments.trim(),
                    expertName = profile.expertName,
                    officeAddress = profile.officeAddress,
                    ministryOrSector = profile.ministryOrSector.ifBlank { "وزارة العدل" },
                    department = profile.department.ifBlank { "إدارة خبراء أسوان" },
                    expertJobTitle = profile.jobTitle.ifBlank { "الخبير المحالة إليه المأمورية" },
                    attendancePhrase = profile.attendancePhrase.ifBlank { "الرجاء الحضور إلى مكتب خبراء وزارة العدل" },
                    recipients = recipients + authorityNotices.distinctBy { it.key }.map { it.toRecipient() },
                    isReprint = reusedFrom != null,
                    sourceBatchId = reusedFrom
                )
            )
        )
    }

    private data class ManualCaseKey(val caseNo: String, val caseYear: String, val court: String)
    private data class PartySummaries(val plaintiffs: String, val defendants: String)
    private fun firstPartyOnly(names: List<String>): String {
        val clean = names.map(String::trim).filter(String::isNotBlank)
        return when (clean.size) { 0 -> ""; 1 -> clean.first(); else -> "${clean.first()} وآخرين" }
    }
    private val RecipientSelection.Manual.rawFullName: String
        get() = listOf(firstName.trim(), restName.trim()).filter { it.isNotBlank() }.joinToString(" ")
    private val RecipientSelection.Manual.displayName: String
        get() = if (withCapacity && rawFullName.isNotBlank()) "$rawFullName بصفته" else rawFullName
}
