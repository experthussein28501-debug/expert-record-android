package com.khabir.app.presentation.reports

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationManager
import android.net.Uri
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
import java.net.URLEncoder

internal enum class ReportMapProvider { GOOGLE, OPEN_STREET_MAP }

internal fun reportMapUrl(query: String, provider: ReportMapProvider = ReportMapProvider.GOOGLE): String {
    val target = query.ifBlank { "أسوان" }
    val encoded = URLEncoder.encode(target, Charsets.UTF_8.name()).replace("+", "%20")
    return when (provider) {
        ReportMapProvider.GOOGLE -> "https://www.google.com/maps/search/?api=1&query=$encoded"
        ReportMapProvider.OPEN_STREET_MAP -> {
            val coordinates = target.split(',').map(String::trim)
            val lat = coordinates.getOrNull(0)?.toDoubleOrNull()
            val lon = coordinates.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) {
                "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon"
            } else {
                "https://www.openstreetmap.org/search?query=$encoded"
            }
        }
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
 * خريطة المعاينة داخل التطبيق.
 *
 * Google Maps لا يعمل بصورة مستقرة داخل WebView على كثير من أجهزة أندرويد، لذلك
 * العرض التفاعلي داخل التطبيق يستخدم خريطة ويب مستقرة لا تحتاج API key، مع زر
 * منفصل يفتح Google Maps على نفس العنوان/الإحداثيات عند الحاجة. لقطة الجزء الظاهر
 * تنتقل مباشرة إلى محرر الرسم داخل التقرير.
 */
@Composable
fun InteractiveMapCapture(
    initialQuery: String,
    onDismiss: () -> Unit,
    onCapture: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var locationMessage by remember { mutableStateOf("") }

    fun reloadInApp() {
        webView?.loadUrl(reportMapUrl(query, ReportMapProvider.OPEN_STREET_MAP))
    }

    fun openGoogleMaps() {
        val url = reportMapUrl(query, ReportMapProvider.GOOGLE)
        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.apps.maps")
        }
        val opened = runCatching { context.startActivity(mapsIntent); true }.getOrDefault(false)
        if (!opened) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { locationMessage = "تعذر فتح Google Maps على هذا الجهاز" }
        }
    }

    fun useLastKnownLocation() {
        val location = bestLastKnownLocation(context)
        if (location == null) {
            locationMessage = "تعذر تحديد موقع محفوظ حاليًا. فعّل الموقع ثم حاول مرة أخرى أو ابحث بالعنوان."
            return
        }
        query = "${location.latitude},${location.longitude}"
        locationMessage = "تم تحديد الموقع من الجهاز. حرّك الخريطة وكبّرها لضبط عين المعاينة."
        webView?.loadUrl(reportMapUrl(query, ReportMapProvider.OPEN_STREET_MAP))
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            useLastKnownLocation()
        } else {
            locationMessage = "لم يتم السماح بالموقع. يمكنك البحث عن مكان المعاينة يدويًا."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خريطة المعاينة والرسم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الخريطة بالأسفل تفاعلية داخل سجل الخبير. ويمكن فتح نفس المكان في Google Maps من الزر المنفصل.")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("ابحث عن العنوان أو اكتب الإحداثيات") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::reloadInApp, modifier = Modifier.weight(1f)) { Text("بحث داخل التطبيق") }
                    OutlinedButton(onClick = ::openGoogleMaps, modifier = Modifier.weight(1f)) { Text("Google Maps") }
                }
                OutlinedButton(
                    onClick = {
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) useLastKnownLocation()
                        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تحديد موقعي الحالي") }
                if (locationMessage.isNotBlank()) Text(locationMessage)
                Text("حرّك الخريطة وكبّرها ثم اضغط «التقاط والرسم»؛ ستفتح طبقة الرسم فوق نفس اللقطة.")
                AndroidView(
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = WebViewClient()
                            loadUrl(reportMapUrl(query, ReportMapProvider.OPEN_STREET_MAP))
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
                if (view.width <= 0 || view.height <= 0) {
                    locationMessage = "الخريطة لم تكتمل بعد؛ حرّكها أو أعد البحث ثم حاول الالتقاط."
                    return@Button
                }
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                onCapture(bitmap)
            }) { Text("التقاط والرسم") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
