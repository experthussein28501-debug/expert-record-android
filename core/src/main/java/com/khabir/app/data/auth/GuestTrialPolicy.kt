package com.khabir.app.data.auth

object GuestTrialPolicy {
    const val DURATION_MILLIS = 7L * 24 * 60 * 60 * 1000
    fun expired(start: Long, now: Long, terminal: Boolean = false): Boolean =
        terminal || (start > 0 && now >= start && now - start >= DURATION_MILLIS)
    fun mayStart(start: Long, terminal: Boolean): Boolean = start == 0L && !terminal
    fun remaining(start: Long, now: Long): Long =
        if (start <= 0) 0 else (DURATION_MILLIS - (now - start).coerceAtLeast(0)).coerceAtLeast(0)
}

object GoogleAccountGate {
    fun allows(userExists: Boolean, anonymous: Boolean, providers: List<String>): Boolean =
        userExists && !anonymous && "google.com" in providers
}
