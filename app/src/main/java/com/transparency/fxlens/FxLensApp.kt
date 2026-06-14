package com.transparency.fxlens

import android.app.Application
import androidx.room.Room
import com.transparency.fxlens.data.ListsRepository
import com.transparency.fxlens.data.Prefs
import com.transparency.fxlens.data.RatesRepository
import com.transparency.fxlens.data.ads.ConsentManager
import com.transparency.fxlens.data.ads.NativeAdLoader
import com.transparency.fxlens.data.ads.RewardedAdManager
import com.transparency.fxlens.data.billing.BillingRepository
import com.transparency.fxlens.data.db.FxDatabase
import com.transparency.fxlens.data.db.MIGRATION_1_2
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

    // Werbung & Consent (CLAUDE.md §6/§8) — Objekte app-weit, der Consent-Flow
    // selbst läuft Activity-gebunden in MainActivity.
    val consentManager = ConsentManager(app)
    val nativeAdLoader = NativeAdLoader(app)
    val rewardedAdManager = RewardedAdManager(app)
}
