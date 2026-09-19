package com.khabir.app.presentation.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Full-screen camera owned by Khabir itself. External analysis opens only when
 * the user explicitly presses the Google Lens option.
 */
@Composable
fun InAppCameraCapture(
    onDismiss: () -> Unit,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onGeminiCaptured: ((android.graphics.Bitmap) -> Unit)? = null,
    onPagesCaptured: ((pages: List<File>, useAi: Boolean) -> Unit)? = null,
    onPagesSelected: ((pages: List<File>) -> Unit)? = null,
    onPagesAnalyze: ((pages: List<File>) -> Unit)? = null,
    lensOnly: Boolean = false,
    onError: (String) -> Unit
) {
    com.khabir.app.data.monetization.BlockWorkAds(true)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedPages by remember { mutableStateOf<List<File>>(emptyList()) }
    var pagesTransferred by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            captureExecutor.shutdown()
            if (!pagesTransferred) capturedPages.forEach { it.delete() }
        }
    }

    fun captureFromPreview() {
        val capture = imageCapture ?: return
        if (isCapturing) return
        val multiPageMode = onPagesCaptured != null || onPagesSelected != null
        if (multiPageMode && capturedPages.size >= MAX_DOCUMENT_PAGES) {
            onError("تم الوصول إلى الحد الأقصى: 10 صور")
            return
        }
        if (lensOnly) {
            captureForLens(context, capture, captureExecutor, mainExecutor, onError, onDismiss) { isCapturing = it }
        } else if (!multiPageMode) {
            captureWith(context, capture, captureExecutor, mainExecutor, onCaptured, onError) { isCapturing = it }
        } else {
            isCapturing = true
            captureToFile(context, capture, captureExecutor) { result ->
                mainExecutor.execute {
                    isCapturing = false
                    result.onSuccess { file -> capturedPages = capturedPages + file }
                        .onFailure { onError(it.message ?: "تعذر حفظ الصفحة") }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                            val gestures = GestureDetector(
                                previewContext,
                                object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onDown(e: MotionEvent): Boolean = true

                                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                        val camera = boundCamera ?: return true
                                        val point = meteringPointFactory.createPoint(e.x, e.y)
                                        val action = FocusMeteringAction.Builder(
                                            point,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                        )
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                            .build()
                                        camera.cameraControl.startFocusAndMetering(action)
                                        return true
                                    }

                                    override fun onDoubleTap(e: MotionEvent): Boolean {
                                        captureFromPreview()
                                        return true
                                    }
                                }
                            )
                            setOnTouchListener { _, event -> gestures.onTouchEvent(event) }

                            val providerFuture = ProcessCameraProvider.getInstance(previewContext)
                            providerFuture.addListener({
                                runCatching {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                        .build()
                                    provider.unbindAll()
                                    boundCamera = provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture
                                    )
                                    imageCapture = capture
                                }.onFailure { error ->
                                    onError(error.message ?: "تعذر تشغيل كاميرا التطبيق")
                                    onDismiss()
                                }
                            }, mainExecutor)
                        }
                    }
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق الكاميرا", tint = Color.White)
                }

                Text(
                    when {
                        lensOnly -> "لمسة واحدة للتركيز — ضغطتان لالتقاط الصورة وإرسالها إلى Google Lens"
                        onPagesCaptured != null || onPagesSelected != null -> "لمسة واحدة للتركيز — ضغطتان لالتقاط صفحة (حتى 10 صور)"
                        else -> "لمسة واحدة للتركيز — ضغطتان لالتقاط الصورة"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).background(Color.Black.copy(alpha = 0.45f)).padding(8.dp)
                )

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (onPagesCaptured != null || onPagesSelected != null) {
                        Text(
                            "عدد الصور: ${capturedPages.size} / $MAX_DOCUMENT_PAGES — أضف الصفحات بالترتيب",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "الحد الأقصى للجلسة 10 صور؛ قد تُقسم إلى أكثر من مستند بعد الفحص.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (capturedPages.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    capturedPages.last().delete()
                                    capturedPages = capturedPages.dropLast(1)
                                },
                                enabled = !isCapturing
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                                Text("حذف آخر صورة")
                            }
                        }
                    }
                    if (!lensOnly) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val capture = imageCapture ?: return@FilledTonalButton
                                if (isCapturing) return@FilledTonalButton
                                captureForLens(context, capture, captureExecutor, mainExecutor, onError, onDismiss) { isCapturing = it }
                            },
                            enabled = imageCapture != null && !isCapturing
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                            Text("فتح Google Lens")
                        }
                        if (onGeminiCaptured != null && onPagesCaptured == null && onPagesSelected == null) {
                            FilledTonalButton(
                                onClick = {
                                    val capture = imageCapture ?: return@FilledTonalButton
                                    if (isCapturing) return@FilledTonalButton
                                    captureWith(context, capture, captureExecutor, mainExecutor, onGeminiCaptured, onError) { isCapturing = it }
                                },
                                enabled = imageCapture != null && !isCapturing
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                                Text("AI Vision")
                            }
                        }
                    }
                        FilledIconButton(
                        onClick = { captureFromPreview() },
                        enabled = imageCapture != null && !isCapturing && (onPagesCaptured == null && onPagesSelected == null || capturedPages.size < MAX_DOCUMENT_PAGES)
                    ) {
                        if (isCapturing) CircularProgressIndicator(color = Color.White)
                        else Icon(Icons.Filled.CameraAlt, contentDescription = "التقاط صفحة")
                    }
                    if (onPagesSelected != null && capturedPages.isNotEmpty()) {
                        if (onPagesAnalyze != null) {
                            FilledTonalButton(
                                onClick = { pagesTransferred = true; onPagesAnalyze(capturedPages) },
                                enabled = !isCapturing
                            ) { Text("AI Vision — تحليل ${capturedPages.size} صورة") }
                        }
                        FilledTonalButton(
                            onClick = { pagesTransferred = true; onPagesSelected(capturedPages) },
                            enabled = !isCapturing
                        ) { Text("إضافة الصور للمراجعة (${capturedPages.size} من 10)") }
                    } else if (onPagesCaptured != null && capturedPages.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (onGeminiCaptured != null) {
                                FilledTonalButton(
                                    onClick = { pagesTransferred = true; onPagesCaptured(capturedPages, true) },
                                    enabled = !isCapturing
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                                    Text("إنهاء وتحليل بـ AI Vision")
                                }
                            }
                            FilledTonalButton(
                                onClick = { pagesTransferred = true; onPagesCaptured(capturedPages, false) },
                                enabled = !isCapturing
                            ) { Text("استخراج محلي عند الحاجة") }
                        }
                    }
                    if ((onPagesCaptured != null || onPagesSelected != null) && capturedPages.size >= MAX_DOCUMENT_PAGES) {
                        Text(
                            "تم الوصول إلى الحد الأقصى: 10 صور. احذف آخر صورة إذا أردت استبدالها ثم أنهِ الفحص.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_DOCUMENT_PAGES = 10


private fun captureWith(
    context: Context,
    imageCapture: ImageCapture,
    captureExecutor: ExecutorService,
    mainExecutor: java.util.concurrent.Executor,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onError: (String) -> Unit,
    onCapturingChanged: (Boolean) -> Unit
) {
    onCapturingChanged(true)
    captureToBitmap(context, imageCapture, captureExecutor) { result ->
        mainExecutor.execute {
            onCapturingChanged(false)
            result.onSuccess(onCaptured).onFailure {
                onError(it.message ?: "تعذر حفظ الصورة")
            }
        }
    }
}

private fun captureForLens(
    context: Context,
    imageCapture: ImageCapture,
    captureExecutor: ExecutorService,
    mainExecutor: java.util.concurrent.Executor,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
    onCapturingChanged: (Boolean) -> Unit
) {
    onCapturingChanged(true)
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    directory.listFiles()?.filter { it.name.startsWith("khabir_lens_") }?.forEach { it.delete() }
    val file = File.createTempFile("khabir_lens_", ".jpg", directory)
    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        captureExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                mainExecutor.execute {
                    onCapturingChanged(false)
                    if (openGoogleLens(context, uri)) onDismiss()
                    else {
                        file.delete()
                        onError("Google Lens غير متاح أو لا يقبل الصورة على هذا الجهاز")
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                mainExecutor.execute {
                    onCapturingChanged(false)
                    onError(exception.message ?: "تعذر التقاط الصورة لـ Google Lens")
                }
            }
        }
    )
}

/** Sends the freshly captured document to Lens instead of merely opening its home screen. */
private fun openGoogleLens(context: Context, imageUri: Uri): Boolean {
    val candidates = listOf("com.google.ar.lens", "com.google.android.googlequicksearchbox").map { packageName ->
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            setPackage(packageName)
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = android.content.ClipData.newRawUri("صورة المستند", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return candidates.any { intent ->
        runCatching {
            context.startActivity(intent)
        }.isSuccess
    }
}

private fun captureToBitmap(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onResult: (Result<android.graphics.Bitmap>) -> Unit
) {
    captureToFile(context, imageCapture, executor) { saved ->
        saved.fold(
            onSuccess = { file ->
                val bitmap = decodeCameraBitmap(context, Uri.fromFile(file))
                file.delete()
                if (bitmap == null) onResult(Result.failure(IllegalStateException("لم يتم حفظ صورة واضحة")))
                else onResult(Result.success(bitmap))
            },
            onFailure = { onResult(Result.failure(it)) }
        )
    }
}

/** Stores session pages on disk so a long document does not exhaust the app's memory. */
private fun captureToFile(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onResult: (Result<File>) -> Unit
) {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("khabir_pages_", ".jpg", directory)
    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onResult(Result.success(file))
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onResult(Result.failure(exception))
            }
        }
    )
}
