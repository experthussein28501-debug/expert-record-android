package com.khabir.app.data.auth

/** Local use is unlimited from 0.9.19; legacy expiry markers are ignored. */
@Suppress("UNUSED_PARAMETER")
object GuestTrialPolicy {
    // Historical duration retained only for upgrade regression fixtures.
    const val DURATION_MILLIS = 7L * 24 * 60 * 60 * 1000
    fun expired(start: Long, now: Long, terminal: Boolean = false): Boolean = false
    fun mayStart(start: Long, terminal: Boolean): Boolean = true
    fun remaining(start: Long, now: Long): Long = Long.MAX_VALUE
}

object GoogleAccountGate {
    fun allows(userExists: Boolean, anonymous: Boolean, providers: List<String>): Boolean =
        userExists && !anonymous && "google.com" in providers
}
