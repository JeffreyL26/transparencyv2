package com.transparency.fxlens.data.billing

/**
 * Produktkatalog (CLAUDE.md §3). Alle Produkte sind einmalige In-App-Produkte
 * (ProductType.INAPP). Preise werden ausschließlich in der Play Console gesetzt —
 * der Code nutzt nur diese IDs und zeigt den von Google gelieferten formattedPrice.
 */
object Products {
    const val ADFREE = "adfree"                 // non-consumable → adFree (dauerhaft)
    const val VACATION_PASS = "vacation_pass"   // consumable     → adFree 7 Tage (lokaler Ablauf)
    const val BUSINESS = "business"             // non-consumable → unlimitedLists + listExport
    const val FULL_PREMIUM = "full_premium"     // non-consumable → adFree + unlimitedLists + listExport

    /** Reihenfolge der Anzeige in der Paywall. */
    val ALL: List<String> = listOf(ADFREE, VACATION_PASS, BUSINESS, FULL_PREMIUM)

    /** Der einzige consumable: wird nach Kauf consumt + 7-Tage-Ablauf in Prefs geschrieben (§3). */
    fun isConsumable(productId: String): Boolean = productId == VACATION_PASS

    /** Pass-Laufzeit in Millisekunden. */
    const val VACATION_PASS_DURATION_MS = 7L * 24 * 60 * 60 * 1000
}

/**
 * Entkoppelte Preis-Info für die UI: Produkt-ID + von Google gelieferter,
 * lokalisierter Preis ([formattedPrice], null solange ProductDetails fehlen).
 * Hält bewusst KEINE Play-Billing-Typen, damit Paywall/ViewModel SDK-frei bleiben.
 */
data class ProductInfo(
    val id: String,
    val formattedPrice: String?,
)

/** Kontext-abhängige Paywall-Headline (§6.3/§7.1). */
enum class PaywallContext { LISTS, EXPORT, GENERIC }
