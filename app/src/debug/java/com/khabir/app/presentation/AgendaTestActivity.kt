package com.khabir.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.khabir.agenda.AgendaScreen
import com.khabir.app.presentation.theme.KhabirTheme
import dagger.hilt.android.AndroidEntryPoint

/** Instrumentation host only; excluded from hardened and release variants. */
@AndroidEntryPoint
class AgendaTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhabirTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AgendaScreen(onBack = {})
                }
            }
        }
    }
}
