package com.khabir.agenda

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** تخزين مستقل تمامًا عن قاعدة بيانات القضايا. */
@Singleton
class AgendaStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = com.khabir.app.data.security.AgendaVault(context)
    private val _records = MutableStateFlow(loadAll())
    val records: StateFlow<Map<Long, AgendaDayNote>> = _records

    fun get(date: LocalDate): AgendaDayNote? = _records.value[date.toEpochDay()]

    @Synchronized
    fun save(record:AgendaDayNote) = saveAll(listOf(record))

    @Synchronized
    fun saveAll(records:List<AgendaDayNote>) {
        val values=records.associate { record ->
            val empty=record.text.isBlank() && record.strokes.isEmpty() && record.imagePaths.isEmpty() && record.manualAppointments.isEmpty() && record.hiddenImportedKeys.isEmpty()
            record.date.toEpochDay().toString() to if(empty) null else AgendaDayCodec.encode(record)
        }
        prefs.putAll(values)
        var updated=_records.value
        records.forEach { record -> val epoch=record.date.toEpochDay();updated=if(values[epoch.toString()]==null) updated-epoch else updated+(epoch to record) }
        _records.value=updated
    }

    @Synchronized
    fun delete(date: LocalDate) {
        val epoch = date.toEpochDay()
        prefs.remove(epoch.toString())
        _records.value = _records.value - epoch
    }

    private fun loadAll(): Map<Long, AgendaDayNote> = prefs.all().mapNotNull { (key, value) ->
        val epoch = key.toLongOrNull() ?: return@mapNotNull null
        val spec = value as? String ?: return@mapNotNull null
        AgendaDayCodec.decode(LocalDate.ofEpochDay(epoch), spec)?.let { epoch to it }
    }.toMap()
}
