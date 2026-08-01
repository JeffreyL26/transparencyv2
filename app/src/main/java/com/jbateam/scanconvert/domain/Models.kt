package com.jbateam.scanconvert.domain

/** Position einer Liste — `value` ist zum Scan-Zeitpunkt fixiert (§7). */
data class ListItem(
    val id: String,
    val raw: Double,
    val from: String,
    val value: Double,
    val ts: Long,
    /** Optionaler Name der Position (z. B. „Hotel", „Abendessen"). */
    val label: String? = null,
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

/**
 * Kontext des „Neue Liste“-Sheets: aus dem Add-Flow (Währung fix), aus dem Panel
 * oder aus dem Foto-Scan „Alle hinzufügen“-Flow (Währung fix, alle Overlays als Positionen).
 */
enum class CreateMode { ADD, PANEL, ADD_ALL }

/** Scan-State-Maschine (§12): scanning → locked. `raws` = erkannte Zahlen in Lesereihenfolge. */
sealed interface ScanPhase {
    data object Scanning : ScanPhase
    data class Locked(val raws: List<Double>) : ScanPhase
}
