package com.khabir.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64

class WorkMinutesCodecTest {

    @Test
    fun `round trip keeps structured follow up time and location`() {
        val original = listOf(
            WorkMinutesEntry(
                number = 1,
                openingDate = LocalDate.of(2026, 9, 17),
                openingTime = "10 صباحًا",
                bodyText = "تمت مباشرة المأمورية",
                closingTime = "11 صباحًا",
                expertName = "الخبير",
                scheduledFollowUpDate = LocalDate.of(2026, 9, 20),
                scheduledFollowUpTime = "9 صباحًا",
                scheduledFollowUpLocation = "المكتب"
            )
        )

        val decoded = WorkMinutesCodec.decode(WorkMinutesCodec.encode(original))

        assertEquals(1, decoded.size)
        assertEquals(LocalDate.of(2026, 9, 20), decoded.single().scheduledFollowUpDate)
        assertEquals("9 صباحًا", decoded.single().scheduledFollowUpTime)
        assertEquals("المكتب", decoded.single().scheduledFollowUpLocation)
    }

    @Test
    fun `old seven column records remain readable`() {
        fun enc(value: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        val oldSpec = listOf(
            "1",
            LocalDate.of(2026, 9, 17).toEpochDay().toString(),
            enc("10 صباحًا"),
            enc("محضر قديم"),
            enc("11 صباحًا"),
            enc("الخبير"),
            LocalDate.of(2026, 9, 20).toEpochDay().toString()
        ).joinToString("\t")

        val decoded = WorkMinutesCodec.decode(oldSpec)

        assertEquals(1, decoded.size)
        assertEquals("محضر قديم", decoded.single().bodyText)
        assertEquals(LocalDate.of(2026, 9, 20), decoded.single().scheduledFollowUpDate)
        assertTrue(decoded.single().scheduledFollowUpTime.isBlank())
        assertTrue(decoded.single().scheduledFollowUpLocation.isBlank())
    }
}
