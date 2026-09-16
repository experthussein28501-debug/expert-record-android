package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.PartyRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyDraftTest {
    @Test
    fun `next party clears first name only and keeps the reusable data`() {
        val next = PartyDraft(
            localId = 17L,
            firstName = "محمد",
            restName = "أحمد علي",
            role = PartyRole.DEFENDANT,
            withCapacity = true,
            address = "أسوان"
        ).forNextParty()

        assertEquals(0L, next.localId)
        assertEquals("", next.firstName)
        assertEquals("أحمد علي", next.restName)
        assertEquals(PartyRole.DEFENDANT, next.role)
        assertEquals("أسوان", next.address)
        assertTrue(next.withCapacity)
    }
}
