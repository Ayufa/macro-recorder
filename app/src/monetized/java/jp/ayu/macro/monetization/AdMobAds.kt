package jp.ayu.macro.monetization

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import jp.ayu.macro.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob のバナー（アプリのメイン画面の下だけ。フローティングや他のアプリの上には出さない）。
 * UMP で同意を確認してから SDK を初期化する。remove_ads を購入済みなら何もしない。
 * 既定では Google の公式テスト用 ID を使う。
 */
internal class AdMobAds(private val entitlements: EntitlementManager) : AdsProvider {
    private val initialized = AtomicBoolean(false)
    private var consent: ConsentInformation? = null
    private var banner: AdView? = null
    private var pending: Pair<Activity, ViewGroup>? = null

    override fun initialize(activity: Activity) {
        if (entitlements.isAdFree) return
        val ci = UserMessagingPlatform.getConsentInformation(activity).also { consent = it }
        ci.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                if (err != null) Log.w(TAG, "consent form: ${err.message}")
                if (ci.canRequestAds()) initSdk(activity)
            }
        }, { err -> Log.w(TAG, "consent update: ${err.message}") })
        // 前回までに同意済みなら、待たずに初期化してよい
        if (ci.canRequestAds()) initSdk(activity)
    }

    private fun initSdk(activity: Activity) {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread { pending?.let { (a, c) -> loadBanner(a, c) } }
        }
    }

    override fun attachBanner(activity: Activity, container: ViewGroup) {
        if (entitlements.isAdFree) { container.removeAllViews(); container.visibility = View.GONE; return }
        pending = activity to container
        if (initialized.get()) loadBanner(activity, container)
    }

    private fun loadBanner(activity: Activity, container: ViewGroup) {
        if (entitlements.isAdFree || consent?.canRequestAds() != true) return
        banner?.destroy()
        val widthDp = (activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density).toInt()
        val v = AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        container.removeAllViews(); container.addView(v); container.visibility = View.VISIBLE
        v.loadAd(AdRequest.Builder().build())
        banner = v
    }

    override val privacyOptionsAvailable: Boolean
        get() = consent?.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { err -> if (err != null) Log.w(TAG, "privacy options: ${err.message}") }
    }

    override fun destroy() { banner?.destroy(); banner = null; pending = null }

    private companion object { const val TAG = "AdMobAds" }
}
