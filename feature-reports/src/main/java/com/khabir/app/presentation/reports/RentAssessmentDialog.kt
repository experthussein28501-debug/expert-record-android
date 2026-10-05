package com.khabir.app.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khabir.app.domain.model.RentAssessmentInput
import com.khabir.app.domain.model.RentRatePeriod

@Composable
internal fun RentAssessmentDialog(onDismiss:()->Unit,onApprove:(RentAssessmentInput)->String?) {
    var claimedStart by rememberSaveable { mutableStateOf("") };var claimedEnd by rememberSaveable { mutableStateOf("") }
    var actualStart by rememberSaveable { mutableStateOf("") };var actualEnd by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") };var quantity by rememberSaveable { mutableStateOf("1") }
    var numerator by rememberSaveable { mutableStateOf("1") };var denominator by rememberSaveable { mutableStateOf("1") }
    var basis by rememberSaveable { mutableStateOf("") };var period by rememberSaveable { mutableStateOf(RentRatePeriod.YEAR) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("حساب الريع من البيانات المثبتة")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("التواريخ يوم/شهر/سنة. يُحسب تقاطع المدة المطالب بها مع وضع اليد الفعلي فقط؛ البداية والنهاية شاملتان. لا تُستنتج هذه البيانات من النموذج.")
            OutlinedTextField(claimedStart,{claimedStart=it},label={Text("بداية المطالبة")});OutlinedTextField(claimedEnd,{claimedEnd=it},label={Text("نهاية المطالبة")})
            OutlinedTextField(actualStart,{actualStart=it},label={Text("بداية وضع اليد المثبت")});OutlinedTextField(actualEnd,{actualEnd=it},label={Text("نهاية وضع اليد المثبت")})
            RentRatePeriod.entries.forEach { p -> FilterChip(selected=period==p,onClick={period=p},label={Text(p.label)}) }
            OutlinedTextField(rate,{rate=it},label={Text("القيمة الإيجارية المعتمدة")})
            OutlinedTextField(quantity,{quantity=it},label={Text("الكمية — المساحة إذا كان السعر للفدان؛ أو واحد لقيمة العين كلها")})
            OutlinedTextField(numerator,{numerator=it},label={Text("بسط نصيب المستحق")});OutlinedTextField(denominator,{denominator=it},label={Text("مقام نصيب المستحق")})
            OutlinedTextField(basis,{basis=it},label={Text("أسانيد القيمة والمدة والمساحة والنصيب")},minLines=3)
            Text("الفترة غير الكاملة تُحسب بنسبة أيامها إلى الشهر/السنة التقويمية؛ راجع صلاحية هذا الأساس قبل الاعتماد.")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={Button(onClick={ error=onApprove(RentAssessmentInput(claimedStart,claimedEnd,actualStart,actualEnd,rate,quantity,numerator,denominator,period,basis));if(error==null) onDismiss() }) {Text("حساب وإضافة لبند الحسابات")}},dismissButton={TextButton(onClick=onDismiss) {Text("إلغاء")}})
}
