package com.khabir.app.data.auth

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.io.File

/** Trial metadata is outside guest data and backups; deleting a workspace never renews the trial. */
class GuestTrialStore(context: Context, private val wallClock: () -> Long = System::currentTimeMillis) {
    private val raw = WorkspaceStorageContext.raw(context)
    private val prefs = raw.getSharedPreferences("khabir_guest_trial_control", Context.MODE_PRIVATE)
    val startedAt: Long get() = prefs.getLong("started", 0)
    val inGuestMode: Boolean get() = prefs.getBoolean("guest_mode", false)
    val canStart: Boolean get() = GuestTrialPolicy.mayStart(startedAt, prefs.getBoolean("expired", false))
    val cleanupComplete: Boolean get() = prefs.getBoolean("cleanup_complete", false)
    private fun bootCount(): Int = Settings.Global.getInt(raw.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun effectiveNow(): Long {
        val wall = wallClock()
        val monotonic = if (startedAt > 0 && bootCount() >= 0 && bootCount() == prefs.getInt("boot", -2)) {
            startedAt + (SystemClock.elapsedRealtime() - prefs.getLong("elapsed_start", 0)).coerceAtLeast(0)
        } else wall
        val now = maxOf(wall, monotonic, prefs.getLong("last_seen", 0))
        check(prefs.edit().putLong("last_seen", now).commit())
        return now
    }
    fun isExpired(): Boolean = GuestTrialPolicy.expired(startedAt, effectiveNow(), prefs.getBoolean("expired", false))
    fun isActive(): Boolean = inGuestMode && startedAt > 0 && !isExpired()
    fun remainingMillis(): Long = GuestTrialPolicy.remaining(startedAt, effectiveNow())
    fun start() {
        check(canStart) { "انتهت فرصة التجربة؛ سجل الدخول بحساب Google للاستمرار" }
        val now = wallClock()
        check(now > 0)
        check(prefs.edit().putLong("started", now).putLong("last_seen", now)
            .putLong("elapsed_start", SystemClock.elapsedRealtime()).putInt("boot", bootCount())
            .putBoolean("guest_mode", true).commit())
    }
    fun useGoogleAccount() { check(prefs.edit().putBoolean("guest_mode", false).commit()) }
    fun markExpired() {
        check(isExpired())
        check(prefs.edit().putBoolean("expired", true).putBoolean("guest_mode", false).commit())
    }
    fun recordGuestProcess(pid: Int) { check(prefs.edit().putInt("guest_pid", if (isActive()) pid else -1).commit()) }
    fun guestProcess(): Int = prefs.getInt("guest_pid", -1)
    /** Caller must close/stop the guest process before removing its database. */
    fun deleteExpiredData(): Boolean {
        check(isExpired()) { "لم تنته مدة التجربة" }
        markExpired()
        val database = raw.getDatabasePath(WorkspaceStorageContext.GUEST_DATABASE)
        val databaseDeleted = !database.exists() || raw.deleteDatabase(WorkspaceStorageContext.GUEST_DATABASE)
        val filesDeleted = listOf(raw.filesDir, raw.cacheDir, raw.noBackupFilesDir)
            .map { File(it, WorkspaceStorageContext.GUEST_DIRECTORY) }
            .map { !it.exists() || it.deleteRecursively() }.all { it }
        var preferencesDeleted = true
        WorkspaceStorageContext.WORKSPACE_PREFERENCES.forEach { name ->
            val guestName = WorkspaceStorageContext.GUEST_PREFIX + name
            // Clear Android's cached SharedPreferences before removing the disk file.
            preferencesDeleted = raw.getSharedPreferences(guestName, Context.MODE_PRIVATE).edit().clear().commit() && preferencesDeleted
            raw.deleteSharedPreferences(guestName)
        }
        val completed = databaseDeleted && filesDeleted && preferencesDeleted
        check(prefs.edit().putBoolean("cleanup_complete", completed).commit())
        return completed
    }
}
