package jp.ayu.macro.monetization

/** monetized フレーバー: AdMob + UMP + Play Billing */
internal object MonetizationModule {
    fun createEntitlements(): EntitlementManager = PlayBillingEntitlements()
    fun createAds(entitlements: EntitlementManager): AdsProvider = AdMobAds(entitlements)
}
