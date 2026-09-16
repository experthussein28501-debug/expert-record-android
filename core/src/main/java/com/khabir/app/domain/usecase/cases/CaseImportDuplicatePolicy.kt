package com.khabir.app.domain.usecase.cases

import com.khabir.app.domain.model.Case

/**
 * Duplicate identity used by Excel import. Incoming date and party rows are deliberately
 * excluded so that re-importing the same exported case does not create a second record.
 */
fun caseImportDuplicateKey(case: Case): String = listOf(
    normalizeCaseIdentity(case.incomingNo),
    normalizeCaseIdentity(case.caseNo),
    normalizeCaseIdentity(case.caseYear),
    normalizeCaseIdentity(case.court)
).joinToString("|")

private fun normalizeCaseIdentity(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
    .replace('ى', 'ي')
    .replace('ة', 'ه')
