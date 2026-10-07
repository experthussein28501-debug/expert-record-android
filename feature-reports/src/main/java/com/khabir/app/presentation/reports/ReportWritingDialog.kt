package com.khabir.app.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khabir.app.domain.model.*
import com.khabir.app.presentation.components.KhabirTextField
import com.khabir.app.presentation.components.ArabicListHangingIndentTransformation

/** One editor, with case identity and navigation outside the scrolling/keyboard area. */
@Composable
internal fun ReportWritingDialog(
    section: ReportSectionDefinition, state: ReportUiState, vm: ReportViewModel,
    onDismiss: () -> Unit, onAccounting: () -> Unit, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?,
    onCamera: (ReportCaptureField, String?) -> Unit, onMic: (ReportCaptureField, String?) -> Unit
) {
    val field = when(section.id) {
        "subject" -> ReportCaptureField.SUBJECT; "assignment" -> ReportCaptureField.ASSIGNMENT
        "proceedings" -> ReportCaptureField.PROCEEDINGS; "statements" -> ReportCaptureField.STATEMENTS
        "witnesses" -> ReportCaptureField.WITNESSES; "inspection" -> ReportCaptureField.INSPECTION
        "documents" -> ReportCaptureField.DOCUMENTS; "facts" -> ReportCaptureField.FACTS
        "research" -> ReportCaptureField.RESEARCH; "conclusion" -> ReportCaptureField.CONCLUSION
        "attachments" -> ReportCaptureField.ATTACHMENTS; "calculations" -> ReportCaptureField.CALCULATIONS
        else -> ReportCaptureField.CUSTOM
    }
    val value = when(field) {
        ReportCaptureField.SUBJECT -> state.subjectOfCase; ReportCaptureField.ASSIGNMENT -> state.assignment
        ReportCaptureField.PROCEEDINGS -> state.proceedings; ReportCaptureField.STATEMENTS -> state.partyStatements
        ReportCaptureField.WITNESSES -> state.witnessStatements; ReportCaptureField.INSPECTION -> state.inspection
        ReportCaptureField.DOCUMENTS -> state.documentsSubmitted; ReportCaptureField.FACTS -> state.facts
        ReportCaptureField.RESEARCH -> state.research; ReportCaptureField.CONCLUSION -> state.conclusion
        ReportCaptureField.ATTACHMENTS -> state.attachmentsNote
        else -> state.customSectionContents[section.id].orEmpty()
    }
    val focusRequester = remember(section.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(section.id) { if(field!=ReportCaptureField.CALCULATIONS) { focusRequester.requestFocus();keyboard?.show() } }
    val style = ReportListStyle.decode(state.customSectionContents[ReportListStyle.key(section.id)])
    var menu by remember(section.id) { mutableStateOf(false) }
    var showFormatting by remember(section.id) { mutableStateOf(false) }
    val supportsLists=section.id in setOf("documents","research","conclusion") || field==ReportCaptureField.CUSTOM
    val indentation=remember {ArabicListHangingIndentTransformation()}
    Dialog(onDismissRequest=onDismiss, properties=DialogProperties(usePlatformDefaultWidth=false, dismissOnClickOutside=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal=8.dp)) {
                Text("${state.caseNo.ifBlank { "بدون رقم" }}/${state.caseYear} — ${state.court}", style=MaterialTheme.typography.labelMedium)
                Text(state.partiesSummary, maxLines=1, style=MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(section.title, Modifier.weight(1f))
                    TextButton(onClick={onCamera(field,section.id.takeIf { field==ReportCaptureField.CUSTOM })}) {Text("تصوير")}
                    TextButton(onClick={onMic(field,section.id.takeIf { field==ReportCaptureField.CUSTOM })}) {Text("إملاء")}
                    Box {
                        TextButton(onClick={menu=true}) {Text("تنسيق")}
                        DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem(text={Text("قضايا حسابية")},onClick={menu=false;onAccounting()})
                            DropdownMenuItem(text={Text("الخط وحجم النص")},onClick={showFormatting=!showFormatting;menu=false})
                            if(supportsLists) ReportListStyle.entries.forEach { option ->
                            DropdownMenuItem(text={Text(option.label)},onClick={vm.onListStyleChanged(section.id,option);menu=false})
                        }}
                    }
                }
                if(showFormatting) ReportFormattingToolbar(ReportTextFormat.decode(state.customSectionContents[ReportTextFormat.KEY]),vm::onTextFormatChanged)
                if(section.headingLines.any(String::isNotBlank)) Text(section.headingLines.filter(String::isNotBlank).joinToString(" • "),style=MaterialTheme.typography.labelSmall,maxLines=2)
                if(field==ReportCaptureField.RESEARCH || field==ReportCaptureField.CONCLUSION) {
                    TextButton(enabled=!state.isAssistantWorking,onClick={onDismiss();vm.prepareSectionAi(if(field==ReportCaptureField.RESEARCH) ReportAiKind.RESEARCH else ReportAiKind.CONCLUSION)}) {Text("إعداد اقتراح ومراجعته")}
                }
                if(field==ReportCaptureField.CALCULATIONS) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ReportCalculationsEditor(state.calculationsTable,vm::onCalculationsChanged,{onCamera(field,null)},{onMic(field,null)})
                    }
                } else KhabirTextField(
                    value=value, onValueChange={ incoming ->
                        val updated=ReportLists.continueOnEnter(value,incoming,style)
                        if(field==ReportCaptureField.CUSTOM) vm.onCustomSectionChanged(section.id,updated) else field.write(vm,updated,true)
                    }, minLines=1,maxLines=Int.MAX_VALUE,modifier=Modifier.fillMaxWidth().weight(1f).focusRequester(focusRequester),
                    visualTransformation=indentation,
                    textStyle=reportEditorTextStyle(ReportTextFormat.decode(state.customSectionContents[ReportTextFormat.KEY])),
                    placeholder={Text("اكتب نص التقرير هنا")}
                )
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled=onPrevious!=null,onClick={onPrevious?.invoke()}) {Text("السابق")}
                    Button(enabled=!state.isSaving,onClick=vm::onSave,modifier=Modifier.weight(1f)) {Text("حفظ")}
                    TextButton(enabled=onNext!=null,onClick={onNext?.invoke()}) {Text("التالي")}
                    TextButton(onClick=onDismiss) {Text("رجوع")}
                }
            }
        }
    }
}
