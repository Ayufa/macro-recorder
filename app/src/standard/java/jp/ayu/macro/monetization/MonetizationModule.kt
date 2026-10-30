package jp.ayu.macro.monetization

import android.app.Activity
import android.content.Context
import android.view.ViewGroup

/** standard フレーバー: 広告なし・課金なし・無制限。ネットワークも使わない。 */
internal object MonetizationModule {
    fun createEntitlements(): EntitlementManager = NoOpEntitlements
    fun createAds(entitlements: EntitlementManager): AdsProvider = NoOpAds
}

private object NoOpEntitlements : EntitlementManager {
    override val purchasesAvailable = false
    override val isAdFree = true
    override val maxSavedMacros: Int? = null
    override fun start(context: Context) {}
    override fun launchPurchase(activity: Activity, productId: String) {}
    override fun priceOf(productId: String): String? = null
    override fun addListener(l: () -> Unit) {}
    override fun removeListener(l: () -> Unit) {}
}

private object NoOpAds : AdsProvider {
    override fun initialize(activity: Activity) {}
    override fun attachBanner(activity: Activity, container: ViewGroup) {}
    override val privacyOptionsAvailable = false
    override fun showPrivacyOptions(activity: Activity) {}
    override fun destroy() {}
}
