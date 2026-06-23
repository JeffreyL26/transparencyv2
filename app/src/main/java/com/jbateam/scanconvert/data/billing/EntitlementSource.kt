package com.jbateam.scanconvert.data.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * Lese-Naht für Entitlements + Produktkatalog (CLAUDE.md §13.2).
 *
 * EINE Quelle, aus der das [com.jbateam.scanconvert.MainViewModel] den
 * [entitlements]- und [products]-Flow bezieht — Signaturen 1:1 wie beim
 * [BillingRepository] ([StateFlow]<[Entitlements]> + [StateFlow]<List<[ProductInfo]>>).
 *
 * Produktivquelle ist und bleibt das [BillingRepository] (Google Play =
 * Source of Truth, §6.2). Im Debug-Build wird es von [DebugEntitlementSource]
 * umhüllt, damit kaufpflichtige Features ohne echte Kaufabwicklung lokal testbar
 * sind. In Release greift die Naht nie: der AppContainer wählt direkt das
 * [BillingRepository] (Auswahl hinter `BuildConfig.DEBUG`, §13.2).
 */
interface EntitlementSource {
    /** Abgeleitete Fähigkeiten (§2) — Quelle für Gates wie `canCreateList` (§4). */
    val entitlements: StateFlow<Entitlements>

    /** Produktkatalog inkl. von Google geliefertem `formattedPrice` (nie hartkodiert, §11). */
    val products: StateFlow<List<ProductInfo>>
}
