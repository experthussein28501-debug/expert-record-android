package com.khabir.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.khabir.app.auth.GuestTrialRuntime
import com.khabir.app.data.auth.GuestTrialStore
import com.khabir.app.data.auth.WorkspaceStorageContext
import com.khabir.app.data.auth.WorkspaceStorageOwner
import java.io.File
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KhabirApplication : Application(), WorkspaceStorageOwner {
    override lateinit var storageScope: WorkspaceStorageContext
        private set
    override fun attachBaseContext(base: Context) {
        storageScope = WorkspaceStorageContext.forProcess(base)
        // ActivityThread requires Application.baseContext to be ContextImpl for receivers.
        super.attachBaseContext(base)
    }
    override fun getFilesDir(): File = storageScope.filesDir
    override fun getCacheDir(): File = storageScope.cacheDir
    override fun getNoBackupFilesDir(): File = storageScope.noBackupFilesDir
    override fun getDatabasePath(name: String): File = storageScope.getDatabasePath(name)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = storageScope.openOrCreateDatabase(name, mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase = storageScope.openOrCreateDatabase(name, mode, factory, errorHandler)
    override fun deleteDatabase(name: String): Boolean = storageScope.deleteDatabase(name)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = storageScope.getSharedPreferences(name, mode)
    override fun onCreate() {
        super.onCreate()
        if (GuestTrialRuntime.isMainProcess(this)) {
            val store = GuestTrialStore(this)
            if (store.isExpired() && !store.cleanupComplete) store.deleteExpiredData()
            GuestTrialRuntime.schedule(this)
        }
    }
}
