package com.jbateam.scanconvert.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jbateam.scanconvert.data.billing.Entitlements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistente Keys (Handoff §7): Pins (max 4, Reihenfolge = Pin-Reihenfolge)
 * und Onboarding-Flag — via DataStore.
 */
private val Context.dataStore by preferencesDataStore(name = "scanconvert")

class Prefs(private val context: Context) {

    private val pinsKey = stringPreferencesKey("scanconvert_pins")
    private val onboardedKey = booleanPreferencesKey("scanconvert_onboarded")
    private val seededKey = booleanPreferencesKey("scanconvert_seeded")
    private val recentsKey = stringPreferencesKey("scanconvert_recents")
    private val customsKey = stringPreferencesKey("scanconvert_customs")

    // Entitlement-Cache (§6.1): Quelle der Wahrheit ist Play; der Cache liefert
    // sofortige UX offline / bevor Billing verbunden ist.
    private val entAdFreeKey = booleanPreferencesKey("ent_adfree")
    private val entUnlimitedKey = booleanPreferencesKey("ent_unlimited")
    private val entExportKey = booleanPreferencesKey("ent_export")
    private val vacationExpiryKey = longPreferencesKey("vacation_pass_expiry")

    // App-Start-Zähler (§5: keine Werbung in der ersten Session).
    private val launchCountKey = androidx.datastore.preferences.core.intPreferencesKey("launch_count")

    private val json = Json { ignoreUnknownKeys = true }

    val pins: Flow<List<String>> = context.dataStore.data.map { p ->
        p[pinsKey]?.split(",")?.filter { it.isNotBlank() } ?: CurrencyMeta.DEFAULT_PINNED
    }

    /** Zuletzt genutzte Währungen, neueste zuerst (max 12) — Vorschläge bei Listenanlage (§6). */
    val recents: Flow<List<String>> = context.dataStore.data.map { p ->
        p[recentsKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addRecent(code: String) {
        context.dataStore.edit { p ->
            val cur = p[recentsKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            p[recentsKey] = (listOf(code) + cur.filterNot { it == code }).take(12).joinToString(",")
        }
    }

    /** Vom Nutzer angelegte Custom-Währungen (max. 5, §F2). */
    val customs: Flow<List<CustomCurrency>> = context.dataStore.data.map { p ->
        p[customsKey]?.let { runCatching { json.decodeFromString<List<CustomCurrency>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun setCustoms(list: List<CustomCurrency>) {
        context.dataStore.edit { it[customsKey] = json.encodeToString(list.take(5)) }
    }

    /** null solange noch nicht geladen ist nicht nötig — DataStore emittiert sofort. */
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[onboardedKey] ?: false }

    suspend fun setPins(pins: List<String>) {
        context.dataStore.edit { it[pinsKey] = pins.take(4).joinToString(",") }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[onboardedKey] = value }
    }

    /** Demo-Seed nur beim allerersten Start (Referenz: persistierter leerer Stand bleibt leer). */
    val seeded: Flow<Boolean> = context.dataStore.data.map { it[seededKey] ?: false }

    suspend fun setSeeded() {
        context.dataStore.edit { it[seededKey] = true }
    }

    // ---------- Monetarisierung (§6.1) ----------

    /**
     * Gecachte Entitlements für sofortige UX. adFree ist true, solange ein
     * Vacation-Pass lokal nicht abgelaufen ist — der Ablauf wird hier (nicht
     * serverseitig) geprüft (§3, akzeptiertes Risiko bei 2,99 €).
     */
    val cachedEntitlements: Flow<Entitlements> = context.dataStore.data.map { p ->
        Entitlements(
            adFree = (p[entAdFreeKey] ?: false) || (p[vacationExpiryKey] ?: 0L) > System.currentTimeMillis(),
            unlimitedLists = p[entUnlimitedKey] ?: false,
            listExport = p[entExportKey] ?: false,
        )
    }

    /** Schreibt die drei dauerhaften Entitlement-Flags (Pass-Ablauf separat via [setVacationPassExpiry]). */
    suspend fun cacheEntitlements(e: Entitlements) {
        context.dataStore.edit {
            it[entAdFreeKey] = e.adFree
            it[entUnlimitedKey] = e.unlimitedLists
            it[entExportKey] = e.listExport
        }
    }

    /** Lokaler Vacation-Pass-Ablauf (epoch ms). */
    suspend fun setVacationPassExpiry(ts: Long) {
        context.dataStore.edit { it[vacationExpiryKey] = ts }
    }

    /** Aktueller Pass-Ablauf (epoch ms, 0 = keiner) — für die Entitlement-Ableitung. */
    val vacationPassExpiry: Flow<Long> = context.dataStore.data.map { it[vacationExpiryKey] ?: 0L }

    /**
     * Wievielter App-Start (1-basiert) — für „keine Werbung in der ersten Session" (§5).
     * Liefert den Stand und erhöht ihn atomar; beim allerersten Aufruf = 1.
     */
    suspend fun incrementLaunchCount(): Int {
        var result = 1
        context.dataStore.edit {
            result = (it[launchCountKey] ?: 0) + 1
            it[launchCountKey] = result
        }
        return result
    }
}
