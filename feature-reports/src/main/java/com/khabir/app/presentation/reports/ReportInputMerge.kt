package com.khabir.app.presentation.reports

fun mergeReportInput(existing: String, incoming: String, replace: Boolean = false): String =
    if (replace) incoming else when {
        incoming.isBlank() -> existing
        existing.isBlank() -> incoming
        else -> existing + "\n\n" + incoming
    }
