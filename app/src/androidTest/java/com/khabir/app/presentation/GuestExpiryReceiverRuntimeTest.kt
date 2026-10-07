package com.khabir.app.presentation

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khabir.app.auth.GuestExpiryReceiver
import com.khabir.app.data.auth.GuestTrialPolicy
import com.khabir.app.data.auth.GuestTrialStore
import com.khabir.app.data.auth.WorkspaceStorageContext
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestExpiryReceiverRuntimeTest {
    @Test fun secondaryProcessDeletesGuestDataAndPreservesAccountAndExpiryHistory() {
        val raw = WorkspaceStorageContext.raw(InstrumentationRegistry.getInstrumentation().targetContext)
        raw.deleteDatabase(GuestTrialStore.CONTROL_DATABASE)
        val guest = WorkspaceStorageContext(raw, true)
        val marker = File(raw.filesDir, "receiver-account-marker")
        try {
            marker.writeText("account")
            File(guest.filesDir, "receiver-guest-marker").writeText("guest")
            guest.openOrCreateDatabase(WorkspaceStorageContext.GUEST_DATABASE, Context.MODE_PRIVATE, null).close()
            GuestTrialStore(raw) { System.currentTimeMillis() - GuestTrialPolicy.DURATION_MILLIS - 1000 }.start()
            raw.sendBroadcast(Intent(raw, GuestExpiryReceiver::class.java))
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!GuestTrialStore(raw).cleanupComplete && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue("Expiry receiver did not finish cleanup", GuestTrialStore(raw).cleanupComplete)
            assertFalse(File(raw.filesDir, "guest_workspace/receiver-guest-marker").exists())
            assertFalse(raw.getDatabasePath(WorkspaceStorageContext.GUEST_DATABASE).exists())
            assertEquals("account", marker.readText())
            assertFalse(GuestTrialStore(raw).canStart)
            assertTrue(GuestTrialStore(raw).isExpired())
        } finally {
            marker.delete()
            raw.deleteDatabase(GuestTrialStore.CONTROL_DATABASE)
        }
    }
}
