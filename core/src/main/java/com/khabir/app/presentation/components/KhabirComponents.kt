package com.khabir.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * مكونات ديزاين موحّدة لتطبيق "سجل الخبير".
 *
 * الهدف: مركزة شكل الحقول والبطاقات والأزرار في مكان واحد بدل ما كل شاشة
 * تعيد تعريفها بنفسها، عشان أي تعديل ديزاين مستقبلي (padding، shape، ألوان)
 * يتغير من هنا فقط وينعكس على كل الشاشات اللي تستخدمه.
 *
 * هذه المكونات إضافية (additive) ولا تُستخدم بعد في الشاشات القديمة — استبدال
 * `OutlinedTextField`/`Card`/`Button` المباشرة بيها في الشاشات الحالية شغل
 * منفصل يحتاج مراجعة سطر بسطر لكل شاشة، لأن تغييره جماعيًا بدون بيئة بناء
 * محلية لمراجعة الناتج فيه مخاطرة كسر شاشات شغالة فعليًا.
 */

/** حقل نص موحّد الشكل — نفس OutlinedTextField لكن بمقاسات وألوان الهوية مركزية. */
@Composable
fun KhabirTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = if (minLines == 1) 1 else Int.MAX_VALUE,
    singleLine: Boolean = minLines == 1 && maxLines == 1,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { com.khabir.app.data.monetization.WorkAdEvents.interacted(); onValueChange(it) },
        modifier = modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        readOnly = readOnly,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.95f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** بطاقة موحّدة الشكل لعرض عناصر القوائم (قضايا، تقارير، إخطارات). */
@Composable
fun KhabirCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    containerColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit
) {
    val colors = containerColor?.let { CardDefaults.elevatedCardColors(containerColor = it) }
        ?: CardDefaults.elevatedCardColors()
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/** الزر الأساسي الموحّد — إجراءات الحفظ والتأكيد الرئيسية. */
@Composable
fun KhabirPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Text(text)
    }
}

/** الزر الثانوي الموحّد — إجراءات مساعدة (إلغاء، حذف، رجوع لخطوة سابقة). */
@Composable
fun KhabirSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Text(text)
    }
}

/** عنوان قسم موحّد الشكل — مستخدم بكثرة في شاشات التقارير وبيانات القضايا. */
@Composable
fun KhabirSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}
