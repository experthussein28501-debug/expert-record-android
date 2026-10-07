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
import com.khabir.app.MainActivity
import com.khabir.app.data.auth.GuestTrialStore
import com.khabir.app.data.auth.WorkspaceStorageContext

object GuestTrialRuntime {
    fun isMainProcess(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?.any { it.pid == Process.myPid() && it.processName == context.packageName } == true
    fun schedule(context: Context) {
        // Cancel the exact PendingIntent used by pre-0.9.19 expiry alarms.
        val intent = Intent(context, GuestExpiryReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, 7017, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }
    fun restart(context: Context) {
        context.startActivity(Intent(context, WorkspaceRestartActivity::class.java)
            .putExtra("old_pid", Process.myPid()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @Suppress("UNUSED_PARAMETER")
    fun cleanupExpired(context: Context, oldPid: Int = -1) { schedule(context) }

}

/** Retained so broadcasts queued by older installations are harmless. */
class GuestExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { GuestTrialRuntime.schedule(context) }
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
