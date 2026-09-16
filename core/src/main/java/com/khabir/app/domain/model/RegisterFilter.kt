package com.khabir.app.domain.model

import java.time.LocalDate

data class RegisterFilter(
    val caseType: String? = null,
    val court: String? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null
)

