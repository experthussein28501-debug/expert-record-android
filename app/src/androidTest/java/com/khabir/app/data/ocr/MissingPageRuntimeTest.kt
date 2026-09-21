package com.khabir.app.data.ocr

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khabir.app.data.ai.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MissingPageRuntimeTest {
    @Test fun failedSecondPageKeepsBothOriginalsAndRejectsPartialText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val good = File(context.cacheDir, "regression-page-1.png")
        val broken = File(context.cacheDir, "regression-page-2.png")
        val bitmap = Bitmap.createBitmap(1800, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 90f; textAlign = Paint.Align.RIGHT }
            canvas.drawText("المحكمة مكتب الخبراء", 1680f, 200f, paint)
            good.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            broken.writeText("invalid camera file")
            val reader = MultiPageDocumentReader(context, ArabicPetitionOcrService(context), GeminiDocumentVisionService(PersonalAiKeyStore(context)))
            val result = reader.read(listOf(good, broken), LegalDocumentPurpose.REPORT, useAi = false)
            assertEquals(1, result.pagesRead)
            assertTrue(result.text.isEmpty())
            assertTrue(result.warnings.any { it.contains("الصفحة 2") })
            assertTrue(good.exists()); assertTrue(broken.exists())
        } finally { bitmap.recycle(); good.delete(); broken.delete() }
    }
}
