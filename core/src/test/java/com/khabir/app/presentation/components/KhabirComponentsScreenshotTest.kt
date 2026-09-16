package com.khabir.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * اختبارات بصرية (screenshot tests) لمكونات الـDesign System الجديدة —
 * بتلتقط صورة فعلية للمكون وتقارنها بنسخة "معتمدة" محفوظة، فتكشف أي تغيير
 * بصري غير مقصود (لون غلط، padding اتغير، نص اتقص) حتى لو الكود بيتبني عادي.
 *
 * كيفية الاستخدام:
 *   1. أول مرة: `./gradlew :core:recordPaparazziDebug` — يحفظ الصور "المعتمدة"
 *      في core/src/test/snapshots/images.
 *   2. بعد كده: `./gradlew :core:verifyPaparazziDebug` — يقارن أي تغيير مستقبلي
 *      بالنسخة المعتمدة ويفشل الاختبار لو في فرق بصري غير متوقع.
 *   3. لو التغيير مقصود (تحسين ديزاين فعلي)، أعد تسجيل الصور بالأمر الأول.
 *
 * ملاحظة: مقدرش أشغّل الأمرين دول هنا (تحتاج Gradle sync كامل بإنترنت لتحميل
 * محرك الرسم الخاص بـPaparazzi)، فالصور "المعتمدة" لسه مش موجودة. أول تشغيل
 * لازم يكون بأمر recordPaparazziDebug عندك عشان تتولد.
 */
class KhabirComponentsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun `text field with label`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    KhabirTextField(
                        value = "دعوى 123 لسنة 2026",
                        onValueChange = {},
                        modifier = Modifier.padding(16.dp),
                        label = { Text("رقم الدعوى") }
                    )
                }
            }
        }
    }

    @Test
    fun `card with title and body text`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    KhabirCard(modifier = Modifier.padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("دعوى 45 لسنة 2026")
                            Text("محكمة أسوان الابتدائية")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `primary and secondary buttons stacked`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KhabirPrimaryButton(text = "حفظ بيانات الخبير", onClick = {})
                        KhabirSecondaryButton(text = "إلغاء", onClick = {})
                    }
                }
            }
        }
    }

    @Test
    fun `disabled primary button`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    KhabirPrimaryButton(
                        text = "جارٍ الحفظ...",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    @Test
    fun `section header`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    KhabirSectionHeader("مزود الذكاء الاصطناعي", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
