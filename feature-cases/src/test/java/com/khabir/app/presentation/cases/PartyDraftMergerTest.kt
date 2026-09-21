package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.PartyRole
import org.junit.Assert.*
import org.junit.Test

class PartyDraftMergerTest {
    private val original = PartyDraft(localId = 1, firstName = "أحمد", restName = "علي", role = PartyRole.PLAINTIFF, address = "أسوان")
    @Test fun spellingAndEmptyAddressDoNotDuplicateAnExistingName() {
        val result = PartyDraftMerger.merge(listOf(original), listOf(original.copy(localId = 2, firstName = "احمد", restName = "على", address = "")))
        assertEquals(listOf(original), result.parties)
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun conflictingAddressRequiresChoiceAndDoesNotOverwrite() {
        val incoming = original.copy(localId = 2, address = "إدفو")
        val result = PartyDraftMerger.merge(listOf(original), listOf(incoming))
        assertEquals(listOf(original), result.parties)
        assertEquals(listOf(original to incoming), result.conflicts)
    }
    @Test fun blankAddressIsEnrichedButDifferentRolesRemainSeparate() {
        val result = PartyDraftMerger.merge(listOf(original.copy(address = "")), listOf(original, original.copy(localId = 3, role = PartyRole.LAWYER)))
        assertEquals(2, result.parties.size)
        assertEquals("أسوان", result.parties.first().address)
    }
}
