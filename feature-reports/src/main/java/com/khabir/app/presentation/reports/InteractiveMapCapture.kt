package com.khabir.app.presentation.reports

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationManager
import java.net.URLEncoder
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

internal enum class ReportMapProvider { GOOGLE, OPEN_STREET_MAP }

internal fun reportMapUrl(query: String, provider: ReportMapProvider = ReportMapProvider.GOOGLE): String {
    val target = query.ifBlank { "أسوان" }
    val encoded = URLEncoder.encode(target, Charsets.UTF_8.name()).replace("+", "%20")
    return when (provider) {
        ReportMapProvider.GOOGLE ->
            "https://www.google.com/maps/search/?api=1&query=$encoded"
        ReportMapProvider.OPEN_STREET_MAP ->
            "https://www.openstreetmap.org/search?query=$encoded"
    }
}

@SuppressLint("MissingPermission")
private fun bestLastKnownLocation(context: Context): Location? {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

/**
 * Interactive map kept inside the report flow. Google Maps is the default,
 * with OpenStreetMap retained as an explicit fallback when Google web maps are
 * unavailable. The visible viewport is captured and then annotated in the
 * report sketch editor, so the result stays under the inspection section.
 */
@Composable
fun InteractiveMapCapture(
    initialQuery: String,
    onDismiss: () -> Unit,
    onCapture: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var provider by remember { mutableStateOf(ReportMapProvider.GOOGLE) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var locationMessage by remember { mutableStateOf("") }

    fun reload() {
        webView?.loadUrl(reportMapUrl(query, provider))
    }

    fun useLastKnownLocation() {
        val location = bestLastKnownLocation(context)
        if (location == null) {
            locationMessage = "تعذر تحديد موقع محفوظ حاليًا. فعّل الموقع ثم حاول مرة أخرى أو ابحث بالعنوان."
            return
        }
        provider = ReportMapProvider.GOOGLE
        query = "${location.latitude},${location.longitude}"
        locationMessage = "تم تحديد الموقع التقريبي من الجهاز. حرّك الخريطة لضبط موضع المعاينة."
        webView?.loadUrl(reportMapUrl(query, ReportMapProvider.GOOGLE))
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            useLastKnownLocation()
        } else {
            locationMessage = "لم يتم السماح بالموقع. يمكنك البحث عن مكان المعاينة يدويًا."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خريطة المعاينة داخل التطبيق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = provider == ReportMapProvider.GOOGLE,
                        onClick = { provider = ReportMapProvider.GOOGLE; reload() },
                        label = { Text("Google Maps") }
                    )
                    FilterChip(
                        selected = provider == ReportMapProvider.OPEN_STREET_MAP,
                        onClick = { provider = ReportMapProvider.OPEN_STREET_MAP; reload() },
                        label = { Text("خريطة احتياطية") }
                    )
                }
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("ابحث عن العنوان أو الموقع") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::reload, modifier = Modifier.weight(1f)) { Text("بحث") }
                    OutlinedButton(
                        onClick = {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) useLastKnownLocation()
                            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("موقعي الحالي") }
                }
                if (locationMessage.isNotBlank()) Text(locationMessage)
                Text("حرّك الخريطة وكبّرها حتى تصل لعين المعاينة، ثم التقط الجزء الظاهر وارسم فوقه.")
                AndroidView(
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(reportMapUrl(query, provider))
                            webView = this
                        }
                    },
                    update = { view -> webView = view },
                    modifier = Modifier.fillMaxWidth().height(430.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val view = webView ?: return@Button
                if (view.width <= 0 || view.height <= 0) return@Button
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                onCapture(bitmap)
            }) { Text("التقاط الجزء والرسم فوقه") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
