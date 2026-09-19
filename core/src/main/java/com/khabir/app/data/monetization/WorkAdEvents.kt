package com.khabir.app.data.monetization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Events are never replayed: an unloaded ad cannot interrupt subsequent work. */
object WorkAdEvents {
    val boundaries = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val blockers = mutableSetOf<Any>()
    @Synchronized fun block(token: Any) { blockers.add(token) }
    @Synchronized fun unblock(token: Any) { blockers.remove(token) }
    @Synchronized fun isBlocked() = blockers.isNotEmpty()
    fun finished() { boundaries.tryEmit(Unit) }
}

@Composable
fun BlockWorkAds(blocked: Boolean) {
    DisposableEffect(blocked) {
        val token = Any()
        if (blocked) WorkAdEvents.block(token)
        onDispose { WorkAdEvents.unblock(token) }
    }
}
