package com.khabir.app.data.monetization
import org.junit.Assert.*
import org.junit.Test

class ActiveUseClockTest {
    @Test fun twentyMinutesOfActiveUseBecomesDueAndShowingResetsIt() {
        val clock = ActiveUseClock(); clock.resume(0)
        repeat(19) { clock.interact((it + 1) * 60_000L) }
        assertFalse(clock.isDue(19 * 60_000L))
        clock.interact(20 * 60_000L)
        assertTrue(clock.isDue(20 * 60_000L))
        clock.adShown(20 * 60_000L)
        assertFalse(clock.isDue(20 * 60_000L))
    }
    @Test fun backgroundAndIdleTimeDoNotAdvanceTheTimer() {
        val clock = ActiveUseClock(); clock.resume(0); clock.pause(30_000)
        clock.resume(4_000_000)
        assertEquals(30_000L, clock.activeMillis)
        assertFalse(clock.isDue(8_000_000))
        assertEquals(90_000L, clock.activeMillis)
    }
    @Test fun movingClockBackwardsNeverSubtractsOrGrantsTime() {
        val clock = ActiveUseClock(); clock.resume(100_000); clock.interact(90_000)
        assertEquals(0L, clock.activeMillis)
    }
}
