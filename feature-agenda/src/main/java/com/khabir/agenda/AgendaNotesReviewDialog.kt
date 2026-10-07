package com.khabir.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khabir.app.presentation.components.KhabirTextField
import java.time.LocalDate

@Composable
internal fun AgendaNotesReviewDialog(review:AgendaNoteReview,onDismiss:()->Unit,onApprove:(List<AgendaNoteCandidate>)->Unit) {
    val rows=remember(review) {mutableStateListOf<AgendaNoteCandidate>().apply {addAll(review.candidates)}}
    val dates=remember(review) {mutableStateListOf<String>().apply {addAll(review.candidates.map {it.date.toString()})}}
    val selected=remember(review) {mutableStateListOf<Boolean>().apply {addAll(review.candidates.map {true})}}
    var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("مراجعة المواعيد من الملاحظات")},text={
        Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("اختر المواعيد وراجع اليوم والساعة. تثبت في الشيت عند حفظ اليوم، ويظل نص الملاحظات كما كتبته.")
            if(rows.isEmpty()) Text("لم يظهر موعد بتاريخ أو ساعة واضحة. يمكنك إضافة موعد يدويًا.")
            review.warnings.forEach {Text(it,color=MaterialTheme.colorScheme.error)}
            rows.indices.forEach { index ->
                val row=rows[index]
                Row {Checkbox(selected[index],{selected[index]=it});Text("موعد ${index+1}")}
                if(row.usedSelectedDate) Text("استُخدم اليوم المحدد أو سنته لاستكمال التاريخ؛ راجعه.",style=MaterialTheme.typography.labelSmall)
                KhabirTextField(dates[index],{dates[index]=it},label={Text("التاريخ YYYY-MM-DD")})
                KhabirTextField(row.appointment.title,{rows[index]=row.copy(appointment=row.appointment.copy(title=it))},label={Text("الموعد / القضية")})
                KhabirTextField(row.appointment.time,{rows[index]=row.copy(appointment=row.appointment.copy(time=it))},label={Text("الساعة")})
                KhabirTextField(row.appointment.location,{rows[index]=row.copy(appointment=row.appointment.copy(location=it))},label={Text("المكان")})
                Text("الملاحظة الأصلية: ${row.appointment.details}",style=MaterialTheme.typography.labelSmall)
                HorizontalDivider()
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={Button(enabled=selected.any {it},onClick={
        runCatching {rows.indices.filter {selected[it]}.map {index -> rows[index].copy(date=LocalDate.parse(dates[index])).also {require(it.appointment.title.isNotBlank()) {"أدخل عنوان الموعد"}}}}
            .onSuccess(onApprove).onFailure {error="راجع تاريخ الموعد وعنوانه"}
    }) {Text("إضافة للشيت")}},dismissButton={TextButton(onClick=onDismiss) {Text("إلغاء")}})
}
