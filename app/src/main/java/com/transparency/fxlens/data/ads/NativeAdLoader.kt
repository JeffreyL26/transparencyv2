package com.transparency.fxlens.data.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.transparency.fxlens.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lädt genau EINE Native-Ad für die Listen-Übersicht (CLAUDE.md §5/§7.4).
 * Hält die geladene Anzeige als [nativeAd]-Flow; die UI rendert sie nur, wenn
 * `!adFree` und Consent vorliegt. Verwendet immer die Test-Unit-ID im Debug.
 */
class NativeAdLoader(private val appContext: Context) {

    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd: StateFlow<NativeAd?> = _nativeAd

    @Volatile private var loading = false

    fun load() {
        if (loading || _nativeAd.value != null) return
        loading = true
        AdLoader.Builder(appContext, BuildConfig.ADMOB_NATIVE_UNIT_ID)
            .forNativeAd { ad ->
                loading = false
                _nativeAd.value?.destroy()
                _nativeAd.value = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w(TAG, "Native ad failed: ${error.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    /** Anzeige verwerfen (z. B. wenn adFree gekauft wurde). */
    fun clear() {
        _nativeAd.value?.destroy()
        _nativeAd.value = null
    }

    private companion object { const val TAG = "NativeAdLoader" }
}
