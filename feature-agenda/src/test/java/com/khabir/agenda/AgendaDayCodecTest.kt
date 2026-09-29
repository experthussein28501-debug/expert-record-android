package com.khabir.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64

class AgendaDayCodecTest {

    @Test
    fun `round trip keeps manual appointments notes drawings and images`() {
        val date = LocalDate.of(2026, 9, 20)
        val original = AgendaDayNote(
            date = date,
            text = "مراجعة الملف قبل الموعد",
            strokes = listOf(AgendaStroke(listOf(AgendaPoint(10f, 20f), AgendaPoint(30f, 40f)))),
            imagePaths = listOf("/tmp/a.jpg"),
            manualAppointments = listOf(
                AgendaManualAppointment(
                    title = "الدعوى 105 لسنة 2025",
                    time = "9 صباحًا",
                    location = "المكتب",
                    details = "مراجعة المستندات"
                )
            ),
            hiddenImportedKeys = setOf("WORK_MINUTES\u001f2026-09-20\u001fالدعوى 105\u001f9 صباحًا"),
            updatedAt = 1234L
        )

        val decoded = AgendaDayCodec.decode(date, AgendaDayCodec.encode(original))!!

        assertEquals(original.text, decoded.text)
        assertEquals(original.imagePaths, decoded.imagePaths)
        assertEquals(1, decoded.strokes.size)
        assertEquals(2, decoded.strokes.single().points.size)
        assertEquals("الدعوى 105 لسنة 2025", decoded.manualAppointments.single().title)
        assertEquals("9 صباحًا", decoded.manualAppointments.single().time)
        assertEquals("المكتب", decoded.manualAppointments.single().location)
        assertEquals("مراجعة المستندات", decoded.manualAppointments.single().details)
        assertEquals(original.hiddenImportedKeys, decoded.hiddenImportedKeys)
    }

    @Test
    fun `legacy four column day record remains readable`() {
        fun b64(value: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        val date = LocalDate.of(2026, 9, 21)
        val legacy = listOf(
            b64("ملاحظة قديمة"),
            b64("10.0,20.0;30.0,40.0"),
            b64("/tmp/old.jpg"),
            "99"
        ).joinToString("\t")

        val decoded = AgendaDayCodec.decode(date, legacy)!!

        assertEquals("ملاحظة قديمة", decoded.text)
        assertEquals(listOf("/tmp/old.jpg"), decoded.imagePaths)
        assertEquals(1, decoded.strokes.size)
        assertTrue(decoded.manualAppointments.isEmpty())
    }
}
