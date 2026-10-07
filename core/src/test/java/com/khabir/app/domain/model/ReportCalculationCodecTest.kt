package com.khabir.app.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCalculationCodecTest {

    @Test
    fun legacyNumericLabelDoesNotShiftAmountsOrLoseNotes() {
        val legacy = "2024\t2\t150.5\tجنيه\tمستند رقم 7"
        val row = ReportCalculationCodec.decode(legacy).single()
        assertEquals("2024", row.label)
        assertEquals("2", row.quantity)
        assertEquals("150.5", row.unitValue)
        assertEquals("جنيه", row.unit)
        assertEquals("مستند رقم 7", row.notes)
        assertEquals(BigDecimal("301.00"), ReportCalculationCodec.total(legacy))
        assertEquals(row, ReportCalculationCodec.decode(ReportCalculationCodec.encode(listOf(row))).single())
    }

    @Test
    fun currentNumericLabelWithEmptyNotesKeepsIdAndTotal() {
        val row = ReportCalculationRow(41L, "2024", "2", "150.5", "جنيه", "")
        val encoded = ReportCalculationCodec.encode(listOf(row))
        assertEquals(row, ReportCalculationCodec.decode(encoded).single())
        assertEquals(BigDecimal("301.00"), ReportCalculationCodec.total(encoded))
    }

    @Test
    fun number_acceptsArabicDigitsAndSeparators() {
        assertEquals(BigDecimal("1234.50"), ReportCalculationCodec.number("١٬٢٣٤٫٥٠"))
    }

    @Test
    fun encodeDecode_roundTripsRows() {
        val rows = listOf(
            ReportCalculationRow(
                id = 7L,
                label = "قيمة الريع",
                quantity = "12",
                unitValue = "250.5",
                unit = "جنيه",
                notes = "سنوي"
            )
        )

        assertEquals(rows, ReportCalculationCodec.decode(ReportCalculationCodec.encode(rows)))
    }

    @Test
    fun totalAndExportText_includeCalculatedTotal() {
        val encoded = ReportCalculationCodec.encode(
            listOf(
                ReportCalculationRow(1L, "البند الأول", "٢", "١٠٫٥"),
                ReportCalculationRow(2L, "البند الثاني", "3", "4")
            )
        )

        assertEquals(BigDecimal("33.00"), ReportCalculationCodec.total(encoded))
        assertTrue(ReportCalculationCodec.exportText(encoded).contains("الإجمالي: 33.00"))
    }
}
