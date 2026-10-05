package com.khabir.app.data.auth

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.provider.Settings
import java.io.File

/** Trial metadata is outside guest data and backups; deleting a workspace never renews the trial. */
class GuestTrialStore(context: Context, private val wallClock: () -> Long = System::currentTimeMillis) {
    private val raw = WorkspaceStorageContext.raw(context)
    // SharedPreferences caches are unsafe across the UI and expiry processes. SQLite
    // transactions give both processes one durable clock and terminal expiry marker.
    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        raw.openOrCreateDatabase(CONTROL_DATABASE, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS trial_state (name TEXT PRIMARY KEY, value INTEGER NOT NULL)")
            db.beginTransaction()
            try {
                val value = block(db)
                db.setTransactionSuccessful()
                return value
            } finally { db.endTransaction() }
        }
    }
    private fun read(db: SQLiteDatabase, name: String, fallback: Long = 0): Long =
        db.query("trial_state", arrayOf("value"), "name=?", arrayOf(name), null, null, null).use {
            if (it.moveToFirst()) it.getLong(0) else fallback
        }
    private fun put(db: SQLiteDatabase, name: String, value: Long) {
        check(db.insertWithOnConflict("trial_state", null, ContentValues().apply {
            put("name", name); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    val startedAt: Long get() = transaction { read(it, "started") }
    val inGuestMode: Boolean get() = transaction { read(it, "guest_mode") == 1L }
    val canStart: Boolean get() = transaction { GuestTrialPolicy.mayStart(read(it, "started"), read(it, "expired") == 1L) }
    val cleanupComplete: Boolean get() = transaction { read(it, "cleanup_complete") == 1L }
    private fun bootCount(): Int = Settings.Global.getInt(raw.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun effectiveNow(db: SQLiteDatabase): Long {
        val wall = wallClock()
        val start = read(db, "started")
        val boot = bootCount()
        val monotonic = if (start > 0 && boot >= 0 && boot.toLong() == read(db, "boot", -2)) {
            start + (SystemClock.elapsedRealtime() - read(db, "elapsed_start")).coerceAtLeast(0)
        } else wall
        val now = maxOf(wall, monotonic, read(db, "last_seen"))
        put(db, "last_seen", now)
        return now
    }
    fun effectiveNow(): Long = transaction { effectiveNow(it) }
    private fun isExpired(db: SQLiteDatabase): Boolean = GuestTrialPolicy.expired(read(db, "started"), effectiveNow(db), read(db, "expired") == 1L)
    fun isExpired(): Boolean = transaction { isExpired(it) }
    fun isActive(): Boolean = transaction { read(it, "guest_mode") == 1L && read(it, "started") > 0 && !isExpired(it) }
    fun remainingMillis(): Long = transaction { GuestTrialPolicy.remaining(read(it, "started"), effectiveNow(it)) }
    fun start() = transaction { db ->
        check(GuestTrialPolicy.mayStart(read(db, "started"), read(db, "expired") == 1L)) { "انتهت فرصة التجربة؛ سجل الدخول بحساب Google للاستمرار" }
        val now = wallClock()
        check(now > 0)
        put(db, "started", now); put(db, "last_seen", now)
        put(db, "elapsed_start", SystemClock.elapsedRealtime()); put(db, "boot", bootCount().toLong())
        put(db, "guest_mode", 1)
    }
    fun useGoogleAccount() = transaction { put(it, "guest_mode", 0); put(it, "guest_pid", -1) }
    fun markExpired() = transaction { db ->
        check(isExpired(db))
        put(db, "expired", 1); put(db, "guest_mode", 0)
    }
    fun recordGuestProcess(pid: Int) = transaction { db ->
        put(db, "guest_pid", if (read(db, "guest_mode") == 1L && !isExpired(db)) pid.toLong() else -1)
    }
    fun guestProcess(): Int = transaction { read(it, "guest_pid", -1).toInt() }
    /** Caller must close/stop the guest process before removing its database. */
    fun deleteExpiredData(): Boolean {
        check(isExpired()) { "لم تنته مدة التجربة" }
        markExpired()
        val database = raw.getDatabasePath(WorkspaceStorageContext.GUEST_DATABASE)
        raw.deleteDatabase(WorkspaceStorageContext.GUEST_DATABASE)
        val databaseDeleted = listOf("", "-wal", "-shm", "-journal").none { File(database.path + it).exists() }
        val filesDeleted = listOf(raw.filesDir, raw.cacheDir, raw.noBackupFilesDir)
            .map { File(it, WorkspaceStorageContext.GUEST_DIRECTORY) }
            .map { !it.exists() || it.deleteRecursively() }.all { it }
        var preferencesDeleted = true
        WorkspaceStorageContext.WORKSPACE_PREFERENCES.forEach { name ->
            val guestName = WorkspaceStorageContext.GUEST_PREFIX + name
            // Clear Android's cached SharedPreferences before removing the disk file.
            preferencesDeleted = raw.getSharedPreferences(guestName, Context.MODE_PRIVATE).edit().clear().commit() && preferencesDeleted
            preferencesDeleted = raw.deleteSharedPreferences(guestName) && preferencesDeleted
        }
        val completed = databaseDeleted && filesDeleted && preferencesDeleted
        transaction { put(it, "cleanup_complete", if (completed) 1 else 0) }
        return completed
    }
    companion object { const val CONTROL_DATABASE = "khabir_guest_trial_control.db" }
}
