package com.khabir.app.data.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.khabir.core.BuildConfig

object GoogleSession {
    @Synchronized fun auth(context: Context): FirebaseAuth? {
        if (listOf(BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_PROJECT_ID).any { it.isBlank() }) return null
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context.applicationContext, FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY).setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build())
        return app?.let(FirebaseAuth::getInstance)
    }
}
