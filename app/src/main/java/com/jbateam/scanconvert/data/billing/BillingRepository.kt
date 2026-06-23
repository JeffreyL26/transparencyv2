package com.jbateam.scanconvert.data.billing

import android.app.Activity
import android.app.Application
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.jbateam.scanconvert.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Play-Billing-8-Naht (CLAUDE.md §6.2). Hält die Verbindung, lädt ProductDetails,
 * wickelt Käufe ab (acknowledge/consume) und leitet die [Entitlements] ab.
 *
 * Bewusst SDK-gekapselt: nach außen nur [entitlements] (abgeleitete Flags) und
 * [products] ([ProductInfo] mit `formattedPrice`) — Paywall/ViewModel importieren
 * keine Play-Billing-Typen. Preise kommen ausschließlich von Google (§11).
 *
 * Degradiert sanft: ohne Play-Dienste/ohne Verbindung bleibt der gecachte Stand
 * stehen, nichts blockiert die UI.
 */
class BillingRepository(
    private val app: Application,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) : EntitlementSource {
    private val _entitlements = MutableStateFlow(Entitlements())
    override val entitlements: StateFlow<Entitlements> = _entitlements

    private val _products = MutableStateFlow<List<ProductInfo>>(emptyList())
    override val products: StateFlow<List<ProductInfo>> = _products

    /** Aktuell „besessene" dauerhafte Produkt-IDs (ohne Vacation-Pass, der ist zeitbasiert). */
    @Volatile private var owned: Set<String> = emptySet()
    @Volatile private var detailsById: Map<String, ProductDetails> = emptyMap()
    @Volatile private var connected = false

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { purchases.forEach { handlePurchase(it) } }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w(TAG, "Purchase update failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            // v8: PendingPurchasesParams ist Pflicht; die parameterlose Variante ist entfernt.
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        // v8: SDK reconnectet selbst → onBillingServiceDisconnected kann leer bleiben.
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        // 1) Cache sofort spiegeln (sofortige UX, auch offline).
        scope.launch { _entitlements.value = prefs.cachedEntitlements.first() }
        // 2) Verbinden, dann ProductDetails laden + Käufe wiederherstellen.
        connect()
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    queryProducts()
                    scope.launch { restore() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-Reconnection (v8) übernimmt; hier nur den Status spiegeln.
                connected = false
            }
        })
    }

    private fun queryProducts() {
        val productList = Products.ALL.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        // PBL 8: zweiter Callback-Parameter ist QueryProductDetailsResult.
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${result.responseCode} ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val list = queryResult.productDetailsList
            detailsById = list.associateBy { it.productId }
            publishProducts()
        }
    }

    private fun publishProducts() {
        _products.value = Products.ALL.map { id ->
            val price = detailsById[id]?.oneTimePurchaseOfferDetails?.formattedPrice
            ProductInfo(id = id, formattedPrice = price)
        }
    }

    /** Bestehende Käufe wiederherstellen (Start + „Käufe wiederherstellen"). */
    suspend fun restore() {
        if (!connected) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryPurchases failed: ${result.responseCode} ${result.debugMessage}")
                return@queryPurchasesAsync
            }
            scope.launch {
                // Dauerhaft besessene Nicht-Consumables aus den aktiven Käufen neu aufbauen
                // (deckt auch Erstattungen ab — fehlt der Kauf, fällt das Entitlement weg).
                val nextOwned = mutableSetOf<String>()
                purchases.forEach { p ->
                    if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
                    if (Products.VACATION_PASS in p.products) {
                        // Consumable separat behandeln (consume + 7-Tage-Ablauf).
                        handlePurchase(p)
                    } else {
                        p.products.forEach { id -> if (!Products.isConsumable(id)) nextOwned += id }
                        if (!p.isAcknowledged) acknowledge(p)
                    }
                }
                owned = nextOwned
                recomputeEntitlements()
            }
        }
    }

    private suspend fun handlePurchase(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        when {
            Products.VACATION_PASS in p.products -> {
                // Consumable: 7-Tage-Ablauf lokal setzen und consumen (erneut kaufbar).
                prefs.setVacationPassExpiry(System.currentTimeMillis() + Products.VACATION_PASS_DURATION_MS)
                val params = ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                client.consumeAsync(params) { r, _ ->
                    if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "consume failed: ${r.responseCode} ${r.debugMessage}")
                    }
                }
            }
            else -> {
                // Non-consumables: einmal acknowledgen, nie consumen.
                p.products.forEach { id -> if (!Products.isConsumable(id)) owned = owned + id }
                if (!p.isAcknowledged) acknowledge(p)
            }
        }
        recomputeEntitlements()
    }

    private fun acknowledge(p: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(p.purchaseToken).build()
        client.acknowledgePurchase(params) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${r.responseCode} ${r.debugMessage}")
            }
        }
    }

    /** Leitet die Entitlements aus aktiven Käufen + Vacation-Pass-Ablauf ab (§2) und cacht sie. */
    private suspend fun recomputeEntitlements() {
        val expiry = prefs.vacationPassExpiry.first()
        val o = owned
        val e = Entitlements(
            adFree = Products.ADFREE in o || Products.FULL_PREMIUM in o ||
                expiry > System.currentTimeMillis(),
            unlimitedLists = Products.BUSINESS in o || Products.FULL_PREMIUM in o,
            listExport = Products.BUSINESS in o || Products.FULL_PREMIUM in o,
        )
        _entitlements.value = e
        prefs.cacheEntitlements(e)
        Log.d(TAG, "Entitlements: $e (owned=$o, passExpiry=$expiry)")
    }

    /** Startet den Kauf-Flow für eine Produkt-ID; no-op, wenn ProductDetails fehlen. */
    fun launchPurchase(activity: Activity, productId: String) {
        val details = detailsById[productId]
        if (details == null) {
            Log.w(TAG, "launchPurchase: no ProductDetails for $productId (not connected / unknown id)")
            return
        }
        // Offer-Token defensiv setzen: das offizielle Sample setzt ihn auch bei
        // einmaligen INAPP-Produkten; sonst kann der Flow zur Laufzeit scheitern.
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetails?.offerToken?.let { builder.setOfferToken(it) }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(builder.build()))
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private companion object {
        const val TAG = "BillingRepository"
    }
}
