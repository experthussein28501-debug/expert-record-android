package com.khabir.app.presentation.reports

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khabir.app.presentation.components.InlineHelp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReportDocumentRequestDialog(pages: List<File>, busy: Boolean, error: String?, onCancel: () -> Unit, onAnalyze: (List<ReportDocumentRequest>) -> Unit) {
    var joins by rememberSaveable(pages.map { it.path }) { mutableStateOf(List(pages.size) { false }) }
    var requests by rememberSaveable(pages.map { it.path }) { mutableStateOf(List(pages.size) { "لخص هذا المستند مع الحفاظ على الأرقام والتواريخ المهمة" }) }
    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("المطلوب من المستندات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    InlineHelp("تعليمات كل مستند", "كل صورة تبدأ مستندًا مستقلًا. لو الصورة صفحة مكملة، اختر تابع للمستند السابق. اكتب طلبك لكل مستند ثم راجع النتائج قبل إضافتها للتقرير.")
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    pages.forEachIndexed { index, file ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الصورة ${index + 1}")
                                ReportPageThumbnail(file)
                                if (index > 0) Row {
                                    Checkbox(joins[index], { checked -> joins = joins.toMutableList().also { it[index] = checked } }, enabled = !busy)
                                    Text("تابع للمستند السابق", Modifier.padding(top = 12.dp))
                                }
                                if (!joins[index] || index == 0) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(enabled = !busy, onClick = { requests = requests.toMutableList().also { it[index] = "لخص هذا المستند مع الحفاظ على الأرقام والتواريخ المهمة" } }) { Text("ملخص") }
                                        TextButton(enabled = !busy, onClick = { requests = requests.toMutableList().also { it[index] = "استخرج النتيجة النهائية الواردة في المستند، واذكر إذا لم توجد نتيجة صريحة" } }) { Text("النتيجة النهائية") }
                                    }
                                    OutlinedTextField(requests[index], { value -> if (value.length <= 2000) requests = requests.toMutableList().also { it[index] = value } },
                                        label = { Text("ماذا تريد من هذا المستند؟") }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 3) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء") }
                    Button(onClick = { onAnalyze(groupReportPages(pages, joins, requests)) },
                        enabled = !busy && pages.indices.all { (it > 0 && joins[it]) || requests[it].isNotBlank() }, modifier = Modifier.weight(1f)) { Text(if (busy) "جارٍ التحليل…" else "تحليل المستندات") }
                }
            }
        }
    }
}

@Composable
private fun ReportPageThumbnail(file: File) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 400).coerceAtLeast(1) }
            BitmapFactory.decodeFile(file.path, options)
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "معاينة المستند", Modifier.fillMaxWidth().height(110.dp)) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
}
