package com.jbateam.scanconvert.data.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MobileAds.initialize() genau einmal und off-main-thread (CLAUDE.md §8.3).
 * Aufruf NUR, wenn Consent erlaubt UND `!entitlements.adFree` UND nicht erste
 * Session (§5/§11) — diese Bedingungen prüft der Aufrufer.
 */
object AdsInitializer {
    private val started = AtomicBoolean(false)

    @Volatile
    var ready = false
        private set

    fun ensureInitialized(context: Context, scope: CoroutineScope, onReady: () -> Unit) {
        if (ready) { onReady(); return }
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {
                ready = true
                onReady()
            }
        }
    }
}
