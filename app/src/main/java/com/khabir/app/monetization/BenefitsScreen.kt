package com.khabir.app.monetization

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.khabir.app.BuildConfig
import com.khabir.app.presentation.components.InlineHelp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitsScreen(onBack: () -> Unit, onAccountDeleted: () -> Unit) {
    val controller = LocalMonetization.current ?: return
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val entitlement = controller.entitlement
    val activeSubscription = entitlement.fresh() && entitlement.subscriptionUntil > entitlement.now()
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!controller.busy) confirmDelete = false }, title = { Text("حذف الحساب؟") },
        text = { Text("سيُحذف حساب الدخول ورصيد المكافآت وربط الاشتراك. ستظل ملفاتك المحلية على الجهاز. حذف الحساب لا يلغي التجديد في Google Play؛ ألغِ الاشتراك من المتجر إذا أردت إيقاف الدفع.") },
        confirmButton = { TextButton(enabled = !controller.busy, onClick = { controller.deleteAccount { confirmDelete = false; onAccountDeleted() } }) { Text("حذف حسابي") } },
        dismissButton = { TextButton(enabled = !controller.busy, onClick = { confirmDelete = false }) { Text("رجوع") } }
    )
    Scaffold(topBar = { TopAppBar(title = { Text("المهام والاشتراك") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
    }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وقت بدون إعلانات", style = MaterialTheme.typography.titleLarge)
                    val freeUntil = maxOf(entitlement.adsStartAt.takeIf { it != Long.MAX_VALUE } ?: 0, entitlement.rewardUntil, entitlement.subscriptionUntil)
                    if (entitlement.fresh() && freeUntil > entitlement.now()) {
                        Text("بدون إعلانات حتى ${Instant.ofEpochMilli(freeUntil).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}")
                    } else Text(if (entitlement.fresh()) "شاهد إعلانًا للحصول على وقت إضافي" else "سجل الدخول وحدّث الحالة لعرض رصيدك")
                    Text("إعلان: ساعتان · إعلانان: ٤ ساعات · ٣: ٦ ساعات · ٤: ٨ ساعات · ٥: ١٢ ساعة")
                    if (entitlement.fresh()) Text("تقدم الدورة: ${entitlement.rewardCount} / ٥")
                    Button(onClick = controller::watchReward, enabled = controller.rewardReady && !controller.busy && !activeSubscription && entitlement.fresh() && entitlement.now() >= entitlement.adsStartAt,
                        modifier = Modifier.fillMaxWidth()) { Text("مشاهدة إعلان وكسب وقت") }
                    InlineHelp("المكافآت", "تُضاف الساعات بعد اكتمال المشاهدة وتأكيدها. الإعلان الخامس يضيف ٤ ساعات بدل ساعتين. يبدأ عدّ الخمسة من جديد بعد اكتمالها أو انتهاء رصيد المكافأة. الوقت يشمل البانر والفيديو.")
                }
            }
            Text("اشتراك بدون إعلانات", style = MaterialTheme.typography.titleLarge)
            subscriptionPlans.forEach { plan ->
                val offer = controller.product?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == plan.basePlanId && it.offerId == null }
                val phase = offer?.pricingPhases?.pricingPhaseList?.singleOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.title, style = MaterialTheme.typography.titleMedium)
                            Text(phase?.formattedPrice ?: "غير متاح من المتجر حاليًا")
                            if (phase != null) Text("يتجدد تلقائيًا كل ${plan.title}. يمكنك الإلغاء من Google Play.", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(enabled = phase != null && phase.billingPeriod == plan.period && !activeSubscription && controller.api.configured,
                            onClick = { controller.product?.let { controller.billing.purchase(it, plan.basePlanId) } }) { Text("اشتراك") }
                    }
                }
            }
            OutlinedButton(onClick = { controller.billing.restore(); controller.refreshFromUser() }, modifier = Modifier.fillMaxWidth()) { Text("استعادة المشتريات وتحديث الرصيد") }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${PlayBillingManager.PRODUCT_ID}&package=${context.packageName}"))) }
            }) { Text("إدارة الاشتراك وإلغاؤه") }
            if (controller.privacyOptionsRequired()) TextButton(onClick = controller::showPrivacyOptions) { Text("اختيارات خصوصية الإعلانات") }
            if (isSecurePlayUrl(BuildConfig.PRIVACY_POLICY_URL)) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
            }) { Text("سياسة الخصوصية") }
            if (isSecurePlayUrl(BuildConfig.ACCOUNT_DELETION_URL)) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ACCOUNT_DELETION_URL))) }
            }) { Text("طلب حذف الحساب عبر الويب") }
            TextButton(enabled = controller.api.configured && !controller.busy && entitlement.uid.isNotBlank(), onClick = { confirmDelete = true }) { Text("حذف حسابي من التطبيق") }
            if (controller.message.isNotBlank()) Text(controller.message)
            InlineHelp("بداية الإعلانات", "تبدأ الإعلانات بعد مرور شهرين على إطلاق التطبيق في Google Play. يحصل الحساب الجديد على شهر مجاني من أول تسجيل دخول. يبدأ الإعلان عند انتهاء الفترتين، وبعد ٢٠ دقيقة استخدام فعلي وعند إنهاء عملك فقط.")
        }
    }
}

@Composable
fun HomeBanner() {
    val controller = LocalMonetization.current ?: return
    if (!controller.canShowBanner() || BuildConfig.ADMOB_BANNER_ID.isBlank()) return
    val context = LocalContext.current
    val ad = remember(context) { AdView(context).apply { adUnitId = BuildConfig.ADMOB_BANNER_ID; setAdSize(AdSize.BANNER); loadAd(AdRequest.Builder().build()) } }
    DisposableEffect(ad) { onDispose { ad.destroy() } }
    AndroidView(factory = { ad }, modifier = Modifier.fillMaxWidth().height(50.dp))
}


internal fun isSecurePlayUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)
