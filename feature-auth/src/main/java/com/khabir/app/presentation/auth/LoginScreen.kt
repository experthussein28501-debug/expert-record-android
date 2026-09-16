package com.khabir.app.presentation.auth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.khabir.core.BuildConfig
import com.khabir.app.data.auth.ActivationClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val LoginGold = Color(0xFFD6B45A)

@Composable
fun LoginScreen(
    onSkip: () -> Unit,
    onGoogleSuccess: () -> Unit,
    onActivationSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activationCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var activating by remember { mutableStateOf(false) }
    val activationClient = remember(context) { ActivationClient(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = LoginGold.copy(alpha = 0.14f)) {
            Icon(Icons.Filled.Balance, null, Modifier.padding(24.dp), tint = LoginGold)
        }
        Spacer(Modifier.height(18.dp))
        Text("سجل الخبير", color = LoginGold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("إدارة أعمال الخبرة القضائية", color = Color.White.copy(alpha = 0.78f))
        Spacer(Modifier.height(30.dp))

        Button(
            onClick = {
                busy = true
                message = null
                scope.launch {
                    busy = false
                    message = "تسجيل Google قريبًا؛ استخدم الدخول للتجربة"
                }
            },
            enabled = false,
            colors = ButtonDefaults.buttonColors(containerColor = LoginGold, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            else Icon(Icons.Filled.AccountCircle, null)
            Spacer(Modifier.width(8.dp))
            Text("تسجيل الدخول باستخدام Google — قريبًا")
        }

        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = activationCode,
            onValueChange = { activationCode = it; message = null },
            label = { Text("كود التفعيل") },
            singleLine = true,
            enabled = !activating,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = LoginGold,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                focusedLabelColor = LoginGold,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                activating = true
                val submittedCode = activationCode
                message = "جارٍ التحقق من كود التفعيل..."
                scope.launch {
                    try {
                        activationClient.activate(submittedCode).fold(
                            onSuccess = { activationCode = ""; onActivationSuccess() },
                            onFailure = { message = it.message ?: "تعذر التفعيل" }
                        )
                    } finally { activating = false }
                }
            },
            enabled = !busy && !activating && activationCode.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = LoginGold, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(if (activating) "جارٍ التفعيل..." else "تفعيل") }

        message?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onSkip, enabled = !busy && !activating, modifier = Modifier.fillMaxWidth()) {
            Text("تخطي والدخول للتجربة", color = LoginGold)
        }
    }
}

/**
 * هل بيانات Firebase/Google المطلوبة لتسجيل الدخول مكتملة — منطق صرف بلا
 * شبكة ولا Context، قابل للاختبار مباشرة.
 */
internal fun isGoogleSignInConfigured(
    googleWebClientId: String,
    firebaseApiKey: String,
    firebaseAppId: String,
    firebaseProjectId: String
): Boolean =
    googleWebClientId.isNotBlank() && firebaseApiKey.isNotBlank() &&
        firebaseAppId.isNotBlank() && firebaseProjectId.isNotBlank()

private suspend fun signInWithGoogle(context: Context): Result<Unit> = runCatching {
    require(
        isGoogleSignInConfigured(
            BuildConfig.GOOGLE_WEB_CLIENT_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_PROJECT_ID
        )
    ) {
        "تسجيل Google يحتاج إعداد Firebase الخاص بالمشروع؛ استخدم التخطي في نسخة التجربة"
    }
    val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
    ) ?: error("تعذر تشغيل Firebase")
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .build()
    val response = CredentialManager.create(context).getCredential(
        context,
        GetCredentialRequest.Builder().addCredentialOption(option).build()
    )
    val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
    val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
    FirebaseAuth.getInstance(app).signInWithCredential(firebaseCredential).await()
}
