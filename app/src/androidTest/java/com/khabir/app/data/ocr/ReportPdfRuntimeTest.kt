package com.khabir.app.data.ocr

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khabir.app.data.export.LegalReportPdfBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportPdfRuntimeTest {
    @Test fun longArabicReportPaginatesAndRendersEveryPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = LegalReportPdfBuilder().buildToFile(File(context.cacheDir, "report-regression.pdf"), "تقرير تجريبي",
            listOf(com.khabir.app.domain.model.ReportTextFormat.KEY to com.khabir.app.domain.model.ReportTextFormat(size = 16).encode(), "رأس التقرير" to "تقرير الدعوى رقم ١٠٠ لسنة ٢٠٢٦", "الوزارة" to "وزارة العدل", "الإدارة" to "إدارة خبراء أسوان", "رقم الدعوى" to "100 لسنة 2026", "المحكمة" to "مدني جزئي كوم أمبو", "المرفوعة من" to "اسم أول تجريبي", "ضد" to "اسم ثان تجريبي"),
            listOf("بحث المستندات" to (1..55).joinToString("\n") { "المستند رقم $it: تمت مطابقة بيانات المستند مع الأوراق المقدمة وتسجيل الملاحظات دون تغيير مضمون المستند. هذه بيانات اختبار لتدقيق التفاف السطور العربية واستمرارها بين الصفحات." }, "النتيجة النهائية" to "نهاية التقرير التجريبي"))
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount >= 3)
            repeat(renderer.pageCount) { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "report-page-${index + 1}.png")
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/expert-record-ui")
                        }
                        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
            }
        }
        file.delete()
    }
}
