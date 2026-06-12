package com.transparency.fxlens.domain

/** Position einer Liste — `value` ist zum Scan-Zeitpunkt fixiert (§7). */
data class ListItem(
    val id: String,
    val raw: Double,
    val from: String,
    val value: Double,
    val ts: Long,
)

/** Reise-Budget-Liste, an genau eine Zielwährung gebunden (§7). */
data class TravelList(
    val id: String,
    val name: String,
    val currency: String,
    val budget: Double?,
    val items: List<ListItem>,
)

/** sumList(list) = Σ items.value */
fun TravelList.total(): Double = items.sumOf { it.value }

/** Welcher Chip im Glas-Menü den Picker geöffnet hat. */
enum class PickerSlot { FROM, TO }

/** Kontext des „Neue Liste“-Sheets: aus dem Add-Flow (Währung fix) oder aus dem Panel. */
enum class CreateMode { ADD, PANEL }

/** Scan-State-Maschine (§12): scanning → locked. */
sealed interface ScanPhase {
    data object Scanning : ScanPhase
    data class Locked(val raw: Double) : ScanPhase
}
