package com.khabir.app.domain.model

/** Arabic-Indic digits used in official Arabic exports and printed case data. */
fun String.toArabicIndicDigits(): String = map { ch ->
    when (ch) {
        '0' -> '٠'
        '1' -> '١'
        '2' -> '٢'
        '3' -> '٣'
        '4' -> '٤'
        '5' -> '٥'
        '6' -> '٦'
        '7' -> '٧'
        '8' -> '٨'
        '9' -> '٩'
        else -> ch
    }
}.joinToString("")
