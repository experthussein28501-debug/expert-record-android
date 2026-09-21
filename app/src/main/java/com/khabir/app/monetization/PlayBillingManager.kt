package com.khabir.app.monetization

import android.app.Activity
import com.android.billingclient.api.*
import com.khabir.app.data.auth.GoogleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

data class SubscriptionPlan(val basePlanId: String, val title: String, val intendedEgp: Int, val period: String)
val subscriptionPlans = listOf(
    SubscriptionPlan("monthly", "شهر", 50, "P1M"),
    SubscriptionPlan("quarterly", "٣ أشهر", 90, "P3M"),
    SubscriptionPlan("halfyear", "٦ أشهر", 150, "P6M"),
    SubscriptionPlan("annual", "سنة", 240, "P1Y")
)

class PlayBillingManager(
    private val activity: Activity,
    private val api: MonetizationApi,
    private val scope: CoroutineScope,
    private val onProducts: (ProductDetails?) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onVerified: suspend () -> Unit
) : PurchasesUpdatedListener {
    private var connecting = false
    private val verifying = mutableSetOf<String>()
    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()

    fun connect() {
        if (!api.configured || GoogleSession.auth(activity)?.currentUser == null) return
        if (client.isReady) { loadProducts(); restore(); return }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { loadProducts(); restore() }
                else onMessage("تعذر الاتصال بمتجر Google Play")
            }
            override fun onBillingServiceDisconnected() { connecting = false }
        })
    }
    private fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) onProducts(details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID })
        }
    }
    fun purchase(product: ProductDetails, basePlanId: String) {
        if (!client.isReady || !api.configured) { connect(); return }
        val uid = GoogleSession.auth(activity)?.currentUser?.uid ?: return
        val offer = product.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId && it.offerId == null } ?: return
        val accountId = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        val params = BillingFlowParams.newBuilder().setObfuscatedAccountId(accountId).setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()
        )).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) onMessage("تعذر بدء الدفع. حدّث بيانات المتجر وحاول مرة أخرى.")
    }
    fun restore() {
        if (!client.isReady) { connect(); return }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::verify)
                scope.launch { onVerified() }
            } else onMessage("تعذر استعادة المشتريات الآن")
        }
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::verify)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> onMessage("لم تكتمل عملية الشراء. حاول مرة أخرى من Google Play.")
        }
    }
    private fun verify(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) { onMessage("الدفع قيد الانتظار. يبدأ الاشتراك بعد تأكيد Google Play."); return }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || !verifying.add(purchase.purchaseToken)) return
        scope.launch {
            try {
                api.call("verifyPurchase", JSONObject().put("purchaseToken", purchase.purchaseToken))
                onVerified(); onMessage("تم التحقق من الاشتراك")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { onMessage("تعذر تأكيد الاشتراك الآن. استخدم استعادة المشتريات بعد عودة الاتصال.") }
            finally { verifying.remove(purchase.purchaseToken) }
        }
    }
    fun close() { client.endConnection() }
    companion object { const val PRODUCT_ID = "khabir_ad_free" }
}
