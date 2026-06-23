package com.jbateam.scanconvert

import android.app.Application
import androidx.room.Room
import com.jbateam.scanconvert.data.ListsRepository
import com.jbateam.scanconvert.data.Prefs
import com.jbateam.scanconvert.data.RatesRepository
import com.jbateam.scanconvert.data.ads.ConsentManager
import com.jbateam.scanconvert.data.ads.NativeAdLoader
import com.jbateam.scanconvert.data.ads.RewardedAdManager
import com.jbateam.scanconvert.data.billing.BillingRepository
import com.jbateam.scanconvert.data.billing.DebugEntitlementSource
import com.jbateam.scanconvert.data.billing.EntitlementSource
import com.jbateam.scanconvert.data.db.FxDatabase
import com.jbateam.scanconvert.data.db.MIGRATION_1_2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FxLensApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Billing früh verbinden, damit Käufe beim Start wiederhergestellt sind (§11).
        container.billingRepository.start()
    }
}

class AppContainer(app: Application) {
    /** App-weiter Scope für Repositories ohne eigenen Lifecycle (Billing). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = Prefs(app)
    val ratesRepository = RatesRepository(app)
    private val db = Room.databaseBuilder(app, FxDatabase::class.java, "fxlens.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val listsRepository = ListsRepository(db.listsDao())
    val billingRepository = BillingRepository(app, prefs, appScope)

    /**
     * Quelle der Entitlements (CLAUDE.md §13.2): In Release direkt das
     * [BillingRepository] (Produktivquelle, Google Play = Source of Truth).
     * Im Debug-Build umhüllt von [DebugEntitlementSource], damit kaufpflichtige
     * Features ohne echte Kaufabwicklung lokal testbar sind. Release referenziert
     * die Debug-Quelle nie — `BuildConfig.DEBUG` ist dort eine Compile-Zeit-`false`,
     * der Zweig ist toter Code und wird nie ausgeführt.
     */
    val entitlementsSource: EntitlementSource =
        if (BuildConfig.DEBUG) DebugEntitlementSource(app, billingRepository, appScope)
        else billingRepository

    // Werbung & Consent (CLAUDE.md §6/§8) — Objekte app-weit, der Consent-Flow
    // selbst läuft Activity-gebunden in MainActivity.
    val consentManager = ConsentManager(app)
    val nativeAdLoader = NativeAdLoader(app)
    val rewardedAdManager = RewardedAdManager(app)
}
