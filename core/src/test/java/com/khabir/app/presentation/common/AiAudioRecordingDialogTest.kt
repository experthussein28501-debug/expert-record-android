package com.khabir.app.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AiAudioRecordingDialogTest {
    @Test
    fun `long recording transcripts merge in segment order`() {
        assertEquals(
            "الجزء الأول\nالجزء الثاني\nالجزء الثالث",
            mergeAiTranscripts(listOf(" الجزء الأول ", "", "الجزء الثاني", "  الجزء الثالث  "))
        )
    }
}
