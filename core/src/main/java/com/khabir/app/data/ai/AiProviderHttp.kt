package com.khabir.app.data.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection

internal object AiProviderHttp {
    fun modelsEndpoint(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> "https://api.openai.com/v1/models"
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1/models"
        AiProvider.GROQ -> "https://api.groq.com/openai/v1/models"
        AiProvider.GEMINI -> error("Gemini model discovery uses its dynamic resolver")
    }

    fun chatEndpoint(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
        AiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
        AiProvider.GEMINI -> error("Gemini generation uses its dynamic resolver")
    }

    fun textModel(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> "gpt-4.1-mini"
        AiProvider.ANTHROPIC -> "claude-sonnet-4-6"
        AiProvider.GROQ -> "llama-3.3-70b-versatile"
        AiProvider.GEMINI -> error("Gemini chooses a compatible model dynamically")
    }

    fun visionModel(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> "gpt-4.1-mini"
        AiProvider.ANTHROPIC -> "claude-sonnet-4-6"
        AiProvider.GROQ -> "qwen/qwen3.6-27b"
        AiProvider.GEMINI -> error("Gemini chooses a compatible model dynamically")
    }

    fun maxVisionImages(provider: AiProvider): Int = when (provider) {
        AiProvider.GROQ -> 5
        else -> 10
    }

    fun audioEndpoint(provider: AiProvider): String? = when (provider) {
        AiProvider.OPENAI -> "https://api.openai.com/v1/audio/transcriptions"
        AiProvider.GROQ -> "https://api.groq.com/openai/v1/audio/transcriptions"
        AiProvider.GEMINI, AiProvider.ANTHROPIC -> null
    }

    fun audioModel(provider: AiProvider): String? = when (provider) {
        AiProvider.OPENAI -> "whisper-1"
        AiProvider.GROQ -> "whisper-large-v3-turbo"
        AiProvider.GEMINI, AiProvider.ANTHROPIC -> null
    }

    fun configureAuth(connection: HttpURLConnection, key: String, provider: AiProvider) {
        when (provider) {
            AiProvider.ANTHROPIC -> {
                connection.setRequestProperty("x-api-key", key)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            }
            AiProvider.OPENAI, AiProvider.GROQ ->
                connection.setRequestProperty("Authorization", "Bearer $key")
            AiProvider.GEMINI ->
                connection.setRequestProperty("x-goog-api-key", key)
        }
    }

    fun textPayload(provider: AiProvider, prompt: String): String = when (provider) {
        AiProvider.ANTHROPIC -> JSONObject()
            .put("model", textModel(provider))
            .put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()

        AiProvider.OPENAI, AiProvider.GROQ -> JSONObject()
            .put("model", textModel(provider))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()

        AiProvider.GEMINI -> error("Use Gemini request format")
    }

    fun visionPayload(provider: AiProvider, prompt: String, images: List<ByteArray>): String = when (provider) {
        AiProvider.ANTHROPIC -> {
            val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
            images.forEach { bytes ->
                content.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        )
                )
            }
            JSONObject()
                .put("model", visionModel(provider))
                .put("max_tokens", 4096)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .toString()
        }

        AiProvider.OPENAI, AiProvider.GROQ -> {
            val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
            images.forEach { bytes ->
                content.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put(
                                "url",
                                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                            )
                        )
                )
            }
            JSONObject()
                .put("model", visionModel(provider))
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .toString()
        }

        AiProvider.GEMINI -> error("Use Gemini request format")
    }

    fun parseText(provider: AiProvider, json: JSONObject): String = when (provider) {
        AiProvider.ANTHROPIC -> {
            val content = json.optJSONArray("content")
            buildString {
                if (content != null) {
                    for (index in 0 until content.length()) {
                        val block = content.optJSONObject(index) ?: continue
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }
            }.trim()
        }

        AiProvider.OPENAI, AiProvider.GROQ ->
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()

        AiProvider.GEMINI -> error("Use Gemini response parser")
    }
}
