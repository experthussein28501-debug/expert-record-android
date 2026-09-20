package com.khabir.app.presentation.common

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DraftSaveCoordinatorTest {
    private data class Draft(val id: Long = 0L, val text: String)
    @Test fun concurrentAutoAndManualSaveUseOneIdAndFlushTheLastEdit() = runBlocking {
        var state = Draft(text = "old")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<Draft>()
        var active = 0
        var maxActive = 0
        val saver = DraftSaveCoordinator(snapshot = { state }, persist = { draft ->
            active++; maxActive = maxOf(maxActive, active)
            if (writes.isEmpty()) { started.complete(Unit); release.await() }
            writes += draft
            active--
            42L
        }, acceptId = { state = state.copy(id = it) })
        val automatic = async { saver.flush() }
        started.await()
        state = state.copy(text = "last keystroke"); saver.changed()
        val manual = async { saver.flush() }
        release.complete(Unit)
        automatic.await(); manual.await()
        assertEquals(1, maxActive)
        assertEquals(1, writes.count { it.id == 0L })
        assertEquals("last keystroke", writes.last().text)
        assertEquals(42L, state.id)
    }
    @Test fun failureDoesNotDiscardTheDraftAndRetryPersistsIt() = runBlocking {
        var fail = true
        var accepted = false
        var stored = ""
        val saver = DraftSaveCoordinator(snapshot = { "retained text" }, persist = { value ->
            if (fail) error("disk unavailable")
            stored = value; 9L
        }, acceptId = { accepted = true })
        assertTrue(runCatching { saver.flush() }.isFailure)
        assertFalse(accepted)
        fail = false
        saver.flush()
        assertEquals("retained text", stored)
        assertTrue(accepted)
    }
}
