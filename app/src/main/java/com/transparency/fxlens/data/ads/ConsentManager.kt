package com.transparency.fxlens.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.transparency.fxlens.BuildConfig

/**
 * UMP-Consent (CLAUDE.md §8.3). Holt bei jedem Start den Consent-Status und zeigt
 * bei Bedarf das Formular. Erst wenn [canRequestAds] true ist, dürfen Ads geladen
 * werden — personalisierte Ads ohne Consent sind verboten (§11).
 */
class ConsentManager(context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    /** UMP schreibt einen Privacy-Options-Eintrag im SettingsSheet vor, wenn REQUIRED (§7.2). */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Aktualisiert den Consent-Status und zeigt ggf. das Formular. [onResult] wird
     * mit `canRequestAds()` aufgerufen (auch im Fehlerfall — evtl. aus einer
     * früheren Session bereits erlaubt).
     */
    fun gatherConsent(activity: Activity, onResult: (canRequestAds: Boolean) -> Unit) {
        val paramsBuilder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            // Debug: EEA erzwingen, damit das Consent-Formular getestet werden kann.
            // TODO(dev): eigene Test-Geräte-Hash-ID aus Logcat via addTestDeviceHashedId() ergänzen.
            paramsBuilder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .build()
            )
        }
        consentInformation.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    onResult(consentInformation.canRequestAds())
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update failed: ${requestError.message}")
                onResult(consentInformation.canRequestAds())
            },
        )
    }

    /** „Datenschutzeinstellungen" im SettingsSheet (von UMP vorgeschrieben, §7.2). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) Log.w(TAG, "Privacy options: ${formError.message}")
        }
    }

    private companion object { const val TAG = "ConsentManager" }
}
