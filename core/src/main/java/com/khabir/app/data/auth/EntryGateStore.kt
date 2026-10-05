package com.khabir.app.data.auth

import android.content.Context

class EntryGateStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("khabir_entry_gate", Context.MODE_PRIVATE)
    fun hasGoogleAccount(): Boolean {
        val user = GoogleSession.auth(context)?.currentUser
        return GoogleAccountGate.allows(user != null, user?.isAnonymous != false, user?.providerData?.map { it.providerId }.orEmpty())
    }
    fun hasPassedGate(): Boolean = hasGoogleAccount() || GuestTrialStore(context).isActive()
    fun markGoogleSignedIn() { check(hasGoogleAccount()); preferences.edit().clear().apply() }
    fun showGateAgain() { GoogleSession.auth(context)?.signOut(); preferences.edit().clear().apply() }
}
