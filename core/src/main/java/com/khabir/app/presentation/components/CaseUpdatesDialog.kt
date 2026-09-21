package com.khabir.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khabir.app.domain.model.CaseFieldUpdate

@Composable
fun CaseUpdatesDialog(updates: List<CaseFieldUpdate>, onDismiss: () -> Unit, onApply: (Set<String>) -> Unit) {
    var selected by remember(updates) { mutableStateOf(emptySet<String>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("مراجعة تحديثات القضية") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                updates.forEach { update ->
                    Column {
                        Row { Checkbox(update.key in selected, { checked -> selected = if (checked) selected + update.key else selected - update.key }); Text(update.key, Modifier.padding(top = 12.dp)) }
                        Text("المحفوظ: " + update.current.ifBlank { "—" })
                        Text("في القضية: " + update.proposed.ifBlank { "—" }, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }, confirmButton = { TextButton(enabled = selected.isNotEmpty(), onClick = { onApply(selected) }) { Text("تحديث المحدد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("الاحتفاظ بالمحفوظ") } })
}
