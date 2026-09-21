package com.khabir.app.monetization

import android.content.Context
import android.os.SystemClock
import com.khabir.app.BuildConfig
import com.khabir.app.data.auth.GoogleSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

data class Entitlements(
    val uid: String = "",
    val serverTime: Long = 0,
    val receivedElapsed: Long = 0,
    val adsStartAt: Long = Long.MAX_VALUE,
    val rewardUntil: Long = 0,
    val subscriptionUntil: Long = 0,
    val rewardCount: Int = 0,
    val adsEnabled: Boolean = false
) {
    fun now() = serverTime + (SystemClock.elapsedRealtime() - receivedElapsed).coerceAtLeast(0)
    fun fresh() = uid.isNotBlank() && SystemClock.elapsedRealtime() - receivedElapsed in 0..(15 * 60_000L)
    fun canAdvertise() = fresh() && adsEnabled && now() >= adsStartAt && now() >= rewardUntil && now() >= subscriptionUntil
}

class MonetizationApi(private val context: Context) {
    val configured get() = BuildConfig.MONETIZATION_BASE_URL.startsWith("https://")
    suspend fun call(path: String, body: JSONObject = JSONObject()): JSONObject {
        check(configured) { "خدمة الاشتراكات والمكافآت غير متاحة في هذه النسخة بعد" }
        val user = GoogleSession.auth(context)?.currentUser ?: error("سجل الدخول باستخدام Google أولًا")
        val token = user.getIdToken(false).await().token ?: error("تعذر التحقق من الحساب")
        return withContext(Dispatchers.IO) {
            val uri = URI(BuildConfig.MONETIZATION_BASE_URL.trimEnd('/') + "/" + path)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null)
            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; instanceFollowRedirects = false
                connectTimeout = 15_000; readTimeout = 30_000; doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                check(connection.responseCode in 200..299) { "تعذر التحقق من الخدمة الآن. حاول مرة أخرى." }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 64 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                JSONObject(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
    }
    suspend fun entitlements(): Entitlements {
        val uid = GoogleSession.auth(context)?.currentUser?.uid ?: return Entitlements()
        val json = call("entitlements")
        check(GoogleSession.auth(context)?.currentUser?.uid == uid)
        return Entitlements(uid, json.getLong("serverTime"), SystemClock.elapsedRealtime(), json.getLong("adsStartAt"),
            json.optLong("rewardUntil"), json.optLong("subscriptionUntil"), json.optInt("rewardCount"), json.optBoolean("adsEnabled"))
    }
}
