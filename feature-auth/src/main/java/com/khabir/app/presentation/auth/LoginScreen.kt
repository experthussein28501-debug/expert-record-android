package com.khabir.app.presentation.auth

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.khabir.app.data.auth.GoogleSession
import com.khabir.app.presentation.components.InlineHelp
import com.khabir.core.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val LoginGold = Color(0xFFD6B45A)

@Composable
fun LoginScreen(logoRes: Int, onGoogleSuccess: () -> Unit, onPreview: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(logoRes), "شعار سجل الخبير", Modifier.size(120.dp))
        Spacer(Modifier.height(12.dp))
        Text("سجل الخبير", color = LoginGold, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = {
                if (!isGoogleSignInConfigured(BuildConfig.GOOGLE_WEB_CLIENT_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_PROJECT_ID)) {
                    message = "تسجيل Google غير متاح في هذه النسخة بعد."
                } else scope.launch {
                    busy = true; message = null
                    try { signInWithGoogle(context); onGoogleSuccess() }
                    catch (_: GetCredentialCancellationException) { /* Account picker dismissed. */ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "تعذر تسجيل الدخول. تأكد من الاتصال بالإنترنت وحاول مرة أخرى." }
                    finally { busy = false }
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("تسجيل الدخول باستخدام Google", fontWeight = FontWeight.Medium)
        }
        message?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 16.dp)) }
        InlineHelp("تسجيل الدخول", "اختر حساب Google لحفظ حالة اشتراكك ومكافآتك. المستندات تبقى على جهازك، ولا يرفعها تسجيل الدخول.")
        onPreview?.let { action ->
            TextButton(onClick = action, enabled = !busy) { Text("فتح النسخة التجريبية", color = LoginGold) }
        }
    }
}

internal fun isGoogleSignInConfigured(googleWebClientId: String, firebaseApiKey: String, firebaseAppId: String, firebaseProjectId: String): Boolean =
    listOf(googleWebClientId, firebaseApiKey, firebaseAppId, firebaseProjectId).all { it.isNotBlank() }

private suspend fun signInWithGoogle(context: Context) {
    val auth = requireNotNull(GoogleSession.auth(context))
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
    val response = CredentialManager.create(context).getCredential(context,
        GetCredentialRequest.Builder().addCredentialOption(option).build())
    val credential = response.credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
}
