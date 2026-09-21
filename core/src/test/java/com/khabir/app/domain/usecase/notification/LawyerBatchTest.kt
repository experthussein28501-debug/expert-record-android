package com.khabir.app.domain.usecase.notification

import com.khabir.app.domain.model.*
import com.khabir.app.domain.repository.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LawyerBatchTest {
    private var stored: NotificationBatch? = null
    private val profile = object : ExpertProfileRepository {
        override fun observe() = flowOf(ExpertProfile(expertName = "خبير تجريبي"))
        override suspend fun get() = ExpertProfile(expertName = "خبير تجريبي")
        override suspend fun save(profile: ExpertProfile) = Unit
    }
    private val batches = object : NotificationBatchRepository {
        override fun observeAll() = flowOf(emptyList<NotificationBatch>())
        override suspend fun getById(batchId: Long) = stored
        override suspend fun save(batch: NotificationBatch): Long { stored = batch; return 1L }
    }
    @Test fun lawyerIsASelectableRecipientWithBarAddressAndNoLitigantSummary() = runBlocking {
        val useCase = CreateNotificationBatchUseCase(profile, batches)
        val result = useCase(LocalDate.of(2026, 9, 20), "10", "المكتب", "", listOf(
            RecipientSelection.Manual("1", "2026", "أسوان", "أحمد", "محمد", PartyRole.LAWYER, "كوم أمبو")))
        assertTrue(result is CreateNotificationBatchUseCase.Result.Success)
        val recipient = stored!!.recipients.single()
        assertEquals("محكمة كوم أمبو — نقابة المحامين بكوم أمبو", recipient.partyAddress)
        assertEquals("", recipient.plaintiffsSummary)
        assertEquals("", recipient.defendantsSummary)
    }
    @Test fun unknownCityCannotBeReplacedWithAnOfficeAddressOrCaseCourt() = runBlocking {
        val result = CreateNotificationBatchUseCase(profile, batches)(LocalDate.of(2026, 9, 20), "10", "المكتب", "", listOf(
            RecipientSelection.Manual("1", "2026", "أسوان", "أحمد", "محمد", PartyRole.LAWYER, "شارع النيل 12")))
        assertEquals(CreateNotificationBatchUseCase.Result.MissingLawyerCity, result)
        assertNull(stored)
    }
}
