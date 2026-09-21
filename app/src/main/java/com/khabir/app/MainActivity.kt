package com.khabir.app

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.khabir.app.navigation.KhabirNavHost
import com.khabir.app.presentation.theme.KhabirTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var monetization: com.khabir.app.monetization.MonetizationController
    override fun onResume() { super.onResume(); if (::monetization.isInitialized) monetization.onResume() }
    override fun onPause() { if (::monetization.isInitialized) monetization.onPause(); super.onPause() }
    override fun onUserInteraction() { super.onUserInteraction(); if (::monetization.isInitialized) monetization.onInteraction() }
    override fun onDestroy() { if (::monetization.isInitialized) monetization.close(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        monetization = com.khabir.app.monetization.MonetizationController(this)
        setContent {
            KhabirTheme {
                CompositionLocalProvider(
                    com.khabir.app.monetization.LocalMonetization provides monetization,
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(
                        textAlign = TextAlign.Right,
                        textDirection = TextDirection.Rtl
                    )
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        KhabirNavHost()
                    }
                }
            }
        }
    }
}
