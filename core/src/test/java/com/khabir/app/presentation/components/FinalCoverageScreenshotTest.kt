package com.khabir.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.khabir.app.presentation.theme.KhabirTheme
import org.junit.Rule
import org.junit.Test

/**
 * اختبارات بصرية أخيرة — تغطي تحديدًا آخر حاجتين اتضافوا للمشروع:
 * (1) خط Cairo العربي المُفعّل حديثًا في KhabirTheme.kt، و
 * (2) دعم containerColor في KhabirCard.
 *
 * ليه مهمين بالذات: لو ملف الخط مش متسجل صح في res/font أو الـR.font
 * reference غلط، الصورة هتبان بخط النظام الافتراضي بدل Cairo أو
 * التطبيق يفشل في تحميل الموارد — وده بالظبط النوع اللي المراجعة اليدوية
 * للكود (بدون build) مش قادرة تكشفه. تشغيل
 * `./gradlew :core:recordPaparazziDebug` وفتح الصور الناتجة هو التأكيد
 * الحقيقي الوحيد إن الخط شغال فعلًا.
 */
class FinalCoverageScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun `arabic text renders across all typography weights with Cairo font`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("محرر التقرير — عنوان رئيسي", style = MaterialTheme.typography.headlineMedium)
                        Text("بيانات الدعوى للتقرير — عنوان قسم", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "هذا نص تجريبي بخط Cairo يشمل حروفًا متصلة ومنفصلة، وأرقامًا ١٢٣٤٥٦٧٨٩٠ وعلامات ترقيم؛ للتأكد من عدم ظهور مربعات فارغة (tofu) بدل الحروف.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("نص فرعي صغير — labelSmall", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    @Test
    fun `tinted KhabirCard renders with custom container color`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KhabirCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text("بطاقة بلون primaryContainer", style = MaterialTheme.typography.titleSmall)
                            Text("مستخدمة في بطاقات الترحيب والتنبيهات المهمة.", style = MaterialTheme.typography.bodySmall)
                        }
                        KhabirCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("بطاقة بلون secondaryContainer", style = MaterialTheme.typography.titleSmall)
                        }
                        KhabirCard {
                            Text("بطاقة بلون افتراضي — من غير containerColor", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `multiline field with supportingText and maxLines cap`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    KhabirTextField(
                        value = "نص طويل نسبيًا يوضح سلوك minLines وmaxLines معًا داخل نفس الحقل، ويجب ألا يتمدد أكثر من الحد الأقصى المسموح به للأسطر.",
                        onValueChange = {},
                        modifier = Modifier.padding(16.dp),
                        label = { Text("رأس التقرير") },
                        supportingText = { Text("مثال: تقرير في الدعوى رقم ... — نص توضيحي تحت الحقل") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        }
    }
}
