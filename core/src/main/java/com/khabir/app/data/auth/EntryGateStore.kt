package com.khabir.app.data.auth

import android.content.Context

class EntryGateStore(context: Context) {
    private val preferences = context.getSharedPreferences("khabir_entry_gate", Context.MODE_PRIVATE)

    fun hasPassedGate(): Boolean = preferences.getBoolean(KEY_PASSED, false)

    fun markSkipped() {
        preferences.edit().putBoolean(KEY_PASSED, true).putString(KEY_METHOD, METHOD_SKIP).apply()
    }

    fun markGoogleSignedIn() {
        preferences.edit().putBoolean(KEY_PASSED, true).putString(KEY_METHOD, METHOD_GOOGLE).apply()
    }

    fun markActivated() {
        preferences.edit().putBoolean(KEY_PASSED, true).putString(KEY_METHOD, METHOD_CODE).apply()
    }

    fun showGateAgain() {
        preferences.edit().remove(KEY_PASSED).remove(KEY_METHOD).apply()
    }

    companion object {
        private const val KEY_PASSED = "passed"
        private const val KEY_METHOD = "method"
        private const val METHOD_SKIP = "skip"
        private const val METHOD_GOOGLE = "google"
        private const val METHOD_CODE = "activation_code"
    }
}
