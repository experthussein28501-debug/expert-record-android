package com.khabir.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.khabir.app.presentation.theme.KhabirTheme
import org.junit.Rule
import org.junit.Test

/**
 * اختبارات بصرية لتركيبات شاشة كاملة (لا مكون منفرد) مبنية بالكامل من
 * KhabirComponents — بدون ViewModel أو Hilt، عشان تفضل تشتغل بدون بيئة
 * حقن تبعيات كاملة. أهم حاجة هنا اختبار RTL صراحة، لأن التطبيق عربي بالكامل
 * وأي خطأ في اتجاه التخطيط (padding غلط، أيقونة في الجهة الغلط) بيبان بصريًا
 * هنا حتى لو الكود اتبنى عادي.
 *
 * التشغيل: نفس تعليمات KhabirComponentsScreenshotTest — أول مرة
 * `./gradlew :core:recordPaparazziDebug`.
 */
class ScreenLayoutScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun `filter form section — mirrors RegisterScreen and ExpertProfileScreen layout`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("استخراج مستقل حسب النوع والفترة والمحكمة", style = MaterialTheme.typography.titleMedium)
                        KhabirTextField(value = "مدني", onValueChange = {}, label = { Text("نوع الدعوى") })
                        KhabirTextField(value = "محكمة أسوان الابتدائية", onValueChange = {}, label = { Text("المحكمة") })
                    }
                }
            }
        }
    }

    @Test
    fun `case list — mirrors CaseListScreen and RegisterScreen result cards`() {
        val sampleCases = listOf(
            "دعوى 12 لسنة 2026" to "محكمة أسوان الابتدائية",
            "دعوى 45 لسنة 2026" to "محكمة الأقصر الجزئية",
            "دعوى 7 لسنة 2025" to "محكمة سوهاج الابتدائية"
        )
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sampleCases) { (title, court) ->
                            KhabirCard {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(court, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `empty results state`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("لا توجد قضايا", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    @Test
    fun `validation error text below a field`() {
        paparazzi.snapshot {
            KhabirTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KhabirTextField(value = "", onValueChange = {}, label = { Text("اسم الخبير") })
                        Text("أكمل: اسم الخبير، الصفة الوظيفية", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    @Test
    fun `layout renders correctly in RTL — critical for this Arabic app`() {
        paparazzi.snapshot {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                KhabirTheme {
                    Surface {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KhabirSectionHeader("بيانات القضية")
                            KhabirTextField(value = "دعوى 123 لسنة 2026", onValueChange = {}, label = { Text("رقم الدعوى") })
                            KhabirCard {
                                Text("محكمة أسوان الابتدائية")
                                Text("أحمد محمد علي — مدعى عليه", style = MaterialTheme.typography.bodySmall)
                            }
                            KhabirPrimaryButton(text = "حفظ", onClick = {})
                        }
                    }
                }
            }
        }
    }
}
