package com.khabir.app.data.ai
import org.junit.Assert.*
import org.junit.Test

class GeminiModelSelectionTest {
    @Test fun economyPrefersLiteAndNeverEscalatesToPro() {
        assertEquals("models/gemini-2.5-flash-lite", GeminiModelSelection.choose(listOf(
            "models/gemini-2.5-flash", "models/gemini-2.5-flash-lite", "models/gemini-2.5-pro"
        )))
        assertNull(GeminiModelSelection.choose(listOf("models/gemini-2.5-pro")))
    }
    @Test fun excludesSpecializedModelsAndPrefersStableFlash() {
        assertEquals("models/gemini-2.5-flash", GeminiModelSelection.choose(listOf(
            "models/gemini-2.5-flash-preview-tts", "models/gemini-3-flash-preview",
            "models/gemini-2.5-flash-image", "models/gemini-2.5-pro", "models/gemini-2.5-flash"
        )))
    }
    @Test fun neverInventsAModelWhenNoneAreAvailable() {
        assertNull(GeminiModelSelection.choose(emptyList()))
        assertNull(GeminiModelSelection.choose(listOf("models/gemini-live", "models/embedding-001")))
    }
}
