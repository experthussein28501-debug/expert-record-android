package com.khabir.agenda

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/** Retained by the screen ViewModel; large pen strokes never enter the saved-state Bundle. */
internal class AgendaDraft(note: AgendaDayNote?) {
    val text = mutableStateOf(note?.text.orEmpty())
    val strokes = mutableStateListOf<AgendaStroke>().apply { addAll(note?.strokes.orEmpty()) }
    val images = mutableStateListOf<String>().apply { addAll(note?.imagePaths.orEmpty()) }
    val appointments = mutableStateListOf<AgendaManualAppointment>().apply { addAll(note?.manualAppointments.orEmpty()) }
    val hiddenImportedKeys = mutableStateListOf<String>().apply { addAll(note?.hiddenImportedKeys.orEmpty()) }
    val noteTransfers = mutableStateListOf<AgendaNoteCandidate>()
    val manualTitle = mutableStateOf("")
    val manualTime = mutableStateOf("")
    val manualLocation = mutableStateOf("")
    val manualDetails = mutableStateOf("")
}
