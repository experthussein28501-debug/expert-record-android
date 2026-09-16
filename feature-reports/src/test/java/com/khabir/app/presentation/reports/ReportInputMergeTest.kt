package com.khabir.app.presentation.reports

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportInputMergeTest {
    @Test fun appendKeepsExistingFacts() = assertEquals("قديم\n\nجديد", mergeReportInput("قديم", "جديد"))
    @Test fun replacementRequiresExplicitChoice() = assertEquals("جديد", mergeReportInput("قديم", "جديد", true))
    @Test fun emptyInputDoesNotEraseText() = assertEquals("قديم", mergeReportInput("قديم", ""))
    @Test fun emptySectionHasNoLeadingLines() = assertEquals("جديد", mergeReportInput("", "جديد"))
}
