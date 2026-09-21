package com.khabir.app.monetization

import android.app.Activity
import android.os.SystemClock
import androidx.compose.runtime.*
import com.android.billingclient.api.ProductDetails
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.*
import com.google.android.gms.ads.rewarded.*
import com.google.android.ump.*
import com.khabir.app.BuildConfig
import com.khabir.app.data.auth.GoogleSession
import com.khabir.app.data.monetization.ActiveUseClock
import com.khabir.app.data.monetization.WorkAdEvents
import kotlinx.coroutines.*

class MonetizationController(private val activity: Activity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val api = MonetizationApi(activity.applicationContext)
    var entitlement by mutableStateOf(Entitlements()); private set
    var product by mutableStateOf<ProductDetails?>(null); private set
    var message by mutableStateOf(""); private set
    var rewardReady by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var adsReady by mutableStateOf(false); private set
    private val clock = ActiveUseClock()
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var consentStarted = false
    private var initializing = false
    private var loadingInterstitial = false
    private var loadingReward = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadedInterstitialAt = 0L
    private var loadedRewardAt = 0L
    private var foreground = false
    private var showing = false
    var homeVisible = false
    private var refreshing = false
    private var clockEnabled = false
    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
        entitlement = Entitlements(); product = null; clearAds()
        scope.launch { refresh(); billing.connect(); if (homeVisible) prepareAds() }
    }
    val billing = PlayBillingManager(activity, api, scope, { product = it }, { message = it }, { refresh() })

    init {
        GoogleSession.auth(activity)?.addAuthStateListener(authListener)
        scope.launch { WorkAdEvents.interactions.collect { onInteraction() } }
        scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                if (foreground) { refresh(); preload() }
            }
        }
        scope.launch {
            WorkAdEvents.boundaries.collect {
                // Let the UI close its review/capture surface before checking blockers.
                delay(32)
                showAtBoundary()
            }
        }
    }
    fun onResume() {
        foreground = true; syncClock()
        scope.launch { refresh(); billing.connect(); if (homeVisible) prepareAds() }
    }
    fun onPause() { foreground = false; clock.pause(SystemClock.elapsedRealtime()); clockEnabled = false }
    fun onInteraction() { syncClock(); if (clockEnabled && !showing && !busy) clock.interact(SystemClock.elapsedRealtime()) }
    private fun syncClock() {
        val enabled = foreground && !showing && entitlement.canAdvertise() && sameAccount()
        if (enabled && !clockEnabled) clock.resume(SystemClock.elapsedRealtime())
        if (!enabled && clockEnabled) {
            clock.pause(SystemClock.elapsedRealtime())
            if (!entitlement.canAdvertise()) clock.adShown(SystemClock.elapsedRealtime())
        }
        clockEnabled = enabled
    }
    suspend fun refresh() {
        if (refreshing) return
        if (!api.configured || GoogleSession.auth(activity)?.currentUser == null) { entitlement = Entitlements(); return }
        refreshing = true
        try { entitlement = api.entitlements() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "تعذر تحديث الاشتراكات والمكافآت الآن" }
        finally { refreshing = false }
        if (!entitlement.canAdvertise()) interstitial = null
        syncClock()
    }
    fun onHomeVisible(visible: Boolean) {
        homeVisible = visible
        if (visible) scope.launch { refresh(); prepareAds() }
    }
    private fun sameAccount() = GoogleSession.auth(activity)?.currentUser?.uid == entitlement.uid
    private fun canUseAds() = sameAccount() && entitlement.fresh() && entitlement.adsEnabled && entitlement.now() >= entitlement.adsStartAt
    fun canShowBanner() = adsReady && consent.canRequestAds() && entitlement.canAdvertise() && sameAccount() && !showing && !WorkAdEvents.isBlocked()
    private fun prepareAds() {
        if (!foreground || !homeVisible || !canUseAds() || WorkAdEvents.isBlocked()) return
        if (adsReady) { preload(); return }
        if (consentStarted) return
        consentStarted = true
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            if (!homeVisible || !foreground) { consentStarted = false; return@requestConsentInfoUpdate }
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error == null && consent.canRequestAds()) initializeAds() else consentStarted = false
            }
        }, { consentStarted = false })
    }
    private fun initializeAds() {
        if (initializing || adsReady || !consent.canRequestAds()) return
        initializing = true
        scope.launch {
            withContext(Dispatchers.IO) { MobileAds.initialize(activity.applicationContext) {} }
            initializing = false; adsReady = true; preload()
        }
    }
    private fun preload() {
        if (!adsReady || !consent.canRequestAds() || !canUseAds()) return
        if (entitlement.canAdvertise() && interstitial == null && !loadingInterstitial && BuildConfig.ADMOB_INTERSTITIAL_ID.isNotBlank()) {
            loadingInterstitial = true
            InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { loadingInterstitial = false; interstitial = ad; loadedInterstitialAt = SystemClock.elapsedRealtime() }
                override fun onAdFailedToLoad(error: LoadAdError) { loadingInterstitial = false; interstitial = null }
            })
        }
        if (rewarded == null && !loadingReward && BuildConfig.ADMOB_REWARDED_ID.isNotBlank()) {
            loadingReward = true
            RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { loadingReward = false; rewarded = ad; rewardReady = true; loadedRewardAt = SystemClock.elapsedRealtime() }
                override fun onAdFailedToLoad(error: LoadAdError) { loadingReward = false; rewarded = null; rewardReady = false }
            })
        }
    }
    private fun showAtBoundary() {
        val now = SystemClock.elapsedRealtime()
        if (!foreground || showing || busy || WorkAdEvents.isBlocked() || !entitlement.canAdvertise() || !sameAccount() || !consent.canRequestAds() || !clock.isDue(now)) return
        val ad = interstitial ?: run { preload(); return }
        if (now - loadedInterstitialAt > 55 * 60_000L) { interstitial = null; preload(); return }
        interstitial = null; showing = true; clock.pause(now); clockEnabled = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { clock.adShown(SystemClock.elapsedRealtime()) }
            override fun onAdDismissedFullScreenContent() { finishFullScreen() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { finishFullScreen() }
        }
        ad.show(activity)
    }
    private fun finishFullScreen() { showing = false; syncClock(); preload() }
    fun watchReward() {
        if (!foreground || busy || showing || !canUseAds() || !consent.canRequestAds()) return
        val ad = rewarded ?: run { preload(); message = "الإعلان غير جاهز الآن"; return }
        if (SystemClock.elapsedRealtime() - loadedRewardAt > 55 * 60_000L) { rewarded = null; rewardReady = false; preload(); return }
        busy = true
        scope.launch {
            try {
                val session = api.call("rewardSession").getString("sessionId")
                if (!foreground || !canUseAds()) return@launch
                ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(entitlement.uid).setCustomData(session).build())
                rewarded = null; rewardReady = false; showing = true; clock.pause(SystemClock.elapsedRealtime()); clockEnabled = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        finishFullScreen()
                        scope.launch {
                            repeat(6) { delay(2_000); refresh() }
                        }
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) { finishFullScreen(); message = "تعذر عرض الإعلان" }
                }
                ad.show(activity) { message = "اكتملت المشاهدة. جارٍ تأكيد المكافأة…" }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "تعذر بدء الإعلان الآن" }
            finally { busy = false }
        }
    }
    fun deleteAccount(onDeleted: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                api.call("deleteAccount")
                GoogleSession.auth(activity)?.signOut()
                entitlement = Entitlements(); clearAds(); onDeleted()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "تعذر حذف الحساب. سجل الدخول مرة أخرى ثم أعد المحاولة خلال خمس دقائق." }
            finally { busy = false }
        }
    }
    fun refreshFromUser() { scope.launch { refresh(); billing.connect(); preload() } }
    fun showPrivacyOptions() {
        if (consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { clearAds(); if (consent.canRequestAds()) initializeAds() }
        }
    }
    fun privacyOptionsRequired() = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    private fun clearAds() { rewarded = null; interstitial = null; rewardReady = false; adsReady = false; consentStarted = false }
    fun close() { GoogleSession.auth(activity)?.removeAuthStateListener(authListener); billing.close(); scope.cancel(); clearAds() }
}

val LocalMonetization = staticCompositionLocalOf<MonetizationController?> { null }
