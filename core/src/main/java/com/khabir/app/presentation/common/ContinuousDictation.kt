package com.khabir.app.presentation.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Chains finite recognition sessions; Android providers may leave gaps between sessions. */
class ContinuousDictationController internal constructor(
    private val context: Context,
    private val onError: (String) -> Unit,
    private val recognitionAvailable: () -> Boolean = { SpeechRecognizer.isRecognitionAvailable(context) }
) {
    var transcript by mutableStateOf("")
        private set
    var isListening by mutableStateOf(false)
        private set
    var isFinishing by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var retryCount = 0
    private var committedText = ""
    private var partialText = ""
    private var finishCallback: ((String) -> Unit)? = null

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private fun publish() { transcript = listOf(committedText, partialText).filter { it.isNotBlank() }.joinToString(" ") }
    private fun commit(text: String = partialText) {
        committedText = listOf(committedText, text).filter { it.isNotBlank() }.joinToString(" ")
        partialText = ""
        publish()
    }
    private fun disposeRecognizer() {
        generation++ // Reject any callbacks queued by the old service.
        recognizer?.destroy()
        recognizer = null
    }
    private fun nextSession(delay: Long = 350L) {
        disposeRecognizer()
        handler.postDelayed({ if (isListening) beginSession() }, delay)
    }
    private fun beginSession() {
        val token = ++generation
        try {
            val created = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = created
            created.setRecognitionListener(object : RecognitionListener {
                private fun active() = token == generation && (isListening || isFinishing)
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partial: Bundle?) {
                    if (!active()) return
                    val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) { partialText = text; publish() }
                }
                override fun onResults(results: Bundle?) {
                    if (!active()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    commit(text.ifBlank { partialText })
                    retryCount = 0
                    if (isFinishing) completeFinish() else nextSession()
                }
                override fun onError(error: Int) {
                    if (!active()) return
                    commit() // Keep the latest partial if a final result was not delivered.
                    if (isFinishing) { completeFinish(); return }
                    when {
                        error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            retryCount = 0
                            nextSession()
                        }
                        error in setOf(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER)
                            && retryCount < 3 -> { retryCount++; nextSession(500L * retryCount) }
                        else -> fail(errorMessage(error))
                    }
                }
            })
            created.startListening(buildIntent())
        } catch (_: Exception) { fail("تعذر بدء الميكروفون؛ أعد المحاولة") }
    }
    private fun fail(message: String) {
        commit()
        isListening = false
        lastError = message
        handler.removeCallbacksAndMessages(null)
        disposeRecognizer()
        onError(message)
    }
    fun start(reset: Boolean = true) {
        if (isListening || isFinishing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("يلزم السماح بالميكروفون أولًا"); return
        }
        if (!recognitionAvailable()) {
            fail("خدمة التعرف على الصوت غير متاحة على هذا الجهاز"); return
        }
        handler.removeCallbacksAndMessages(null)
        if (reset) { committedText = ""; partialText = ""; publish() }
        lastError = null
        retryCount = 0
        isListening = true
        beginSession()
    }
    /** Wait for the provider's final result before returning text. */
    fun finish(onDone: (String) -> Unit) {
        if (isFinishing) return
        isListening = false
        isFinishing = true
        finishCallback = onDone
        handler.removeCallbacksAndMessages(null)
        if (recognizer == null) { completeFinish(); return }
        handler.postDelayed({ completeFinish() }, 3500L)
        try { recognizer?.stopListening() } catch (_: Exception) { completeFinish() }
    }
    private fun completeFinish() {
        if (!isFinishing) return
        commit()
        val callback = finishCallback
        finishCallback = null
        isFinishing = false
        handler.removeCallbacksAndMessages(null)
        disposeRecognizer()
        callback?.invoke(transcript)
    }
    fun stop(): String {
        commit()
        release()
        return transcript
    }
    fun release() {
        isListening = false
        isFinishing = false
        finishCallback = null
        handler.removeCallbacksAndMessages(null)
        disposeRecognizer()
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يلزم السماح بميكروفون التطبيق"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تعذر الاتصال بخدمة التعرف على الصوت"
        SpeechRecognizer.ERROR_AUDIO -> "تعذر الوصول إلى الميكروفون"
        else -> "تعذر الاستماع، حاول مرة أخرى"
    }
}

@Composable
fun rememberContinuousDictation(onError: (String) -> Unit): ContinuousDictationController {
    val context = LocalContext.current
    val controller = remember { ContinuousDictationController(context, onError) }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    return controller
}

@Composable
fun ContinuousDictationDialog(
    controller: ContinuousDictationController,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val context = LocalContext.current
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            controller.start()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    AlertDialog(
        onDismissRequest = { controller.stop(); onDismiss() },
        title = { Text(if (controller.isFinishing) "جارٍ إنهاء الإملاء…" else if (controller.isListening) "بيسمع... اتكلم بارتياح" else "تم الإيقاف") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("يجدد التطبيق جلسات الإملاء حتى تضغط «تم». قد توجد وقفة قصيرة بين الجلسات بحسب خدمة الصوت على الجهاز.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(controller.transcript.ifBlank { "…" })
                controller.lastError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Column {
                Button(enabled = !controller.isFinishing, onClick = { controller.finish(onDone) }) {
                    Text(if (controller.isFinishing) "انتظر…" else "تم")
                }
                if (!controller.isListening && !controller.isFinishing) {
                    TextButton(onClick = { controller.start(reset = false) }) { Text("إعادة المحاولة مع الاحتفاظ بالنص") }
                }
            }
        },
        dismissButton = { TextButton(onClick = { controller.stop(); onDismiss() }) { Text("إلغاء") } }
    )
}

