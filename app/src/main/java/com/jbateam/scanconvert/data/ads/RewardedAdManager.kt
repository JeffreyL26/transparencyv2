package com.jbateam.scanconvert.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.jbateam.scanconvert.BuildConfig

/**
 * Optionales Rewarded-Video für einmalige Gratis-Unlocks (CLAUDE.md §6/Phase 6) —
 * immer opt-in, nie erzwungen (§5/§11). [onReward] feuert nur bei vollständig
 * angesehener Werbung.
 */
class RewardedAdManager(private val appContext: Context) {

    private var rewardedAd: RewardedAd? = null
    @Volatile private var loading = false

    val isReady: Boolean get() = rewardedAd != null

    fun load() {
        if (loading || rewardedAd != null) return
        loading = true
        RewardedAd.load(
            appContext, BuildConfig.ADMOB_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded failed: ${error.message}")
                }
            },
        )
    }

    /** Zeigt das Video, falls geladen; sonst lädt es vor und ruft [onReward] nicht. */
    fun show(activity: Activity, onReward: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) { load(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewardedAd = null; load() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                Log.w(TAG, "Rewarded show failed: ${error.message}")
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { _ -> onReward() })
    }

    private companion object { const val TAG = "RewardedAdManager" }
}
