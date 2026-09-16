package com.khabir.app.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArabicOcrRuntimeTest {
    @Test fun readsBundledArabicAndRepairsStaleModelOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stale = File(context.filesDir, "tesseract/tessdata/ara.traineddata")
        stale.parentFile!!.mkdirs()
        stale.writeBytes(ByteArray(600_000))
        val bitmap = Bitmap.createBitmap(1800, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 90f; textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("المحكمة مكتب الخبراء", 1680f, 200f, paint)
            canvas.drawText("المدعي أحمد محمد", 1680f, 360f, paint)
            val result = ArabicPetitionOcrService(context).recognize(bitmap)
            assertTrue("OCR failed: $result", result is ArabicPetitionOcrService.Result.Success)
            val text = (result as ArabicPetitionOcrService.Result.Success).text
            assertTrue("Expected Arabic words, received: $text", listOf("المحكمة", "الخبراء", "المدعي", "محمد").count { text.contains(it) } >= 2)
            assertEquals(1_432_056L, stale.length())
        } finally { bitmap.recycle() }
    }
}
