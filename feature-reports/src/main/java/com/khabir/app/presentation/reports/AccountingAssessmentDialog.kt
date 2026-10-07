package com.khabir.app.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khabir.app.domain.model.*
import com.khabir.app.presentation.components.KhabirTextField

@Composable
internal fun AccountingAssessmentDialog(state:ReportUiState,vm:ReportViewModel,onDismiss:()->Unit,onImportExample:()->Unit) {
    val input=AccountingAssessment.decode(state.customSectionContents[AccountingAssessment.DRAFT_KEY].orEmpty())
    fun update(value:AccountingInput) = vm.onCustomSectionChanged(AccountingAssessment.DRAFT_KEY,AccountingAssessment.encode(value))
    var preview by remember {mutableStateOf<AccountingResult?>(null)}
    var previewInput by remember {mutableStateOf<AccountingInput?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("القضايا الحسابية — بداية إعداد الحساب")},text={
        Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("${state.caseNo}/${state.caseYear} — ${state.court}")
            Text("أدخل القاعدة من القرار والمستندات. لم تُحدد معدلات قانونية تلقائية. النموذج السابق يفيد في الهيكل والصياغة، وتراجع قواعد الحساب لكل قضية.")
            AccountingAssessment.categories.forEach { category -> FilterChip(selected=input.category==category,onClick={update(input.copy(category=category))},label={Text(category)}) }
            TextButton(onClick=onImportExample) {Text("استيراد تقرير Word معتمد للتعلم من أسلوبه")}
            KhabirTextField(input.person,{update(AccountingAssessment.changePerson(input,it))},label={Text("اسم العامل / المستحق — تغييره يصفر بيانات الحساب")})
            KhabirTextField(input.benefit,{update(input.copy(benefit=it))},label={Text("البند: مكافأة / إجازات / نهاية خدمة / غيره")})
            KhabirTextField(input.start,{update(input.copy(start=it))},label={Text("بداية الفترة YYYY-MM-DD")})
            KhabirTextField(input.end,{update(input.copy(end=it))},label={Text("نهاية الفترة YYYY-MM-DD")})
            AccountingPeriod.entries.forEach {period -> FilterChip(selected=input.period==period,onClick={update(input.copy(period=period))},label={Text(period.label)})}
            if(input.period==AccountingPeriod.COUNT) KhabirTextField(input.count,{update(input.copy(count=it))},label={Text("العدد المستحق المثبت")})
            KhabirTextField(input.rate,{update(input.copy(rate=it))},label={Text("قيمة الوحدة المعتمدة")})
            KhabirTextField(input.factor,{update(input.copy(factor=it))},label={Text("معامل القاعدة — أدخله من القرار المعتمد")})
            KhabirTextField(input.source,{update(input.copy(source=it))},minLines=3,label={Text("القرار / المستند / الصفحات وقاعدة الاحتساب")})
            TextButton(onClick={runCatching {AccountingAssessment.calculate(input)}.onSuccess {preview=it;previewInput=input;error=null}.onFailure {error=it.message ?: "راجع البيانات"}}) {Text("حساب وعرض المعادلة للمراجعة")}
            if(previewInput==input) preview?.let {Text(it.explanation)}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={Button(enabled=preview!=null && previewInput==input,onClick={error=vm.approveAccountingAssessment(input);if(error==null) onDismiss()}) {Text("اعتماد وإضافة للحسابات")}},dismissButton={TextButton(onClick=onDismiss) {Text("إغلاق — المسودة محفوظة")}})
}
