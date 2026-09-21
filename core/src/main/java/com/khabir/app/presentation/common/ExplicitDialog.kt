package com.khabir.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

val ExplicitDialogProperties: DialogProperties
    get() = DialogProperties(
        dismissOnClickOutside = false,
        dismissOnBackPress = false
    )

@Composable
fun ExplicitDialogTitle(
    text: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text)
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "إغلاق")
        }
    }
}
