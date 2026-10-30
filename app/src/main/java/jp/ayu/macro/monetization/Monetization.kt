package jp.ayu.macro.monetization

import android.app.Activity
import android.content.Context
import android.view.ViewGroup

/**
 * 収益化の共通インターフェース。
 *
 * - standard フレーバー（配布しているもの）: 何もしない実装。広告・課金 SDK は含まれず、INTERNET 権限もなし。全機能が無料・無制限。
 * - monetized フレーバー: AdMob（テスト用 ID が既定）と Play Billing を使う実装。詳しくは docs/MONETIZATION_ja.md。
 *
 * どちらのフレーバーでも、他のアプリの画面内容を集めたり送信したりはしない。
 */
interface AdsProvider {
    /** 同意（UMP）の確認と SDK の初期化。Activity の onCreate から呼ぶ */
    fun initialize(activity: Activity)
    /** バナーを container に貼る。広告なしの場合は container を GONE のままにする */
    fun attachBanner(activity: Activity, container: ViewGroup)
    /** プライバシー設定（同意の変更）を開けるか */
    val privacyOptionsAvailable: Boolean
    fun showPrivacyOptions(activity: Activity)
    fun destroy()
}

/** 購入の種類（Play Console の商品 ID） */
object Products {
    const val REMOVE_ADS = "remove_ads"
    const val UNLIMITED_MACROS = "unlimited_macros"
    val ALL = listOf(REMOVE_ADS, UNLIMITED_MACROS)
}

interface EntitlementManager {
    /** 課金機能が使えるか（standard では常に false。UI は購入ボタンを出さない） */
    val purchasesAvailable: Boolean
    val isAdFree: Boolean
    /** 保存できるマクロの上限。null は無制限 */
    val maxSavedMacros: Int?
    fun start(context: Context)
    /** 購入画面を開く。結果は addListener で通知 */
    fun launchPurchase(activity: Activity, productId: String)
    /** 表示用の価格（取得できていなければ null） */
    fun priceOf(productId: String): String?
    fun addListener(l: () -> Unit)
    fun removeListener(l: () -> Unit)
}

/** フレーバーごとの実装へのアクセス口（実体は src/<flavor>/.../MonetizationModule.kt） */
object Monetization {
    @Volatile private var ent: EntitlementManager? = null
    @Volatile private var adsP: AdsProvider? = null

    fun entitlements(context: Context): EntitlementManager =
        ent ?: synchronized(this) {
            ent ?: MonetizationModule.createEntitlements().also { it.start(context.applicationContext); ent = it }
        }

    fun ads(context: Context): AdsProvider =
        adsP ?: synchronized(this) {
            adsP ?: MonetizationModule.createAds(entitlements(context)).also { adsP = it }
        }
}

/** 無料枠の判定（純粋な関数なのでテストしやすい） */
object FreeTier {
    const val FREE_MACRO_LIMIT = 3
    fun canSaveNew(currentCount: Int, limit: Int?): Boolean = limit == null || currentCount < limit
}
