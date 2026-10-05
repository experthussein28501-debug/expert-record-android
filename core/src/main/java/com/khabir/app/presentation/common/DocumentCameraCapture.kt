package com.khabir.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import java.io.File

/** One camera configuration for case intake, reports, notices and minutes. */
@Composable
fun DocumentCameraCapture(
    onDismiss: () -> Unit,
    onPagesSelected: (List<File>) -> Unit,
    onPagesAnalyze: (List<File>) -> Unit,
    onError: (String) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focus.clearFocus()
        keyboard?.hide()
    }
    InAppCameraCapture(
        onDismiss = onDismiss,
        onCaptured = {}, // Multi-page mode always transfers files, not full-resolution bitmaps.
        onPagesSelected = onPagesSelected,
        onPagesAnalyze = onPagesAnalyze,
        onError = onError
    )
}
