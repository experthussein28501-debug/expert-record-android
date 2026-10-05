package com.khabir.app

import android.app.Application
import android.content.Context
import com.khabir.app.auth.GuestTrialRuntime
import com.khabir.app.data.auth.GuestTrialStore
import com.khabir.app.data.auth.WorkspaceStorageContext
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KhabirApplication : Application() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(WorkspaceStorageContext.forProcess(base)) }
    override fun onCreate() {
        super.onCreate()
        if (GuestTrialRuntime.isMainProcess(this)) {
            val store = GuestTrialStore(this)
            if (store.isExpired() && !store.cleanupComplete) store.deleteExpiredData()
            GuestTrialRuntime.schedule(this)
        }
    }
}
