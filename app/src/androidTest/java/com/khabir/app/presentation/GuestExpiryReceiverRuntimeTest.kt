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
    @Test fun legacyExpiryBroadcastPreservesLocalDataAndAccess() {
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
            // Exercise the callback synchronously as well as delivery to the legacy process.
            GuestExpiryReceiver().onReceive(raw, Intent(raw, GuestExpiryReceiver::class.java))
            SystemClock.sleep(500)
            assertFalse(GuestTrialStore(raw).cleanupComplete)
            assertTrue(File(raw.filesDir, "guest_workspace/receiver-guest-marker").exists())
            assertTrue(raw.getDatabasePath(WorkspaceStorageContext.GUEST_DATABASE).exists())
            assertEquals("account", marker.readText())
            assertFalse(GuestTrialStore(raw).canStart)
            assertFalse(GuestTrialStore(raw).isExpired())
            assertTrue(GuestTrialStore(raw).isActive())
        } finally {
            marker.delete()
            raw.deleteDatabase(GuestTrialStore.CONTROL_DATABASE)
        }
    }
}
