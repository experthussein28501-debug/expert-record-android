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
internal fun ReportDocumentRequestDialog(pages: List<File>, busy: Boolean, error: String?, defaultTask: ReportDocumentTask = ReportDocumentTask.SUMMARY, defaultDestination: String? = null, onCancel: () -> Unit, onAnalyze: (List<ReportDocumentRequest>) -> Unit) {
    var groupIds by rememberSaveable(pages.map { it.path }) { mutableStateOf(pages.indices.toList()) }
    var tasks by rememberSaveable(pages.map { it.path }, defaultTask) { mutableStateOf(List(pages.size) { defaultTask }) }
    var destinations by rememberSaveable(pages.map { it.path }) {
        mutableStateOf(List(pages.size) { defaultDestination ?: ReportCaptureField.DOCUMENTS.name })
    }
    var requests by rememberSaveable(pages.map { it.path }) {
        mutableStateOf(List(pages.size) { defaultTask.defaultInstruction })
    }
    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("المطلوب من المستندات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    InlineHelp("تعليمات كل مستند", "حدد مجموعة لأي صور تخص مستندًا واحدًا ولو لم تكن متجاورة، واختر بند التقرير لكل مجموعة. ستراجع نتيجة كل مجموعة قبل اعتمادها.")
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    pages.forEachIndexed { index, file ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الصورة ${index + 1}")
                                ReportPageThumbnail(file)
                                var groupMenu by remember(file.path) { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(onClick = { groupMenu = true }, enabled = !busy) {
                                        Text("ضم إلى مجموعة ${groupIds[index] + 1} ▾")
                                    }
                                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                                        pages.indices.forEach { group ->
                                            DropdownMenuItem(text = { Text("المجموعة ${group + 1}") }, onClick = {
                                                val next = reassignReportPageGroup(
                                                    ReportPageGroupingState(groupIds, requests, tasks, destinations),
                                                    index,
                                                    group
                                                )
                                                groupIds = next.groupIds
                                                requests = next.instructions
                                                tasks = next.tasks
                                                destinations = next.destinations
                                                groupMenu = false
                                            })
                                        }
                                    }
                                }
                                if (groupIds.indexOf(groupIds[index]) == index) {
                                    Text("اختر المطلوب", style = MaterialTheme.typography.labelLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        FilterChip(
                                            selected = tasks[index] == ReportDocumentTask.SUBJECT,
                                            onClick = {
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.SUBJECT }
                                                requests = requests.toMutableList().also { it[index] = ReportDocumentTask.SUBJECT.defaultInstruction }
                                                destinations = destinations.toMutableList().also { it[index] = ReportCaptureField.SUBJECT.name }
                                            },
                                            label = { Text("موضوع") },
                                            enabled = !busy
                                        )
                                        FilterChip(
                                            selected = tasks[index] == ReportDocumentTask.ASSIGNMENT,
                                            onClick = {
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.ASSIGNMENT }
                                                requests = requests.toMutableList().also { it[index] = ReportDocumentTask.ASSIGNMENT.defaultInstruction }
                                                destinations = destinations.toMutableList().also { it[index] = ReportCaptureField.ASSIGNMENT.name }
                                            },
                                            label = { Text("مأمورية") },
                                            enabled = !busy
                                        )
                                        FilterChip(
                                            selected = tasks[index] == ReportDocumentTask.RESEARCH,
                                            onClick = {
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.RESEARCH }
                                                requests = requests.toMutableList().also { it[index] = ReportDocumentTask.RESEARCH.defaultInstruction }
                                                destinations = destinations.toMutableList().also { it[index] = ReportCaptureField.DOCUMENTS.name }
                                            },
                                            label = { Text("بحث مستند") },
                                            enabled = !busy
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        FilterChip(
                                            selected = tasks[index] == ReportDocumentTask.SUMMARY,
                                            onClick = {
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.SUMMARY }
                                                requests = requests.toMutableList().also { it[index] = ReportDocumentTask.SUMMARY.defaultInstruction }
                                            },
                                            label = { Text("تلخيص") },
                                            enabled = !busy
                                        )
                                        FilterChip(
                                            selected = tasks[index] == ReportDocumentTask.CONCLUSION,
                                            onClick = {
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.CONCLUSION }
                                                requests = requests.toMutableList().also { it[index] = ReportDocumentTask.CONCLUSION.defaultInstruction }
                                                destinations = destinations.toMutableList().also { it[index] = ReportCaptureField.CONCLUSION.name }
                                            },
                                            label = { Text("نتيجة") },
                                            enabled = !busy
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        listOf(ReportCaptureField.PLAINTIFF_DOCUMENTS to "حلل مستندات المدعين واستخرج الوقائع والأسماء والتواريخ المرتبطة بهم", ReportCaptureField.DEFENDANT_DOCUMENTS to "حلل مستندات المدعى عليهم واستخرج الوقائع والأسماء والتواريخ المرتبطة بهم").forEach { (field, prompt) ->
                                            FilterChip(selected = requests[index] == prompt, onClick = {
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.CUSTOM }
                                                requests = requests.toMutableList().also { it[index] = prompt }
                                                destinations = destinations.toMutableList().also { it[index] = field.name }
                                            }, label = { Text(field.label) }, enabled = !busy)
                                        }
                                    }
                                    var destinationMenu by remember(file.path) { mutableStateOf(false) }
                                    Box {
                                        val chosen = ReportCaptureField.entries.firstOrNull { it.name == destinations[index] }
                                        OutlinedButton(onClick = { destinationMenu = true }, enabled = !busy) {
                                            Text("مكان الإضافة: ${chosen?.label ?: "بحث المستندات"} ▾")
                                        }
                                        DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                                            ReportCaptureField.entries.filter { it != ReportCaptureField.CUSTOM || defaultDestination == it.name }.forEach { field ->
                                                DropdownMenuItem(text = { Text(field.label) }, onClick = {
                                                    destinations = destinations.toMutableList().also { it[index] = field.name }
                                                    destinationMenu = false
                                                })
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        requests[index],
                                        { value ->
                                            if (value.length <= 2000) {
                                                requests = requests.toMutableList().also { it[index] = value }
                                                tasks = tasks.toMutableList().also { it[index] = ReportDocumentTask.CUSTOM }
                                            }
                                        },
                                        label = { Text("ماذا تريد من هذا المستند؟") },
                                        minLines = 2,
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 3) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء") }
                    Button(onClick = { onAnalyze(groupReportPagesBySelection(pages, groupIds, requests, tasks, destinations)) },
                        enabled = !busy && pages.indices.all { groupIds.indexOf(groupIds[it]) != it || requests[it].isNotBlank() }, modifier = Modifier.weight(1f)) { Text(if (busy) "جارٍ التحليل…" else "تحليل المستندات") }
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
    // Compose may still draw the last frame after disposal. Let GC release
    // this bounded thumbnail rather than recycling a bitmap used by RenderThread.
}
