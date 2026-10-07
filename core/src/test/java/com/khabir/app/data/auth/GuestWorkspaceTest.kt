package com.khabir.app.data.auth

import android.content.Context
import android.content.ContextWrapper
import com.khabir.app.data.local.AppDatabase
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GuestWorkspaceTest {
    private lateinit var raw: Context
    private var now = 1_700_000_000_000L
    @Before fun prepare() {
        raw = WorkspaceStorageContext.raw(RuntimeEnvironment.getApplication())
        raw.deleteDatabase(GuestTrialStore.CONTROL_DATABASE)
        now = 1_700_000_000_000L
    }
    private fun store() = GuestTrialStore(raw) { now }
    @Test fun expiryDeletesEveryGuestStoreAndPreservesAccountAndTrialHistory() {
        val guest = WorkspaceStorageContext(raw, true)
        val account = WorkspaceStorageContext(raw, false)
        store().start()
        File(guest.filesDir, "source.jpg").writeText("guest")
        File(guest.cacheDir, "temporary.jpg").writeText("guest")
        File(guest.noBackupFilesDir, "draft").writeText("guest")
        File(account.filesDir, "account.jpg").writeText("account")
        guest.openOrCreateDatabase(AppDatabase.DB_NAME, Context.MODE_PRIVATE, null).close()
        account.openOrCreateDatabase(AppDatabase.DB_NAME, Context.MODE_PRIVATE, null).close()
        guest.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE).edit().putString("key", "guest-key").commit()
        account.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE).edit().putString("key", "account-key").commit()
        now += GuestTrialPolicy.DURATION_MILLIS
        assertTrue(store().deleteExpiredData())
        assertFalse(File(raw.filesDir, "guest_workspace/source.jpg").exists())
        assertFalse(File(raw.cacheDir, "guest_workspace/temporary.jpg").exists())
        assertFalse(File(raw.noBackupFilesDir, "guest_workspace/draft").exists())
        assertFalse(raw.getDatabasePath(WorkspaceStorageContext.GUEST_DATABASE).exists())
        assertTrue(raw.getDatabasePath(AppDatabase.DB_NAME).exists())
        assertEquals("account", File(account.filesDir, "account.jpg").readText())
        assertEquals("account-key", account.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE).getString("key", null))
        assertNull(raw.getSharedPreferences("guest_secure_ai_preferences", Context.MODE_PRIVATE).getString("key", null))
        assertFalse(store().canStart)
        assertFalse(store().isActive())
        assertTrue(store().cleanupComplete)
    }
    @Test fun guestAndAccountHaveIndependentPathsAndPreferences() {
        val guest = WorkspaceStorageContext(raw, true)
        assertNotEquals(raw.filesDir, guest.filesDir)
        assertNotEquals(raw.cacheDir, guest.cacheDir)
        assertNotEquals(raw.noBackupFilesDir, guest.noBackupFilesDir)
        assertNotEquals(raw.getDatabasePath(AppDatabase.DB_NAME), guest.getDatabasePath(AppDatabase.DB_NAME))
        assertEquals(raw.getDatabasePath("firebase.db"), guest.getDatabasePath("firebase.db"))
        guest.getSharedPreferences("khabir_agenda_days_v1", 0).edit().putString("1", "guest").commit()
        assertNull(raw.getSharedPreferences("khabir_agenda_days_v1", 0).getString("1", null))
    }
    @Test fun signingInDoesNotResetTheDeadlineOrDeleteTheAccountFiles() {
        store().start()
        val started = store().startedAt
        store().useGoogleAccount()
        assertFalse(store().isActive())
        assertFalse(store().canStart)
        assertEquals(started, store().startedAt)
        now += GuestTrialPolicy.DURATION_MILLIS
        assertTrue(store().isExpired())
    }
    @Test fun earlyDeletionIsRejected() {
        store().start()
        assertThrows(IllegalStateException::class.java) { store().deleteExpiredData() }
        assertTrue(store().isActive())
    }
    @Test fun legacyUnlimitedPreviewFlagNeverOpensTheApplication() {
        raw.getSharedPreferences("khabir_entry_gate", 0).edit().putBoolean("preview", true).commit()
        assertFalse(EntryGateStore(raw).hasPassedGate())
    }
    @Test fun rollbackDoesNotMoveTheLastObservedTimeBackwards() {
        store().start()
        now += 100_000
        val observed = store().effectiveNow()
        now -= 500_000
        assertTrue(store().effectiveNow() >= observed)
    }
    @Test fun separateStoreInstancesObserveModeAndTerminalExpiryWithoutCachedPreferences() {
        val ui = store()
        val cleanup = store()
        ui.start()
        ui.recordGuestProcess(123)
        assertEquals(123, cleanup.guestProcess())
        ui.useGoogleAccount()
        assertEquals(-1, cleanup.guestProcess())
        assertFalse(cleanup.inGuestMode)
        now += GuestTrialPolicy.DURATION_MILLIS
        cleanup.markExpired()
        assertFalse(ui.canStart)
        assertFalse(ui.isActive())
    }
    @Test fun activityScopeStaysWithItsApplicationWhenTheTrialEndsDuringLaunch() {
        val application = object : ContextWrapper(raw), WorkspaceStorageOwner {
            override val storageScope = WorkspaceStorageContext(raw, true)
            override fun getApplicationContext(): Context = this
        }
        store().start()
        now += GuestTrialPolicy.DURATION_MILLIS
        store().markExpired()
        assertFalse(store().isActive())
        assertTrue(WorkspaceStorageContext.forProcess(application).guest)
        assertTrue(WorkspaceStorageContext.isGuest(application))
    }
}
