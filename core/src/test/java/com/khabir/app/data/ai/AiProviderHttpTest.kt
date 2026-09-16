package com.khabir.app.data.ai

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AiProviderHttpTest {
    @Test
    fun provider_endpoints_and_models_stay_explicit() {
        assertEquals("https://api.anthropic.com/v1/messages", AiProviderHttp.chatEndpoint(AiProvider.ANTHROPIC))
        assertEquals("claude-sonnet-4-6", AiProviderHttp.textModel(AiProvider.ANTHROPIC))
        assertEquals("https://api.groq.com/openai/v1/chat/completions", AiProviderHttp.chatEndpoint(AiProvider.GROQ))
        assertEquals("qwen/qwen3.6-27b", AiProviderHttp.visionModel(AiProvider.GROQ))
        assertEquals(5, AiProviderHttp.maxVisionImages(AiProvider.GROQ))
    }

    @Test
    fun anthropic_parser_reads_text_blocks() {
        val json = JSONObject("{\"content\":[{\"type\":\"text\",\"text\":\"نص أول\"},{\"type\":\"text\",\"text\":\" ثم ثان\"}]}")
        assertEquals("نص أول ثم ثان", AiProviderHttp.parseText(AiProvider.ANTHROPIC, json))
    }

    @Test
    fun groq_vision_payload_uses_current_multimodal_model() {
        val payload = AiProviderHttp.visionPayload(AiProvider.GROQ, "اقرأ", listOf(byteArrayOf(1, 2, 3)))
        val json = JSONObject(payload)
        assertEquals("qwen/qwen3.6-27b", json.getString("model"))
        val content = json.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
    }
}
