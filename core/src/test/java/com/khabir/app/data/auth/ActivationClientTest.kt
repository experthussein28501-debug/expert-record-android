package com.khabir.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * اختبارات لمنطق تفسير أكواد استجابة خدمة التفعيل — كان بدون أي تغطية
 * اختبارية سابقًا رغم أنه المسار اللي المستخدم بيشوف رسالة الخطأ منه فعليًا.
 */
class ActivationClientTest {

    @Test
    fun `invalid or revoked code maps to Arabic message`() {
        assertEquals(
            "كود التفعيل غير صحيح أو تم إلغاؤه",
            ActivationClient.failureMessageForResponseCode(403)
        )
    }

    @Test
    fun `device seat limit reached maps to Arabic message`() {
        assertEquals(
            "الكود وصل لعدد الأجهزة المسموح؛ اطلب نقل التفعيل من الجهاز القديم",
            ActivationClient.failureMessageForResponseCode(409)
        )
    }

    @Test
    fun `rate limited maps to Arabic message`() {
        assertEquals(
            "محاولات كثيرة؛ انتظر عشر دقائق ثم أعد المحاولة",
            ActivationClient.failureMessageForResponseCode(429)
        )
    }

    @Test
    fun `unknown response codes fall back to a generic retry message`() {
        val genericMessage = "تعذر التفعيل حاليًا؛ حاول لاحقًا"
        assertEquals(genericMessage, ActivationClient.failureMessageForResponseCode(500))
        assertEquals(genericMessage, ActivationClient.failureMessageForResponseCode(400))
        assertEquals(genericMessage, ActivationClient.failureMessageForResponseCode(0))
    }
}
