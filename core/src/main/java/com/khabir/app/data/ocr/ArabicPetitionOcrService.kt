package com.khabir.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArabicPetitionOcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class Result {
        data class Success(val text: String) : Result()
        data class Failure(val message: String) : Result()
    }

    private val mutex = Mutex()

    suspend fun recognize(bitmap: Bitmap): Result = mutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            val dataRoot = File(context.filesDir, "tesseract")
            val tessData = File(dataRoot, "tessdata")
            val model = File(tessData, MODEL_FILE)
            if (!model.exists() || modelHash(model) != MODEL_SHA256) {
                tessData.mkdirs()
                installBundledArabicModel(model)
            }

            val api = TessBaseAPI()
            try {
                if (!api.init(dataRoot.absolutePath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
                    return@withContext Result.Failure("تعذر تهيئة محرك OCR العربي المحلي")
                }
                api.setVariable("preserve_interword_spaces", "1")
                fun readPrepared(binary: Boolean, mode: Int): String {
                    val prepared = prepareForOcr(bitmap, binary)
                    return try {
                        api.pageSegMode = mode
                        api.setImage(prepared)
                        api.utF8Text.orEmpty().trim()
                    } finally {
                        api.clear()
                        prepared.recycle()
                    }
                }
                val grayscaleText = readPrepared(false, TessBaseAPI.PageSegMode.PSM_AUTO)
                val binaryText = readPrepared(true, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)

                val text = listOf(grayscaleText, binaryText).maxByOrNull(::arabicTextScore).orEmpty()
                if (text.isBlank()) Result.Failure("لم يتم العثور على نص واضح في الصورة")
                else Result.Success(text)
            } finally {
                api.recycle()
            }
        }.getOrElse { e ->
            Result.Failure(e.message ?: "تعذر قراءة العريضة من الصورة")
        }
    } }

    private fun modelHash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installBundledArabicModel(destination: File) {
        val temp = File(destination.parentFile, "${destination.name}.install")
        try {
            context.assets.open("tessdata/$MODEL_FILE").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            require(modelHash(temp) == MODEL_SHA256) { "نموذج OCR العربي داخل التطبيق غير مكتمل؛ أعد تثبيت النسخة الصحيحة" }
            temp.copyTo(destination, overwrite = true)
        } finally {
            temp.delete()
        }
    }

    private fun prepareForOcr(source: Bitmap, binary: Boolean): Bitmap {
        val longest = maxOf(source.width, source.height)
        val targetLongest = longest.coerceIn(1800, 2600)
        val scale = targetLongest.toFloat() / longest.toFloat()
        val scaled = if (scale in 0.95f..1.05f) source else Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )

        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val gray = (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100
            histogram[gray]++
        }
        val threshold = otsuThreshold(histogram, pixels.size).coerceIn(105, 220)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val gray = (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100
            val corrected = ((gray - 128) * 1.18f + 128).toInt().coerceIn(0, 255)
            pixels[index] = if (binary) {
                if (corrected > threshold) Color.WHITE else Color.BLACK
            } else {
                Color.rgb(corrected, corrected, corrected)
            }
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()
        return result
    }

    private fun arabicTextScore(value: String): Int {
        val arabicLetters = value.count { it in '\u0600'..'\u06FF' }
        val digits = value.count(Char::isDigit)
        val lines = value.lineSequence().count { it.isNotBlank() }
        return arabicLetters * 4 + digits * 2 + lines * 3 + value.length.coerceAtMost(2_000)
    }

    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var weightedSum = 0L
        histogram.forEachIndexed { value, count -> weightedSum += value.toLong() * count }
        var backgroundWeight = 0
        var backgroundSum = 0L
        var bestVariance = -1.0
        var bestThreshold = 160
        for (value in histogram.indices) {
            backgroundWeight += histogram[value]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundSum += value.toLong() * histogram[value]
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (weightedSum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight *
                (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = value
            }
        }
        return bestThreshold
    }

    companion object {
        private const val LANGUAGE = "ara"
        private const val MODEL_FILE = "ara.traineddata"
        private const val MODEL_SHA256 = "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"
    }
}
