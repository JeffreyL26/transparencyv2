package com.jbateam.scanconvert.data.billing

/**
 * Die drei Fähigkeiten, die der Code prüft (CLAUDE.md §2). Produkte schalten
 * Kombinationen davon frei — die UI/Gating-Logik kennt nur diese Flags, nie die
 * konkreten Käufe.
 *
 * Quelle der Wahrheit = Google Play (über [BillingRepository]); der DataStore-Cache
 * in [com.jbateam.scanconvert.data.Prefs] sorgt nur für sofortige UX, bevor Billing
 * verbunden ist / offline.
 */
data class Entitlements(
    val adFree: Boolean = false,
    val unlimitedLists: Boolean = false,
    val listExport: Boolean = false,
) {
    /** Free: max. 3 Listen; mit [unlimitedLists] unbegrenzt (§2/§4). */
    val listLimit: Int get() = if (unlimitedLists) Int.MAX_VALUE else FREE_LIST_LIMIT

    companion object {
        const val FREE_LIST_LIMIT = 3
        /** Custom-Kurs-Limit — unverändert für ALLE, kein Gate (§2/§11). */
        const val CUSTOM_RATE_LIMIT = 5
    }
}
