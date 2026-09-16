package com.khabir.app.presentation.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * أول اختبارات مباشرة لـ feature-auth — بعد استخراج منطق التحقق من اكتمال
 * إعداد Firebase/Google لتسجيل الدخول (`isGoogleSignInConfigured`) لدالة
 * نقية بلا شبكة ولا Context، بنفس أسلوب باقي اختبارات المشروع.
 */
class LoginScreenTest {

    @Test
    fun `all four values present means configured`() {
        assertTrue(isGoogleSignInConfigured("web-client-id", "api-key", "app-id", "project-id"))
    }

    @Test
    fun `missing google web client id means not configured`() {
        assertFalse(isGoogleSignInConfigured("", "api-key", "app-id", "project-id"))
    }

    @Test
    fun `missing firebase api key means not configured`() {
        assertFalse(isGoogleSignInConfigured("web-client-id", "", "app-id", "project-id"))
    }

    @Test
    fun `missing firebase app id means not configured`() {
        assertFalse(isGoogleSignInConfigured("web-client-id", "api-key", "", "project-id"))
    }

    @Test
    fun `missing firebase project id means not configured`() {
        assertFalse(isGoogleSignInConfigured("web-client-id", "api-key", "app-id", ""))
    }

    @Test
    fun `blank-only whitespace values are treated as missing`() {
        assertFalse(isGoogleSignInConfigured("   ", "api-key", "app-id", "project-id"))
    }

    @Test
    fun `nothing set at all means not configured`() {
        assertFalse(isGoogleSignInConfigured("", "", "", ""))
    }
}
