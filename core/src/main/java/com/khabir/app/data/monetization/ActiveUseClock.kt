package com.khabir.app.data.monetization

/** Uses elapsed realtime, so changing the device date cannot accelerate the timer. */
class ActiveUseClock {
    private var lastTick = 0L
    private var lastInteraction = 0L
    private var foreground = false
    var activeMillis = 0L
        private set

    fun resume(now: Long) { foreground = true; lastTick = now; lastInteraction = now }
    fun pause(now: Long) { tick(now); foreground = false }
    fun interact(now: Long) { tick(now); lastInteraction = now }
    fun tick(now: Long) {
        if (foreground && now >= lastTick) {
            val activeUntil = minOf(now, lastInteraction + IDLE_TIMEOUT)
            activeMillis = (activeMillis + (activeUntil - lastTick).coerceAtLeast(0)).coerceAtMost(INTERVAL)
        }
        lastTick = now
    }
    fun isDue(now: Long): Boolean { tick(now); return foreground && activeMillis >= INTERVAL }
    fun adShown(now: Long) { activeMillis = 0; lastTick = now; lastInteraction = now }
    companion object { const val INTERVAL = 20 * 60 * 1000L; const val IDLE_TIMEOUT = 60 * 1000L }
}
