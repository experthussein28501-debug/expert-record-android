package com.khabir.app.data.ai

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class GeminiValidationTest {
    private class Reply(url: String, private val code: Int, private val body: String) : HttpURLConnection(URL(url)) {
        val sent = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
        override fun getOutputStream() = sent
    }
    private val listing = """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]}]}"""
    private val success = """{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}"""
    private fun service() = GeminiDocumentVisionService(PersonalAiKeyStore(RuntimeEnvironment.getApplication()))

    @Test fun listingSuccessDoesNotMeanGenerationSuccess() = runBlocking {
        val service = service()
        service.openGeminiConnection = { url -> Reply(url, if (url.contains(":generateContent")) 429 else 200,
            if (url.contains(":generateContent")) """{"error":{"message":"You exceeded your current quota, raw provider details"}}""" else listing) }
        val result = service.validatePersonalKey("test-key")
        assertTrue(result is GeminiDocumentVisionService.Result.Failure)
        val message = (result as GeminiDocumentVisionService.Result.Failure).message
        assertTrue(message.contains("429"))
        assertTrue(message.contains("تم تجاوز حد استخدام"))
        assertFalse(message.contains("raw provider details"))
    }
    @Test fun model404RefreshesAndTriesAnotherAdvertisedModelWithImage() = runBlocking {
        val service = service()
        val requests = mutableListOf<Reply>()
        service.openGeminiConnection = { url ->
            val reply = when {
                url.contains("flash-lite:generateContent") -> Reply(url, 404, """{"error":{"message":"model missing"}}""")
                url.contains("flash:generateContent") -> Reply(url, 200, success)
                else -> Reply(url, 200, listing)
            }
            requests += reply
            reply
        }
        val result = service.validatePersonalKey("test-key")
        assertTrue(result.toString(), result is GeminiDocumentVisionService.Result.Success)
        assertEquals(4, requests.size)
        val sentJson = org.json.JSONObject(requests.last().sent.toString("UTF-8"))
        val imagePart = sentJson.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(1)
        assertEquals("image/png", imagePart.getJSONObject("inline_data").getString("mime_type"))
        assertTrue(imagePart.getJSONObject("inline_data").getString("data").isNotBlank())
    }
    @Test fun modelScoped429TriesFlashAfterLite() = runBlocking {
        val service = service()
        val generated = mutableListOf<String>()
        service.openGeminiConnection = { url ->
            if (url.contains(":generateContent")) generated += url
            when {
                url.contains("flash-lite:generateContent") -> Reply(url, 429,
                    """{"error":{"details":[{"violations":[{"quotaDimensions":{"model":"gemini-2.5-flash-lite"}}]}]}}""")
                url.contains("flash:generateContent") -> Reply(url, 200, success)
                else -> Reply(url, 200, listing)
            }
        }
        val result = service.validatePersonalKey("test-key")
        assertTrue(result.toString(), result is GeminiDocumentVisionService.Result.Success)
        assertEquals(2, generated.size)
        assertTrue(generated.first().contains("flash-lite:"))
        assertTrue(generated.last().contains("flash:"))
    }

    @Test fun ambiguous429StopsWithoutRotatingModels() = runBlocking {
        val service = service()
        var generationCalls = 0
        service.openGeminiConnection = { url ->
            if (url.contains(":generateContent")) {
                generationCalls++
                Reply(url, 429, """{"error":{"message":"quota exceeded"}}""")
            } else Reply(url, 200, listing)
        }
        assertTrue(service.validatePersonalKey("test-key") is GeminiDocumentVisionService.Result.Failure)
        assertEquals(1, generationCalls)
    }

    @Test fun repeated404IsBoundedAndNeverReportsReady() = runBlocking {
        val service = service()
        var calls = 0
        service.openGeminiConnection = { url -> calls++; Reply(url,
            if (url.contains(":generateContent")) 404 else 200,
            if (url.contains(":generateContent")) """{"error":{"message":"not found"}}""" else listing) }
        assertTrue(service.validatePersonalKey("test-key") is GeminiDocumentVisionService.Result.Failure)
        assertTrue(calls <= 6)
    }
}
