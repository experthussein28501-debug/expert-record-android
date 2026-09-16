package com.khabir.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.khabir.core.R

/**
 * هوية "سجل الخبير" البصرية: أزرق ليلي رسمي (ink) كلون أساسي — يوحي بالرسمية
 * والثقة القضائية — مع لمسة نحاسية (brass) للتفعيل والعناصر المهمة، على خلفية
 * ورقية دافئة (paper) تحاكي ملف القضية الورقي. هذه الهوية مطبَّقة هنا مركزيًا
 * عبر ColorScheme + Shapes + Typography، فتنعكس تلقائيًا على كل الشاشات التي
 * تعتمد على MaterialTheme.colorScheme بدل الألوان الثابتة.
 */

// ---------- الألوان الأساسية ----------
private val Ink = Color(0xFF14213D)
private val InkSoft = Color(0xFF3C4A63)
private val InkDeep = Color(0xFF0C1730)
private val Paper = Color(0xFFFBF9F4)
private val PaperDim = Color(0xFFF2EEE4)
private val Line = Color(0xFFCEC6AC)
private val Brass = Color(0xFF9C7A3C)
private val BrassDeep = Color(0xFF6E5527)
private val BrassTint = Color(0xFFF1E6CE)
private val Alert = Color(0xFFA23B3B)
private val AlertTint = Color(0xFFF6E4E1)
private val Success = Color(0xFF3F6B4C)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = BrassTint,
    onPrimaryContainer = BrassDeep,
    secondary = Brass,
    onSecondary = Color.White,
    secondaryContainer = BrassTint,
    onSecondaryContainer = BrassDeep,
    tertiary = Success,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFEFB),
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkSoft,
    outline = Line,
    error = Alert,
    onError = Color.White,
    errorContainer = AlertTint,
    onErrorContainer = Alert
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9B98A),
    onPrimary = InkDeep,
    primaryContainer = BrassDeep,
    onPrimaryContainer = BrassTint,
    secondary = Color(0xFFD9C08C),
    onSecondary = InkDeep,
    background = InkDeep,
    onBackground = Paper,
    surface = Color(0xFF17223F),
    onSurface = Paper,
    surfaceVariant = Color(0xFF2A3958),
    onSurfaceVariant = Color(0xFFCBD1E0),
    outline = Color(0xFF5A6580),
    error = Color(0xFFE3A3A3),
    onError = InkDeep
)

// ---------- الأشكال — زوايا مستديرة هادئة تطابق شكل ملف رسمي، لا حواف حادة ولا مبالغة ----------
private val KhabirShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// ---------- الخطوط ----------
// خط Cairo العربي — مضمّن في res/font، يغطي كل حروف العربية والأرقام.
private val KhabirFontFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_bold, FontWeight.Bold)
)
private fun TextStyle.asArabicRtl(bold: Boolean = false): TextStyle = copy(
    fontFamily = KhabirFontFamily,
    textAlign = TextAlign.Right,
    textDirection = TextDirection.Rtl,
    fontWeight = if (bold) FontWeight.Bold else fontWeight
)

private val DefaultTypography = Typography()
private val ArabicTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.asArabicRtl(bold = true),
    displayMedium = DefaultTypography.displayMedium.asArabicRtl(bold = true),
    displaySmall = DefaultTypography.displaySmall.asArabicRtl(bold = true),
    headlineLarge = DefaultTypography.headlineLarge.asArabicRtl(bold = true),
    headlineMedium = DefaultTypography.headlineMedium.asArabicRtl(bold = true),
    headlineSmall = DefaultTypography.headlineSmall.asArabicRtl(bold = true),
    titleLarge = DefaultTypography.titleLarge.asArabicRtl(bold = true),
    titleMedium = DefaultTypography.titleMedium.asArabicRtl(),
    titleSmall = DefaultTypography.titleSmall.asArabicRtl(),
    bodyLarge = DefaultTypography.bodyLarge.asArabicRtl(),
    bodyMedium = DefaultTypography.bodyMedium.asArabicRtl(),
    bodySmall = DefaultTypography.bodySmall.asArabicRtl(),
    labelLarge = DefaultTypography.labelLarge.asArabicRtl(bold = true),
    labelMedium = DefaultTypography.labelMedium.asArabicRtl(),
    labelSmall = DefaultTypography.labelSmall.asArabicRtl()
)

@Composable
fun KhabirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ArabicTypography,
        shapes = KhabirShapes,
        content = content
    )
}
