package com.khabir.app.presentation.common

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Call from the owning UI dispatcher. All persistence paths share this coordinator. */
class DraftSaveCoordinator<T>(
    private val snapshot: () -> T,
    private val persist: suspend (T) -> Long,
    private val acceptId: (Long) -> Unit
) {
    private val mutex = Mutex()
    private var revision = 0L
    fun changed() { revision++ }

    suspend fun flush(): Long = mutex.withLock {
        var id: Long
        do {
            val savingRevision = revision
            id = persist(snapshot())
            // Publish the generated ID before allowing another snapshot, avoiding duplicate inserts.
            acceptId(id)
        } while (savingRevision != revision)
        id
    }
}
