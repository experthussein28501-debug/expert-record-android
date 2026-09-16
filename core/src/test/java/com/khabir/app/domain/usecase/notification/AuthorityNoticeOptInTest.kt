package com.khabir.app.domain.usecase.notification

import com.khabir.app.domain.model.*
import com.khabir.app.domain.repository.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AuthorityNoticeOptInTest {
    private class Batches : NotificationBatchRepository {
        var saved: NotificationBatch? = null
        override fun observeAll() = flowOf(listOfNotNull(saved))
        override suspend fun getById(batchId: Long) = saved
        override suspend fun save(batch: NotificationBatch): Long { saved = batch; return 1 }
    }
    private val profiles = object : ExpertProfileRepository {
        override fun observe() = flowOf(ExpertProfile())
        override suspend fun get() = ExpertProfile()
        override suspend fun save(profile: ExpertProfile) = Unit
    }
    private val original = RecipientSelection.Manual("20", "2025", "كوم أمبو",
        "مدير", "مديرية الصحة بصفته", PartyRole.DEFENDANT, "عنوان الجهة الإدارية")
    private val draft = AuthorityNoticeDraft("case1", "20", "2025", "كوم أمبو",
        "عنوان الهيئة المعتمد", "صحة ونفاذ", "2025-12-01", "123", "المدعي", "الجهة الإدارية")

    @Test fun governmentDoesNotAutomaticallyAddAuthority() = runBlocking {
        val batches = Batches()
        CreateNotificationBatchUseCase(profiles, batches)(LocalDate.now(), "10", "المكتب", "", listOf(original))
        assertEquals(1, batches.saved!!.recipients.size)
        assertFalse(batches.saved!!.recipients.single().isAuthorityNotice)
        assertEquals(original.address, batches.saved!!.recipients.single().partyAddress)
    }
    @Test fun explicitApprovalAddsOneNoticeWithoutChangingOriginal() = runBlocking {
        val batches = Batches()
        CreateNotificationBatchUseCase(profiles, batches)(LocalDate.now(), "10", "المكتب", "", listOf(original), authorityNotices = listOf(draft, draft))
        val recipients = batches.saved!!.recipients
        assertEquals(2, recipients.size)
        assertEquals(original.address, recipients.first().partyAddress)
        assertTrue(recipients.last().isAuthorityNotice)
        assertEquals("عنوان الهيئة المعتمد", recipients.last().partyAddress)
        assertEquals("123", recipients.last().incomingNo)
    }
    @Test fun missingAddressAndInvalidDatesCannotBeApproved() {
        assertFalse(draft.copy(address = "").valid())
        assertFalse(draft.copy(judgmentDate = "2025-99-99").valid())
        assertTrue(draft.copy(judgmentDate = "").valid())
    }
}
