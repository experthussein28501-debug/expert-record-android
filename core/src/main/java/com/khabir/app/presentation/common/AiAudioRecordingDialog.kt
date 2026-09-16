package com.khabir.app.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khabir.app.data.ai.GeminiDocumentVisionService
import java.io.File
import kotlinx.coroutines.launch

internal fun mergeAiTranscripts(parts: List<String>): String =
    parts.map(String::trim).filter(String::isNotBlank).joinToString("\n")

@Composable
fun AiAudioRecordingDialog(
    transcribe: suspend (File) -> GeminiDocumentVisionService.Result,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val segments = remember { mutableStateListOf<File>() }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var busy by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    var continuousCapture by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf("سجّل براحتك. التسجيل الطويل يُقسَّم تلقائيًا إلى أجزاء آمنة ثم يدمجها AI في نص واحد. لن يُرسل شيء قبل موافقتك.")
    }

    fun deleteSegments() {
        currentFile?.delete()
        currentFile = null
        segments.forEach { it.delete() }
        segments.clear()
    }

    lateinit var startSegment: (Boolean) -> Unit

    fun stopCurrent(autoContinue: Boolean = false) {
        val active = recorder ?: return
        recorder = null
        val file = currentFile
        currentFile = null
        val saved = runCatching {
            active.stop()
            file != null && file.length() > 0
        }.getOrDefault(false)
        active.release()

        if (saved && file != null) {
            segments += file
        } else {
            file?.delete()
        }

        if (autoContinue && continuousCapture && !disposed) {
            message = "تم حفظ الجزء ${segments.size}، ويستمر التسجيل في جزء جديد تلقائيًا…"
            startSegment(false)
        } else {
            continuousCapture = false
            message = if (segments.isNotEmpty()) {
                "تم التسجيل في ${segments.size} جزء. موافقتك سترسل الأجزاء بالترتيب إلى مزود AI ثم تجمع النتيجة في نص واحد."
            } else {
                "التسجيل قصير جدًا أو تعذر حفظه؛ أعد المحاولة"
            }
        }
    }

    startSegment = { clearExisting ->
        if (clearExisting) deleteSegments()
        val file = File.createTempFile("report_voice_${segments.size + 1}_", ".m4a", context.cacheDir)
        currentFile = file
        @Suppress("DEPRECATION")
        val active = MediaRecorder()
        runCatching {
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(64_000)
            active.setAudioSamplingRate(44_100)
            active.setOutputFile(file.absolutePath)
            active.setMaxDuration(540_000)
            active.setMaxFileSize(8_500_000)
            active.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    stopCurrent(autoContinue = true)
                }
            }
            active.prepare()
            active.start()
            recorder = active
            continuousCapture = true
            message = if (segments.isEmpty()) {
                "جارٍ التسجيل… اضغط إيقاف عند الانتهاء. لو طال التسجيل سيُقسَّم تلقائيًا."
            } else {
                "جارٍ تسجيل الجزء ${segments.size + 1}…"
            }
        }.onFailure {
            continuousCapture = false
            currentFile = null
            file.delete()
            active.release()
            message = "تعذر بدء التسجيل؛ تأكد من إذن الميكروفون وعدم استخدامه في تطبيق آخر"
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSegment(true) else message = "يلزم السماح بالميكروفون لبدء التسجيل"
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                continuousCapture = false
                stopCurrent()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            disposed = true
            continuousCapture = false
            owner.lifecycle.removeObserver(observer)
            recorder?.let { active ->
                runCatching { active.stop() }
                active.release()
            }
            recorder = null
            if (!busy) deleteSegments()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                continuousCapture = false
                if (recorder != null) stopCurrent()
                deleteSegments()
                onDismiss()
            }
        },
        title = { Text("تسجيل صوت AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                if (segments.isNotEmpty()) {
                    Text("الأجزاء المحفوظة: ${segments.size}", style = MaterialTheme.typography.bodySmall)
                }
                if (busy) CircularProgressIndicator()
                Button(
                    enabled = !busy,
                    onClick = {
                        if (recorder != null) {
                            continuousCapture = false
                            stopCurrent()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startSegment(true)
                        } else {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            recorder != null -> "إيقاف"
                            segments.isNotEmpty() -> "إعادة التسجيل من البداية"
                            else -> "بدء التسجيل"
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = segments.isNotEmpty() && recorder == null && !busy,
                onClick = {
                    busy = true
                    message = "جارٍ تفريغ ${segments.size} جزء بالترتيب…"
                    val files = segments.toList()
                    scope.launch {
                        try {
                            val transcripts = mutableListOf<String>()
                            for ((index, file) in files.withIndex()) {
                                message = "جارٍ تفريغ الجزء ${index + 1} من ${files.size}…"
                                when (val result = transcribe(file)) {
                                    is GeminiDocumentVisionService.Result.Success -> transcripts += result.text
                                    is GeminiDocumentVisionService.Result.Failure -> {
                                        message = "تعذر تفريغ الجزء ${index + 1}: ${result.message}"
                                        return@launch
                                    }
                                    is GeminiDocumentVisionService.Result.Unavailable -> {
                                        message = result.message
                                        return@launch
                                    }
                                }
                            }
                            val merged = mergeAiTranscripts(transcripts)
                            if (merged.isBlank()) {
                                message = "لم ينتج نص واضح من التسجيل"
                            } else {
                                files.forEach { it.delete() }
                                segments.clear()
                                onDone(merged)
                            }
                        } finally {
                            busy = false
                            if (disposed) deleteSegments()
                        }
                    }
                }
            ) { Text("موافقة وإرسال الصوت") }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    continuousCapture = false
                    if (recorder != null) stopCurrent()
                    deleteSegments()
                    onDismiss()
                }
            ) { Text("إلغاء") }
        }
    )
}
