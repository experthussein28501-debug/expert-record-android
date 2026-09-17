package com.khabir.app.data.ai

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

enum class AiProvider(val label: String) {
    GEMINI("Gemini"),
    OPENAI("ChatGPT / OpenAI"),
    ANTHROPIC("Claude"),
    GROQ("Groq");

    companion object {
        fun fromStored(value: String?): AiProvider = entries.firstOrNull { it.name == value } ?: GEMINI
    }
}

@Singleton
class PersonalAiKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE)
    fun read(): String = runCatching {
        val packed = Base64.decode(preferences.getString("personal_gemini_key", null) ?: return "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        cipher.doFinal(packed.copyOfRange(12, packed.size)).toString(StandardCharsets.UTF_8)
    }.getOrDefault("")
    fun write(value: String) {
        if (value.isBlank()) { clear(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val packed = cipher.iv + cipher.doFinal(value.trim().toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString("personal_gemini_key", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }
    fun readProvider(): AiProvider = AiProvider.fromStored(preferences.getString("personal_ai_provider", null))
    fun writeProvider(provider: AiProvider) = preferences.edit().putString("personal_ai_provider", provider.name).apply()
    fun clear() = preferences.edit().remove("personal_gemini_key").remove("personal_ai_provider").apply()
    fun readInstructions(): String = preferences.getString("personal_ai_instructions", null).orEmpty()
    /** Guidance text is not a credential, so it is stored separately from the encrypted API key. */
    fun writeInstructions(value: String) = preferences.edit()
        .putString("personal_ai_instructions", value.trim())
        .apply()
    fun readReportStyleMemory(): String {
        // Version 0.7.0 stored case text here. Never reuse it across cases.
        preferences.edit().remove("approved_report_style").apply()
        return preferences.getString("approved_style_rules_v2", null).orEmpty()
    }
    fun writeReportStyleMemory(value: String) = preferences.edit()
        .remove("approved_report_style")
        .putString("approved_style_rules_v2", value.trim())
        .apply()
    fun clearReportStyleMemory() = preferences.edit().remove("approved_report_style").remove("approved_style_rules_v2").apply()
    private fun reportTemplateStorageSuffix(templateId: String): String =
        Base64.encodeToString(
            templateId.ifBlank { "default" }.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    private fun reportTemplateUriKey(templateId: String) =
        "report_word_template_uri_" + reportTemplateStorageSuffix(templateId)

    private fun reportTemplateMappingKey(templateId: String) =
        "report_word_mapping_" + reportTemplateStorageSuffix(templateId)

    /** Legacy single-template accessors retained only for migration from 0.8.1 and older. */
    fun readReportTemplateUri(): String = preferences.getString("report_word_template_uri", null).orEmpty()
    fun writeReportTemplateUri(value: String) = preferences.edit().putString("report_word_template_uri", value).apply()
    fun clearReportTemplateUri() = preferences.edit().remove("report_word_template_uri").apply()
    fun writeReportTemplateMapping(value: Map<String, String>) = preferences.edit()
        .putString("report_word_mapping", org.json.JSONObject(value).toString()).apply()
    fun readReportTemplateMapping(): Map<String, String> = readMapping("report_word_mapping")

    fun readReportTemplateUri(templateId: String): String =
        preferences.getString(reportTemplateUriKey(templateId), null).orEmpty()

    fun readReportTemplateMapping(templateId: String): Map<String, String> =
        readMapping(reportTemplateMappingKey(templateId))

    fun writeReportTemplateUri(templateId: String, value: String) = preferences.edit()
        .putString(reportTemplateUriKey(templateId), value)
        .apply()

    fun writeReportTemplateMapping(templateId: String, value: Map<String, String>) = preferences.edit()
        .putString(reportTemplateMappingKey(templateId), org.json.JSONObject(value).toString())
        .apply()

    fun clearReportTemplate(templateId: String) = preferences.edit()
        .remove(reportTemplateUriKey(templateId))
        .remove(reportTemplateMappingKey(templateId))
        .apply()

    fun migrateLegacyReportTemplate(templateId: String) {
        if (readReportTemplateUri(templateId).isNotBlank()) return
        val legacyUri = readReportTemplateUri()
        if (legacyUri.isBlank()) return
        writeReportTemplateUri(templateId, legacyUri)
        writeReportTemplateMapping(templateId, readReportTemplateMapping())
        preferences.edit().remove("report_word_template_uri").remove("report_word_mapping").apply()
    }

    /**
     * قالب Word شخصي لمحاضر الأعمال — نفس فكرة قوالب التقارير الشخصية،
     * لكن بمفتاح تخزين مستقل خاص بمحاضر الأعمال (نوع واحد فقط، بدون
     * تعدد أنواع زي تقارير مدني/أسري/جنح، فمفيش حاجة لـtemplateId هنا).
     */
    fun readWorkMinutesTemplateUri(): String =
        preferences.getString("work_minutes_word_template_uri", null).orEmpty()

    fun writeWorkMinutesTemplateUri(value: String) =
        preferences.edit().putString("work_minutes_word_template_uri", value).apply()

    fun readWorkMinutesTemplateMapping(): Map<String, String> = readMapping("work_minutes_word_mapping")

    fun writeWorkMinutesTemplateMapping(value: Map<String, String>) = preferences.edit()
        .putString("work_minutes_word_mapping", org.json.JSONObject(value).toString())
        .apply()

    fun clearWorkMinutesTemplate() = preferences.edit()
        .remove("work_minutes_word_template_uri")
        .remove("work_minutes_word_mapping")
        .apply()

    private fun readMapping(key: String): Map<String, String> = runCatching {
        val json = org.json.JSONObject(preferences.getString(key, "{}").orEmpty())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("khabir_personal_ai_key", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder("khabir_personal_ai_key", 3)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build())
        }.generateKey()
    }
}
