package com.transparency.fxlens

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transparency.fxlens.data.FxRates
import com.transparency.fxlens.domain.CreateMode
import com.transparency.fxlens.domain.ListItem
import com.transparency.fxlens.domain.PickerSlot
import com.transparency.fxlens.domain.ScanPhase
import com.transparency.fxlens.domain.TravelList
import com.transparency.fxlens.domain.convert
import com.transparency.fxlens.scan.Detection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Anfrage für das „Neue Liste“-Sheet; `itemLabel` benennt die erste Position (ADD-Flow). */
data class CreateRequest(val mode: CreateMode, val currency: String, val itemLabel: String? = null)

/**
 * Zentraler App-State (Session-State nach Handoff §7) + Aktionen.
 * Persistenz: Pins/Onboarded über DataStore, Listen über Room, Kurse mit Datei-Cache.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FxLensApp).container
    private val prefs = container.prefs
    private val listsRepo = container.listsRepository
    private val ratesRepo = container.ratesRepository

    // ---------- persistenter State ----------
    val rates: StateFlow<FxRates> = ratesRepo.rates

    /** Alle Codes der Live-API (alphabetisch, ohne ausgeblendete) — Onboarding, Picker, Listen (§8). */
    val allCodes: StateFlow<List<String>> = ratesRepo.rates
        .map { visibleCodes(it.rates.keys) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, visibleCodes(ratesRepo.rates.value.rates.keys))

    /** null = noch nicht geladen (kein Onboarding-Flackern beim Start). */
    val onboarded: StateFlow<Boolean?> = prefs.onboarded
        .map { v -> v as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pins: StateFlow<List<String>> = prefs.pins
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.transparency.fxlens.data.CurrencyMeta.DEFAULT_PINNED)

    /** Zuletzt genutzte Währungen (neueste zuerst) — Vorschlagsreihenfolge bei Listenanlage (§6). */
    val recents: StateFlow<List<String>> = prefs.recents
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lists: StateFlow<List<TravelList>> = listsRepo.observeLists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- Session-State (nicht persistent, §7) ----------
    var from by mutableStateOf("EUR")
        private set
    var to by mutableStateOf("USD")
        private set
    var scanPhase by mutableStateOf<ScanPhase>(ScanPhase.Scanning)
        private set
    var picker by mutableStateOf<PickerSlot?>(null)
        private set
    var addOpen by mutableStateOf(false)
        private set
    var creating by mutableStateOf<CreateRequest?>(null)
        private set
    var panelOpen by mutableStateOf(false)
        private set
    var selectedListId by mutableStateOf<String?>(null)
        private set
    var editingListId by mutableStateOf<String?>(null)
        private set
    var editingItem by mutableStateOf<EditItemTarget?>(null)
        private set

    /** Akkumulierter Drehwinkel des Swap-Buttons (+180° je Tap, §11). */
    var swapAngle by mutableStateOf(0f)
        private set

    var toastMsg by mutableStateOf("")
        private set
    var toastVisible by mutableStateOf(false)
        private set
    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            // Seed nur beim allerersten Start — ein bewusst geleerter Stand bleibt leer (§6.1).
            if (!prefs.seeded.first()) {
                listsRepo.seedIfEmpty(rates.value.rates)
                prefs.setSeeded()
            }
        }
        // Kurse beim Start und danach periodisch aktualisieren (§8).
        viewModelScope.launch {
            while (true) {
                ratesRepo.refresh()
                delay(15 * 60 * 1000L)
            }
        }
    }

    // ---------- Onboarding ----------
    fun completeOnboarding(selection: List<String>) {
        viewModelScope.launch {
            prefs.setPins(selection)
            prefs.setOnboarded(true)
        }
    }

    fun togglePin(code: String) {
        viewModelScope.launch {
            val p = pins.value
            val next = if (code in p) p - code else if (p.size >= 4) p else p + code
            prefs.setPins(next)
        }
    }

    // ---------- Währungswahl ----------
    fun openPicker(slot: PickerSlot) {
        picker = slot
    }

    fun closePicker() {
        picker = null
    }

    /** Zeilen-Tap im Picker: wählen; wäre das Paar gleich, werden beide getauscht (§7). */
    fun choose(code: String) {
        when (picker) {
            PickerSlot.FROM -> {
                val oldFrom = from
                from = code
                if (code == to) to = oldFrom
            }
            PickerSlot.TO -> {
                val oldTo = to
                to = code
                if (code == from) from = oldTo
            }
            null -> return
        }
        viewModelScope.launch { prefs.addRecent(code) }
        picker = null
        rescan()
    }

    fun swap() {
        swapAngle += 180f
        val f = from
        from = to
        to = f
        rescan()
    }

    // ---------- Scan-State-Maschine (§12) ----------
    // Rollierendes Zeitfenster aus Analyzer-Frames (jeweils die im Rahmen erkannten
    // Zahlen). Jede Zahl wird EINZELN bestätigt: erscheint sie über mehrere Frames
    // hinweg stabil, gilt sie als gesichert. Gelockt wird, sobald ALLE aktuell
    // erkannten Zahlen gesichert sind — so rasten auch mehrere Zahlen gleichzeitig
    // ein (das alte „eine dominante Zahl"-Kriterium scheiterte bei 2+ Zahlen).
    private val frames = ArrayDeque<Pair<List<Detection>, Long>>()

    fun rescan() {
        scanPhase = ScanPhase.Scanning
        frames.clear()
    }

    /** Vom Kamera-Analyzer (Main-Thread): die aktuell im Rahmen erkannten Zahlen. */
    fun onAnalyzerValues(dets: List<Detection>) {
        if (scanPhase is ScanPhase.Locked) return
        val now = SystemClock.elapsedRealtime()
        while (frames.isNotEmpty() && now - frames.first().second > LOCK_WINDOW_MS) frames.removeFirst()
        frames.addLast(dets to now)
        if (dets.isEmpty()) return

        // Bestätige jede Zahl über ZUSAMMENHÄNGENDE Präsenz in den jüngsten Frames:
        // vom neuesten rückwärts bis zur ersten Lücke zählen. So rastet ein Flackern
        // (Wert nur in einem Bruchteil der Frames mit Lücken) nicht ein, eine ruhig
        // gehaltene Zahl dagegen nach ~480 ms ununterbrochener Präsenz.
        val confirmed = dets.filter { d ->
            var run = 0
            var oldestT = now
            for (i in frames.indices.reversed()) {
                val (fd, t) = frames[i]
                if (fd.any { similar(it.value, d.value) }) {
                    run++
                    oldestT = t
                } else break
            }
            run >= LOCK_MIN_COUNT && now - oldestT >= LOCK_MIN_SPAN_MS
        }
        // Erst locken, wenn das Bild ruhig ist (alle erkannten Zahlen gesichert).
        if (confirmed.isNotEmpty() && confirmed.size == dets.size) {
            scanPhase = ScanPhase.Locked(confirmed.map { it.value })
        }
    }

    private fun similar(a: Double, b: Double): Boolean {
        val scale = kotlin.math.max(kotlin.math.abs(a), kotlin.math.abs(b)).coerceAtLeast(1e-6)
        return kotlin.math.abs(a - b) <= CLUSTER_TOL * scale
    }

    // ---------- Sheets & Panel ----------
    /** Welcher erkannte Rohwert gerade hinzugefügt wird (eine der gestapelten Zeilen). */
    var addRaw by mutableStateOf<Double?>(null)
        private set

    fun openAdd(raw: Double) {
        if (scanPhase is ScanPhase.Locked) {
            addRaw = raw
            addOpen = true
        }
    }

    fun closeAdd() {
        addOpen = false
    }

    fun openPanel() {
        selectedListId = null
        panelOpen = true
    }

    fun closePanel() {
        panelOpen = false
        selectedListId = null
    }

    fun selectList(id: String?) {
        selectedListId = id
    }

    fun startCreate(mode: CreateMode, itemLabel: String? = null) {
        if (mode == CreateMode.ADD) addOpen = false
        creating = CreateRequest(mode, to, itemLabel?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun cancelCreate() {
        creating = null
    }

    fun startEdit(listId: String) {
        editingListId = listId
    }

    fun cancelEdit() {
        editingListId = null
    }

    fun startEditItem(listId: String, itemId: String) {
        editingItem = EditItemTarget(listId, itemId)
    }

    fun cancelEditItem() {
        editingItem = null
    }

    // ---------- Listen-Aktionen ----------
    /** Aktuell gescannte Position (optional benannt) zu bestehender Liste hinzufügen (Screen 4). */
    fun addToExisting(listId: String, label: String? = null) {
        val raw = addRaw ?: return
        val list = lists.value.find { it.id == listId } ?: return
        val item = ListItem(
            id = uid(), raw = raw, from = from,
            value = convert(raw, from, list.currency, rates.value.rates),
            ts = System.currentTimeMillis(),
            label = label?.trim()?.takeIf { it.isNotEmpty() },
        )
        viewModelScope.launch {
            listsRepo.addItem(listId, item)
            addOpen = false
            showToast("Zu „${list.name}“ hinzugefügt")
        }
    }

    /** „Neue Liste“-Sheet bestätigt (Screen 5); benennt die erste Position aus dem ADD-Flow. */
    fun doCreate(name: String, currency: String, budget: Double?) {
        val req = creating ?: return
        viewModelScope.launch {
            if (req.mode == CreateMode.ADD) {
                val raw = addRaw
                val first = raw?.let {
                    ListItem(
                        id = uid(), raw = it, from = from,
                        value = convert(it, from, currency, rates.value.rates),
                        ts = System.currentTimeMillis(),
                        label = req.itemLabel,
                    )
                }
                listsRepo.createList(name, currency, budget, first)
                showToast("Zu „$name“ hinzugefügt")
            } else {
                val id = listsRepo.createList(name, currency, budget)
                selectedListId = id
                panelOpen = true
            }
            prefs.addRecent(currency)
            creating = null
            addOpen = false
        }
    }

    /** Position umbenennen (Screen 7, Inline-Edit der Liste). */
    fun saveItemLabel(label: String?) {
        val target = editingItem ?: return
        viewModelScope.launch {
            listsRepo.updateItemLabel(target.itemId, label?.trim()?.takeIf { it.isNotEmpty() })
            editingItem = null
        }
    }

    /** „Liste bearbeiten“ speichern (Screen 7). */
    fun saveListEdit(name: String, budget: Double?) {
        val id = editingListId ?: return
        viewModelScope.launch {
            listsRepo.updateList(id, name, budget)
            editingListId = null
        }
    }

    fun deleteList() {
        val id = editingListId ?: return
        viewModelScope.launch {
            listsRepo.deleteList(id)
            editingListId = null
            selectedListId = null
        }
    }

    fun deleteItem(listId: String, itemId: String) {
        viewModelScope.launch { listsRepo.deleteItem(itemId) }
    }

    // ---------- Toast (Screen 8) ----------
    private fun showToast(msg: String) {
        toastJob?.cancel()
        toastMsg = msg
        toastVisible = true
        toastJob = viewModelScope.launch {
            delay(2200)
            toastVisible = false
        }
    }

    private fun uid(): String = UUID.randomUUID().toString().take(8)

    private companion object {
        const val LOCK_WINDOW_MS = 1100L   // Zeitfenster der Frame-Historie
        const val LOCK_MIN_COUNT = 3       // Mindestanzahl Frames, in denen die Zahl vorkommt
        const val LOCK_MIN_SPAN_MS = 480L  // Zahl muss so lange durchgehend präsent sein
        const val CLUSTER_TOL = 0.012      // relative Toleranz (±1,2 %) für „gleiche“ Werte
    }
}

/** Welche Position gerade umbenannt wird (Screen 7). */
data class EditItemTarget(val listId: String, val itemId: String)

/** API-Codes alphabetisch, ohne ausgeblendete Währungen (§9). */
private fun visibleCodes(codes: Set<String>): List<String> =
    codes.filterNot { it in com.transparency.fxlens.data.CurrencyMeta.HIDDEN }.sorted()
