package com.khabir.app.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun InlineHelp(title: String, message: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "مساعدة: $title")
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("فهمت") } }
    )
}
