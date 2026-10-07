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
    private val pageCache = com.khabir.app.domain.model.ExtractionResultCache()
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
        detectMultipleDocuments: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result {
        val pages = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var localFallback = false
        var pagesRead = 0
        var aiError: String? = null
        val effectiveUseAi = shouldUseAiBatch(
            requestedUseAi = useAi,
            pageCount = pageFiles.size,
            canAnalyzeMultiplePages = aiVision.canAnalyzeMultiplePages()
        )
        if (useAi && !effectiveUseAi) {
            localFallback = true
            aiError = "لا يوجد مفتاح AI شخصي للتحليل متعدد الصفحات؛ تم استخدام OCR المحلي صفحة بصفحة."
            warnings += aiError
        }
        val batchSize = if (effectiveUseAi) PAGES_PER_REQUEST else 1
        pageFiles.chunked(batchSize).forEachIndexed batchLoop@ { batchIndex, files ->
            val firstPage = batchIndex * batchSize + 1
            // Local OCR is deliberately decoded and recycled one page at a time. Keeping ten
            // camera bitmaps alive together can exceed the heap on real phones and starve the
            // UI long enough for Android to report an ANR.
            if (!effectiveUseAi) {
                files.forEachIndexed { offset, file ->
                    val pageNumber = firstPage + offset
                    val localCacheKey = runCatching { com.khabir.app.domain.model.ExtractionResultCache.key(listOf(file.readBytes()),"local-ocr","","case-session") }.getOrNull()
                    val cached = localCacheKey?.let(pageCache::get)
                    if(cached != null) {
                        pages += "[الصفحة $pageNumber]\n$cached"; pagesRead++; onProgress(pagesRead,pageFiles.size)
                        return@forEachIndexed
                    }
                    val bitmap = decodeCameraBitmap(context, Uri.fromFile(file), MAX_ANALYSIS_EDGE)
                    if (bitmap == null) {
                        warnings += "تعذر قراءة الصفحة $pageNumber"
                    } else {
                        try {
                            when (val local = localOcr.recognize(bitmap)) {
                                is ArabicPetitionOcrService.Result.Success -> {
                                    pages += "[الصفحة $pageNumber]\n${local.text.trim()}"
                                    localCacheKey?.let { pageCache.put(it,local.text.trim()) }
                                    pagesRead++
                                }
                                is ArabicPetitionOcrService.Result.Failure -> warnings += "الصفحة $pageNumber: ${local.message}"
                            }
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                    onProgress(pagesRead,pageFiles.size)
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
            if (bitmaps.size != files.size) {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                return@batchLoop
            }
            if (bitmaps.isEmpty()) return@batchLoop
            try {
                val result = aiVision.analyzePages(bitmaps, purpose, detectMultipleDocuments)
                when (result) {
                    is GeminiDocumentVisionService.Result.Success -> {
                        pages += "[الصفحات $firstPage-${firstPage + bitmaps.size - 1}]\n${result.text.trim()}"
                        pagesRead += bitmaps.size
                        onProgress(pagesRead,pageFiles.size)
                    }
                    is GeminiDocumentVisionService.Result.Unavailable,
                    is GeminiDocumentVisionService.Result.Failure -> {
                        val failureMessage = when (result) {
                            is GeminiDocumentVisionService.Result.Unavailable -> result.message
                            is GeminiDocumentVisionService.Result.Failure -> result.message
                            else -> null
                        }
                        aiError = failureMessage ?: "تعذر تحليل الصور بالذكاء الاصطناعي"
                        if (fallbackToLocal) {
                            localFallback = true
                            // Release the AI batch before local OCR. Otherwise an API/key failure
                            // would keep up to ten decoded pages alive while Tesseract allocates
                            // its own working buffers.
                            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                            files.forEachIndexed { offset, file ->
                                val bitmap = decodeCameraBitmap(context, Uri.fromFile(file), MAX_ANALYSIS_EDGE)
                                if (bitmap == null) {
                                    warnings += "تعذر قراءة الصفحة ${firstPage + offset}"
                                } else {
                                    try {
                                        when (val local = localOcr.recognize(bitmap)) {
                                            is ArabicPetitionOcrService.Result.Success -> {
                                                pages += "[الصفحة ${firstPage + offset}]\n${local.text.trim()}"
                                                pagesRead++
                                            }
                                            is ArabicPetitionOcrService.Result.Failure ->
                                                warnings += "الصفحة ${firstPage + offset}: ${local.message}"
                                        }
                                    } finally {
                                        if (!bitmap.isRecycled) bitmap.recycle()
                                    }
                                }
                                yield()
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
        if (pagesRead != pageFiles.size) {
            warnings.add(0, "لم تكتمل قراءة جميع الصفحات ($pagesRead من ${pageFiles.size}). لم يتم اعتماد نص ناقص؛ الصور محفوظة لإعادة المحاولة.")
            return Result("", pagesRead, localFallback, warnings, aiError)
        }
        if (deleteAfterRead) pageFiles.forEach { it.delete() }
        return Result(pages.joinToString("\n\n"), pagesRead, localFallback, warnings, aiError)
    }

    private companion object {
        const val PAGES_PER_REQUEST = 10
        const val MAX_ANALYSIS_EDGE = 1800
    }
}

internal fun shouldUseAiBatch(
    requestedUseAi: Boolean,
    pageCount: Int,
    canAnalyzeMultiplePages: Boolean
): Boolean = requestedUseAi && (pageCount <= 1 || canAnalyzeMultiplePages)
