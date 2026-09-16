package com.khabir.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderChunkingTest {
    @Test
    fun groq_ten_pages_are_split_into_two_safe_groups() {
        val groups = (1..10).toList().chunked(AiProviderHttp.maxVisionImages(AiProvider.GROQ))
        assertEquals(2, groups.size)
        assertEquals(listOf(1, 2, 3, 4, 5), groups.first())
        assertEquals(listOf(6, 7, 8, 9, 10), groups.last())
    }
}
