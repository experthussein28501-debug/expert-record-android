package com.khabir.app.data.ocr

import android.content.Context
import android.net.Uri
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.LegalDocumentPurpose
import com.khabir.app.presentation.common.decodeCameraBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.yield

/**
 * Reads a camera session page by page. Page files remain on disk until processing begins,
 * so the same camera flow can safely accept a long document in every screen.
 */
@Singleton
class MultiPageDocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localOcr: ArabicPetitionOcrService,
    private val aiVision: GeminiDocumentVisionService
) {
    data class Result(
        val text: String,
        val pagesRead: Int,
        val usedLocalFallback: Boolean,
        val warnings: List<String>,
        val aiError: String? = null
    )

    suspend fun read(
        pageFiles: List<File>,
        purpose: LegalDocumentPurpose,
        useAi: Boolean,
        fallbackToLocal: Boolean = true,
        deleteAfterRead: Boolean = true,
        detectMultipleDocuments: Boolean = false
    ): Result {
        val pages = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var localFallback = false
        var pagesRead = 0
        var aiError: String? = null
        pageFiles.chunked(PAGES_PER_REQUEST).forEachIndexed batchLoop@ { batchIndex, files ->
            val firstPage = batchIndex * PAGES_PER_REQUEST + 1
            // Local OCR is deliberately decoded and recycled one page at a time. Keeping ten
            // camera bitmaps alive together can exceed the heap on real phones and starve the
            // UI long enough for Android to report an ANR.
            if (!useAi) {
                files.forEachIndexed { offset, file ->
                    val pageNumber = firstPage + offset
                    val bitmap = decodeCameraBitmap(context, Uri.fromFile(file), MAX_ANALYSIS_EDGE)
                    if (bitmap == null) {
                        warnings += "تعذر قراءة الصفحة $pageNumber"
                    } else {
                        try {
                            when (val local = localOcr.recognize(bitmap)) {
                                is ArabicPetitionOcrService.Result.Success -> {
                                    pages += "[الصفحة $pageNumber]\n${local.text.trim()}"
                                    pagesRead++
                                }
                                is ArabicPetitionOcrService.Result.Failure -> warnings += "الصفحة $pageNumber: ${local.message}"
                            }
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                    yield()
                }
                return@batchLoop
            }
            val bitmaps = files.mapIndexedNotNull { offset, file ->
                // Gemini still receives the whole ordered group, but at a bounded resolution.
                // Ten 3200px ARGB pages can consume hundreds of MB before encoding.
                decodeCameraBitmap(context, Uri.fromFile(file), MAX_ANALYSIS_EDGE)
                    ?: run { warnings += "تعذر قراءة الصفحة ${firstPage + offset}"; null }
            }
            if (bitmaps.isEmpty()) return@batchLoop
            try {
                val result = if (useAi) aiVision.analyzePages(bitmaps, purpose, detectMultipleDocuments) else null
                when (result) {
                    is GeminiDocumentVisionService.Result.Success -> {
                        pages += "[الصفحات $firstPage-${firstPage + bitmaps.size - 1}]\n${result.text.trim()}"
                        pagesRead += bitmaps.size
                    }
                    is GeminiDocumentVisionService.Result.Unavailable,
                    is GeminiDocumentVisionService.Result.Failure,
                    null -> {
                        val failureMessage = when (result) {
                            is GeminiDocumentVisionService.Result.Unavailable -> result.message
                            is GeminiDocumentVisionService.Result.Failure -> result.message
                            else -> null
                        }
                        if (useAi) aiError = failureMessage ?: "تعذر تحليل الصور بالذكاء الاصطناعي"
                        if (!useAi || fallbackToLocal) {
                            if (useAi) localFallback = true
                            bitmaps.forEachIndexed { offset, bitmap ->
                                when (val local = localOcr.recognize(bitmap)) {
                                    is ArabicPetitionOcrService.Result.Success -> {
                                        pages += "[الصفحة ${firstPage + offset}]\n${local.text.trim()}"
                                        pagesRead++
                                    }
                                    is ArabicPetitionOcrService.Result.Failure -> warnings += "الصفحة ${firstPage + offset}: ${local.message}"
                                }
                            }
                        } else {
                            warnings += aiError.orEmpty()
                        }
                    }
                }
            } finally {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
        if (deleteAfterRead) pageFiles.forEach { it.delete() }
        return Result(pages.joinToString("\n\n"), pagesRead, localFallback, warnings, aiError)
    }

    private companion object {
        const val PAGES_PER_REQUEST = 10
        const val MAX_ANALYSIS_EDGE = 1800
    }
}
