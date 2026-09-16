package com.khabir.app.data.auth

import android.content.Context
import com.khabir.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class ActivationClient(private val context: Context) {

    companion object {
        /** رسالة الخطأ العربية المقابلة لكود استجابة خدمة التفعيل — منطق صرف بلا شبكة، قابل للاختبار مباشرة. */
        fun failureMessageForResponseCode(code: Int): String = when (code) {
            403 -> "كود التفعيل غير صحيح أو تم إلغاؤه"
            409 -> "الكود وصل لعدد الأجهزة المسموح؛ اطلب نقل التفعيل من الجهاز القديم"
            429 -> "محاولات كثيرة؛ انتظر عشر دقائق ثم أعد المحاولة"
            else -> "تعذر التفعيل حاليًا؛ حاول لاحقًا"
        }
    }

    fun installationId(): String {
        val preferences = context.getSharedPreferences("activation_device", Context.MODE_PRIVATE)
        return preferences.getString("id", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("id", it).commit()) { "تعذر حفظ معرف الجهاز" }
        }
    }

    suspend fun activate(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = BuildConfig.ACTIVATION_ENDPOINT.trim()
            require(endpoint.startsWith("https://")) { "خدمة التفعيل لم تُربط بعد؛ التخطي متاح للتجربة" }
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                val payload = JSONObject().put("code", code.trim()).put("device", installationId()).toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                when (connection.responseCode) {
                    200 -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        check(JSONObject(body).optString("status") == "activated") { "استجابة تفعيل غير صحيحة" }
                    }
                    else -> error(failureMessageForResponseCode(connection.responseCode))
                }
            } catch (error: SocketTimeoutException) {
                error("انتهت مهلة التفعيل؛ يمكنك إعادة المحاولة دون استهلاك مكان جهاز إضافي")
            } catch (error: UnknownHostException) {
                error("لا يوجد اتصال بالإنترنت أو تعذر الوصول لخدمة التفعيل")
            } finally {
                connection.disconnect()
            }
        }
    }
}
