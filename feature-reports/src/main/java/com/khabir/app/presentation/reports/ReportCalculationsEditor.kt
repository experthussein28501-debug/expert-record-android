package com.khabir.app.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.khabir.app.domain.model.ReportCalculationCodec
import com.khabir.app.domain.model.ReportCalculationRow

@Composable
internal fun ReportCalculationsEditor(
    value: String,
    onChange: (String) -> Unit,
    onCamera: () -> Unit,
    onMic: () -> Unit
) {
    val rows = remember(value) { ReportCalculationCodec.decode(value) }

    fun save(updated: List<ReportCalculationRow>) {
        onChange(ReportCalculationCodec.encode(updated))
    }

    fun update(index: Int, transform: (ReportCalculationRow) -> ReportCalculationRow) {
        save(rows.toMutableList().also { it[index] = transform(it[index]) })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الحسابات والجداول", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onCamera) { Icon(Icons.Filled.CameraAlt, "تصوير جدول أو حسابات") }
            IconButton(onClick = onMic) { Icon(Icons.Filled.Mic, "إملاء بيانات الحسابات") }
        }
        Text("أدخل الكمية وقيمة الوحدة وسيحسب التطبيق نتيجة كل بند والإجمالي تلقائيًا.", style = MaterialTheme.typography.bodySmall)

        rows.forEachIndexed { index, row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("البند ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { save(rows.toMutableList().also { it.removeAt(index) }) }) {
                            Icon(Icons.Filled.Delete, "حذف البند ${index + 1}")
                        }
                    }
                    OutlinedTextField(value = row.label, onValueChange = { next -> update(index) { it.copy(label = next) } }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = row.quantity, onValueChange = { next -> update(index) { it.copy(quantity = next) } }, label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = row.unitValue, onValueChange = { next -> update(index) { it.copy(unitValue = next) } }, label = { Text("قيمة الوحدة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = row.unit, onValueChange = { next -> update(index) { it.copy(unit = next) } }, label = { Text("الوحدة") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = row.result.toPlainString(), onValueChange = {}, readOnly = true, label = { Text("الناتج") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = row.notes, onValueChange = { next -> update(index) { it.copy(notes = next) } }, label = { Text("ملاحظات البند") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        FilledTonalButton(onClick = { save(rows + ReportCalculationRow(id = System.nanoTime())) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("إضافة بند حسابي")
        }

        if (rows.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي", fontWeight = FontWeight.Bold)
                    Text(ReportCalculationCodec.total(value).toPlainString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

