package com.khabir.app.data.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPageDocumentReaderModeTest {
    @Test
    fun `ten pages without multi page AI availability use local OCR path`() {
        assertFalse(
            shouldUseAiBatch(
                requestedUseAi = true,
                pageCount = 10,
                canAnalyzeMultiplePages = false
            )
        )
    }

    @Test
    fun `ten pages with a personal AI key may use unified AI analysis`() {
        assertTrue(
            shouldUseAiBatch(
                requestedUseAi = true,
                pageCount = 10,
                canAnalyzeMultiplePages = true
            )
        )
    }

    @Test
    fun `single image can still try configured proxy without personal key`() {
        assertTrue(
            shouldUseAiBatch(
                requestedUseAi = true,
                pageCount = 1,
                canAnalyzeMultiplePages = false
            )
        )
    }

    @Test
    fun `explicit local OCR never switches to AI`() {
        assertFalse(
            shouldUseAiBatch(
                requestedUseAi = false,
                pageCount = 10,
                canAnalyzeMultiplePages = true
            )
        )
    }
}
