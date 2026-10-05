package com.khabir.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class ReportCalculationRow(
    val id: Long,
    val label: String = "",
    val quantity: String = "",
    val unitValue: String = "",
    val unit: String = "",
    val notes: String = ""
) {
    val result: BigDecimal
        get() = ReportCalculationCodec.number(quantity)
            .multiply(ReportCalculationCodec.number(unitValue))
            .setScale(2, RoundingMode.HALF_UP)
}

object ReportCalculationCodec {
    private const val SEPARATOR = "\t"

    fun decode(value: String): List<ReportCalculationRow> =
        value.lineSequence()
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                val parts = line.split(SEPARATOR)
                // Current rows have an ID plus five fields. Legacy rows have only
                // five fields, and their label can itself be numeric (e.g. a year).
                val hasStoredId = parts.size >= 6 && parts.firstOrNull()?.toLongOrNull() != null
                val offset = if (hasStoredId) 1 else 0
                ReportCalculationRow(
                    id = if (hasStoredId) parts.first().toLong() else (System.nanoTime() + index),
                    label = parts.getOrNull(offset).orEmpty(),
                    quantity = parts.getOrNull(offset + 1).orEmpty(),
                    unitValue = parts.getOrNull(offset + 2).orEmpty(),
                    unit = parts.getOrNull(offset + 3).orEmpty(),
                    notes = parts.getOrNull(offset + 4).orEmpty()
                )
            }
            .toList()

    fun encode(rows: List<ReportCalculationRow>): String = rows.joinToString("\n") { row ->
        listOf(row.id.toString(), row.label, row.quantity, row.unitValue, row.unit, row.notes)
            .joinToString(SEPARATOR) { clean(it) }
    }

    fun total(value: String): BigDecimal =
        decode(value).fold(BigDecimal.ZERO) { sum, row -> sum.add(row.result) }
            .setScale(2, RoundingMode.HALF_UP)

    fun exportText(value: String): String {
        val rows = decode(value)
        if (rows.isEmpty()) return ""
        val details = rows.mapIndexed { index, row ->
            val title = row.label.ifBlank { "البند ${index + 1}" }
            val unitSuffix = row.unit.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
            val noteSuffix = row.notes.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty()
            "$title: ${row.quantity.ifBlank { "0" }} × ${row.unitValue.ifBlank { "0" }} = ${row.result.toPlainString()}$unitSuffix$noteSuffix"
        }
        return (details + "الإجمالي: ${total(value).toPlainString()}").joinToString("\n")
    }

    fun number(raw: String): BigDecimal {
        val normalized = raw
            .map { ch ->
                when (ch) {
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                    '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    '٫' -> '.'; else -> ch
                }
            }
            .joinToString("")
            .replace("٬", "")
            .replace(",", "")
            .trim()
        return normalized.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    private fun clean(value: String): String =
        value.replace("\t", " ").replace("\n", " ").replace("\r", " ")
}
