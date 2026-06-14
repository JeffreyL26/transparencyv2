package com.jbateam.scanconvert.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live-Kurse (Handoff §8): open.er-api.com, EUR-Basis, ~166 Währungen.
 * Caching auf Platte; Offline-Fallback = letzter Stand, sonst Demo-Kurse aus §6.
 */
data class FxRates(
    /** Kurs je Code relativ zu EUR. */
    val rates: Map<String, Double>,
    /** Zeitstempel des letzten erfolgreichen Updates (ms, 0 = Demo-Daten). */
    val updatedAt: Long,
    /** true = frisch von der API geladen (diese Session). */
    val live: Boolean,
)

/** Platzhalter-Kurse aus dem Handoff §6 — nur Fallback ohne Netz und ohne Cache. */
val DEMO_RATES: Map<String, Double> = mapOf(
    "EUR" to 1.0, "USD" to 1.0850, "GBP" to 0.8520, "CHF" to 0.9450,
    "JPY" to 172.0, "AUD" to 1.6300, "CAD" to 1.4750, "CNY" to 7.7400,
    "INR" to 90.50, "BRL" to 5.9200, "SEK" to 11.420, "NOK" to 11.680,
    "MXN" to 19.850, "ZAR" to 19.420, "SGD" to 1.4350, "HKD" to 8.4500,
)

class RatesRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "fx_rates_cache.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _rates = MutableStateFlow(loadCacheOrDemo())
    val rates: StateFlow<FxRates> = _rates.asStateFlow()

    private fun loadCacheOrDemo(): FxRates {
        runCatching {
            if (cacheFile.exists()) {
                val obj = json.parseToJsonElement(cacheFile.readText()).jsonObject
                val ts = obj["updatedAt"]!!.jsonPrimitive.long
                val map = obj["rates"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }
                if (map.isNotEmpty()) return FxRates(map, ts, live = false)
            }
        }
        return FxRates(DEMO_RATES, 0L, live = false)
    }

    /**
     * Holt frische Kurse; bei Erfolg live=true mit neuem Zeitstempel. Bei Fehler
     * (offline) bleibt der letzte Stand erhalten, wird aber als nicht-live markiert,
     * damit die UI Datum/Uhrzeit des letzten Stands statt „LIVE" zeigt (§1/§3).
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://open.er-api.com/v6/latest/EUR").openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val obj = json.parseToJsonElement(body).jsonObject
            check(obj["result"]?.jsonPrimitive?.content == "success") { "API result != success" }
            val rates = obj["rates"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }
            check(rates.isNotEmpty() && rates["EUR"] != null)

            val updatedAt = System.currentTimeMillis()
            _rates.value = FxRates(rates, updatedAt, live = true)
            persist(rates, updatedAt)
            true
        }.getOrElse {
            markOffline()
            false
        }
    }

    /** Verbindung verloren: letzten Kurs behalten, aber nicht mehr als „live" zeigen. */
    fun markOffline() {
        if (_rates.value.live) _rates.value = _rates.value.copy(live = false)
    }

    private fun persist(rates: Map<String, Double>, updatedAt: Long) {
        runCatching {
            val ratesJson = rates.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
            cacheFile.writeText("{\"updatedAt\":$updatedAt,\"rates\":{$ratesJson}}")
        }
    }
}
