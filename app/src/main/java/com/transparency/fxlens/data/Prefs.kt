package com.transparency.fxlens.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistente Keys (Handoff §7): Pins (max 4, Reihenfolge = Pin-Reihenfolge)
 * und Onboarding-Flag — via DataStore.
 */
private val Context.dataStore by preferencesDataStore(name = "fxlens")

class Prefs(private val context: Context) {

    private val pinsKey = stringPreferencesKey("fxlens_pins")
    private val onboardedKey = booleanPreferencesKey("fxlens_onboarded")
    private val seededKey = booleanPreferencesKey("fxlens_seeded")
    private val recentsKey = stringPreferencesKey("fxlens_recents")
    private val customsKey = stringPreferencesKey("fxlens_customs")
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
}
