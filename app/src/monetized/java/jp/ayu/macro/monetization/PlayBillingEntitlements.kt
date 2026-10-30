package jp.ayu.macro.monetization

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Play Billing による買い切り商品（remove_ads / unlimited_macros）の管理。
 * 購入状態は端末内（SharedPreferences）にもキャッシュし、オフラインでも有効にする。
 * 注意: 本番では購入トークンをサーバーで検証するのが推奨（docs/MONETIZATION_ja.md 参照）。
 */
internal class PlayBillingEntitlements : EntitlementManager {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private lateinit var prefs: SharedPreferences
    private var client: BillingClient? = null
    private val details = mutableMapOf<String, ProductDetails>()

    override val purchasesAvailable = true
    override val isAdFree get() = owned(Products.REMOVE_ADS)
    override val maxSavedMacros: Int? get() = if (owned(Products.UNLIMITED_MACROS)) null else FreeTier.FREE_MACRO_LIMIT

    private fun owned(id: String) = ::prefs.isInitialized && prefs.getBoolean("owned_$id", false)

    override fun start(context: Context) {
        prefs = context.getSharedPreferences("entitlements", Context.MODE_PRIVATE)
        val c = BillingClient.newBuilder(context)
            .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty(), full = false) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) { Log.w(TAG, "billing setup: ${result.debugMessage}"); return }
                refreshPurchases(); loadDetails()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun refreshPurchases() {
        client?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, full = true) }
    }

    private fun loadDetails() {
        val products = Products.ALL.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client?.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, res ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            synchronized(details) { res.productDetailsList.forEach { details[it.productId] = it } }
            notifyChanged()
        }
    }

    /** full = true のときは「持っていない商品」をオフにする（返金などの反映） */
    private fun handle(purchases: List<Purchase>, full: Boolean) {
        val ownedNow = mutableSetOf<String>()
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            ownedNow += p.products
            if (!p.isAcknowledged) {
                client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
            }
        }
        val e = prefs.edit()
        for (id in Products.ALL) {
            if (id in ownedNow) e.putBoolean("owned_$id", true) else if (full) e.putBoolean("owned_$id", false)
        }
        e.apply()
        notifyChanged()
    }

    override fun launchPurchase(activity: Activity, productId: String) {
        val d = synchronized(details) { details[productId] }
        val c = client
        if (d == null || c == null || !c.isReady) { loadDetails(); return }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
        ).build()
        c.launchBillingFlow(activity, params)
    }

    override fun priceOf(productId: String): String? =
        synchronized(details) { details[productId] }?.oneTimePurchaseOfferDetails?.formattedPrice

    override fun addListener(l: () -> Unit) { listeners += l }
    override fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyChanged() = main.post { listeners.forEach { it() } }

    private companion object { const val TAG = "Entitlements" }
}
