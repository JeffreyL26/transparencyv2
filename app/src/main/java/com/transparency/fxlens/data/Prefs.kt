package com.transparency.fxlens.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistente Keys (Handoff §7): Pins (max 4, Reihenfolge = Pin-Reihenfolge)
 * und Onboarding-Flag — via DataStore.
 */
private val Context.dataStore by preferencesDataStore(name = "fxlens")

class Prefs(private val context: Context) {

    private val pinsKey = stringPreferencesKey("fxlens_pins")
    private val onboardedKey = booleanPreferencesKey("fxlens_onboarded")
    private val seededKey = booleanPreferencesKey("fxlens_seeded")

    val pins: Flow<List<String>> = context.dataStore.data.map { p ->
        p[pinsKey]?.split(",")?.filter { it.isNotBlank() } ?: CurrencyMeta.DEFAULT_PINNED
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
