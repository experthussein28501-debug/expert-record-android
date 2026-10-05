package com.khabir.app.data.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.khabir.app.data.local.AppDatabase
import java.io.File

interface WorkspaceStorageOwner { val storageScope: WorkspaceStorageContext }

/** The scope is immutable for a process; switching scope requires restarting that process. */
class WorkspaceStorageContext(base: Context, val guest: Boolean) : ContextWrapper(base) {
    private fun directory(root: File): File = if (guest) File(root, GUEST_DIRECTORY).apply { mkdirs() } else root
    override fun getFilesDir(): File = directory(super.getFilesDir())
    override fun getCacheDir(): File = directory(super.getCacheDir())
    override fun getNoBackupFilesDir(): File = directory(super.getNoBackupFilesDir())
    private fun databaseName(name: String) = if (guest && name == AppDatabase.DB_NAME) GUEST_DATABASE else name
    override fun getDatabasePath(name: String): File = super.getDatabasePath(databaseName(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        super.openOrCreateDatabase(databaseName(name), mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        super.openOrCreateDatabase(databaseName(name), mode, factory, errorHandler)
    override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(databaseName(name))
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(if (guest && name in WORKSPACE_PREFERENCES) GUEST_PREFIX + name else name, mode)
    companion object {
        const val GUEST_DIRECTORY = "guest_workspace"
        const val GUEST_PREFIX = "guest_"
        val GUEST_DATABASE = GUEST_PREFIX + AppDatabase.DB_NAME
        val WORKSPACE_PREFERENCES = setOf("khabir_agenda_encrypted_v2", "khabir_agenda_days_v1", "khabir_backup_restore", "secure_ai_preferences", "activation_device")
        fun raw(context: Context): Context {
            var current = context
            while (current is ContextWrapper && current.baseContext != null && current.baseContext !== current) current = current.baseContext
            return current
        }
        fun forProcess(base: Context): WorkspaceStorageContext {
            val store = GuestTrialStore(base)
            return WorkspaceStorageContext(base, store.isActive())
        }
        fun isGuest(context: Context): Boolean {
            var current = context
            while (current is ContextWrapper) {
                if (current is WorkspaceStorageOwner) return current.storageScope.guest
                if (current is WorkspaceStorageContext) return current.guest
                if (current.baseContext == null || current.baseContext === current) break
                current = current.baseContext
            }
            return false
        }
    }
}
