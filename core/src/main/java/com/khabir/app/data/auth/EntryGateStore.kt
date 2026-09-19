package com.khabir.app.data.auth

import android.content.Context

class EntryGateStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("khabir_entry_gate", Context.MODE_PRIVATE)
    fun hasPassedGate(allowPreview: Boolean = false): Boolean =
        GoogleSession.auth(context)?.currentUser != null || (allowPreview && preferences.getBoolean("preview", false))
    fun markSkipped() { preferences.edit().putBoolean("preview", true).apply() }
    fun markGoogleSignedIn() { preferences.edit().clear().apply() }
    fun showGateAgain() { GoogleSession.auth(context)?.signOut(); preferences.edit().clear().apply() }
}
