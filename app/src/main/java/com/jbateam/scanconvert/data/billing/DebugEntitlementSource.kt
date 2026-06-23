package com.jbateam.scanconvert.data.billing

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Eigener Debug-DataStore (CLAUDE.md §13.2): bewusst getrennt vom Produktiv-Store
 * „fxlens" aus [com.jbateam.scanconvert.data.Prefs] — der Override-Zustand soll den
 * echten Entitlement-Cache nie berühren.
 */
private val Context.devDataStore by preferencesDataStore(name = "scanconvert_dev")

/**
 * Lokaler Override-Zustand des Dev-Sheets (CLAUDE.md §13.2).
 *
 * [active] = false → es gilt der echte Flow des [BillingRepository] (kein Override).
 * Sobald ein Schalter geschrieben wird, ist [active] = true und die drei Flags bzw.
 * der zeitbasierte [vacationPassUntil] bestimmen die Entitlements.
 */
data class DevOverride(
    val active: Boolean = false,
    val adFree: Boolean = false,
    val unlimitedLists: Boolean = false,
    val listExport: Boolean = false,
    /** Lokaler Ablauf des Test-„Vacation-Pass" (epoch ms, 0 = keiner). */
    val vacationPassUntil: Long = 0L,
)

/**
 * Debug-Entitlement-Naht (CLAUDE.md §13.2) — NUR im Debug-Build instanziiert
 * (Auswahl im AppContainer hinter `BuildConfig.DEBUG`). Sie kapselt das
 * [BillingRepository] (= Produktivquelle, unverändert) und überschreibt allein den
 * [entitlements]-Flow mit einem lokalen Override aus dem [devDataStore].
 *
 * So lassen sich die kaufpflichtigen Features (Werbefrei §5/§7.4, unbegrenzte
 * Listen §4, Export §7.2) ohne Play Console / License-Tester live durchschalten.
 * Ist KEIN Override gesetzt ([DevOverride.active] = false), reicht die Naht den
 * echten Flow des [BillingRepository] unverändert durch ([combine]). [products]
 * werden immer direkt durchgereicht — Preise kommen ausschließlich von Google (§11).
 *
 * Release-Verhalten bleibt unberührt: diese Klasse wird dort nie erzeugt (§13.2).
 */
class DebugEntitlementSource(
    context: Context,
    private val billing: BillingRepository,
    scope: CoroutineScope,
) : EntitlementSource {

    private val appContext = context.applicationContext

    private val activeKey = booleanPreferencesKey("ov_active")
    private val adFreeKey = booleanPreferencesKey("ov_adfree")
    private val unlimitedKey = booleanPreferencesKey("ov_unlimited")
    private val exportKey = booleanPreferencesKey("ov_export")
    private val vacationKey = longPreferencesKey("ov_vacation_until")

    /** Aktueller Override-Zustand für das Dev-Sheet (Schalterstellungen). */
    val override: Flow<DevOverride> = appContext.devDataStore.data.map { p ->
        DevOverride(
            active = p[activeKey] ?: false,
            adFree = p[adFreeKey] ?: false,
            unlimitedLists = p[unlimitedKey] ?: false,
            listExport = p[exportKey] ?: false,
            vacationPassUntil = p[vacationKey] ?: 0L,
        )
    }

    /** Produkte 1:1 durchreichen (§11/§13.2). */
    override val products: StateFlow<List<ProductInfo>> = billing.products

    /**
     * Entitlements: kein Override → echter Flow des [BillingRepository]; aktiver
     * Override → lokal abgeleitete Flags. Der „Vacation-Pass" wirkt wie in der
     * Produktivquelle zeitbasiert auf `adFree` (§3) — der Ablauf wird beim nächsten
     * Emit neu bewertet (für ein Debug-Werkzeug ausreichend).
     */
    override val entitlements: StateFlow<Entitlements> =
        combine(billing.entitlements, override) { real, ov ->
            if (!ov.active) {
                real
            } else {
                Entitlements(
                    adFree = ov.adFree || ov.vacationPassUntil > System.currentTimeMillis(),
                    unlimitedLists = ov.unlimitedLists,
                    listExport = ov.listExport,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, billing.entitlements.value)

    // ---------- Setter (vom Dev-Sheet) — jeder Schreibzugriff aktiviert den Override ----------

    suspend fun setAdFree(value: Boolean) = write { it[adFreeKey] = value }
    suspend fun setUnlimited(value: Boolean) = write { it[unlimitedKey] = value }
    suspend fun setExport(value: Boolean) = write { it[exportKey] = value }

    /** Test-„Vacation-Pass" auf 7 Tage setzen (§3) → schaltet `adFree` zeitbasiert frei. */
    suspend fun grantVacationPass(durationMs: Long = Products.VACATION_PASS_DURATION_MS) =
        write { it[vacationKey] = System.currentTimeMillis() + durationMs }

    /** „Override aus": kompletten Debug-Zustand löschen → zurück zur echten Quelle (§13.2). */
    suspend fun clearOverride() {
        appContext.devDataStore.edit { it.clear() }
    }

    private suspend fun write(block: (MutablePreferences) -> Unit) {
        appContext.devDataStore.edit {
            block(it)
            it[activeKey] = true
        }
    }
}
