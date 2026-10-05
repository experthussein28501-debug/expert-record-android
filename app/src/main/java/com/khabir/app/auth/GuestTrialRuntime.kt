package com.khabir.app.auth

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import com.khabir.app.MainActivity
import com.khabir.app.data.auth.GuestTrialStore
import com.khabir.app.data.auth.WorkspaceStorageContext

object GuestTrialRuntime {
    fun isMainProcess(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?.any { it.pid == Process.myPid() && it.processName == context.packageName } == true
    fun schedule(context: Context) {
        val store = GuestTrialStore(context)
        if (store.startedAt == 0L || store.cleanupComplete) return
        val delay = if (store.isExpired()) 15L * 60 * 1000 else store.remainingMillis().coerceAtLeast(1000)
        val intent = Intent(context, GuestExpiryReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, 7017, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pending)
    }
    fun restart(context: Context) {
        context.startActivity(Intent(context, WorkspaceRestartActivity::class.java)
            .putExtra("old_pid", Process.myPid()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun cleanupExpired(context: Context, oldPid: Int = -1) {
        val store = GuestTrialStore(context)
        if (!store.isExpired()) return
        store.markExpired()
        run {
            val pid = if (oldPid > 0) oldPid else store.guestProcess()
            val processes = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            if (pid > 0 && pid != Process.myPid() && processes?.any { it.pid == pid && it.processName == context.packageName } == true) Process.killProcess(pid)
        }
        store.deleteExpiredData()
        schedule(context)
    }
}

/** Runs outside the UI/Room process, so no guest writer survives deletion. */
class GuestExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try { runCatching { if (GuestTrialStore(context).isExpired()) GuestTrialRuntime.cleanupExpired(context) else GuestTrialRuntime.schedule(context) } }
            finally { GuestTrialRuntime.schedule(context); result.finish() }
        }.start()
    }
}

/** Scope switches must recreate Hilt singletons; an Activity recreation is insufficient. */
class WorkspaceRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = WorkspaceStorageContext.raw(this)
        val oldPid = intent.getIntExtra("old_pid", -1)
        val processes = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
        if (oldPid > 0 && oldPid != Process.myPid() && processes?.any { it.pid == oldPid && it.processName == packageName } == true) Process.killProcess(oldPid)
        if (GuestTrialStore(raw).isExpired()) GuestTrialRuntime.cleanupExpired(raw)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
