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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Anfrage für das „Neue Liste“-Sheet. */
data class CreateRequest(val mode: CreateMode, val currency: String)

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

    /** Alle Codes der Live-API (alphabetisch) — Onboarding-Grid, Picker, Listen (§8). */
    val allCodes: StateFlow<List<String>> = ratesRepo.rates
        .map { it.rates.keys.sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ratesRepo.rates.value.rates.keys.sorted())

    /** null = noch nicht geladen (kein Onboarding-Flackern beim Start). */
    val onboarded: StateFlow<Boolean?> = prefs.onboarded
        .map { v -> v as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pins: StateFlow<List<String>> = prefs.pins
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.transparency.fxlens.data.CurrencyMeta.DEFAULT_PINNED)

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
    private var candidate: Double? = null
    private var candidateSince = 0L
    private var lastSeenAt = 0L

    fun rescan() {
        scanPhase = ScanPhase.Scanning
        candidate = null
    }

    /**
     * Vom Kamera-Analyzer (Main-Thread): aktuell stabilste Zahl im ROI oder null.
     * Wird eine konsistente Zahl über ~1 s gehalten → locked.
     */
    fun onAnalyzerValue(value: Double?) {
        if (scanPhase is ScanPhase.Locked) return
        val now = SystemClock.elapsedRealtime()
        if (value == null) {
            if (now - lastSeenAt > 600) candidate = null
            return
        }
        lastSeenAt = now
        if (candidate != null && candidate == value) {
            if (now - candidateSince >= 1000) {
                scanPhase = ScanPhase.Locked(value)
            }
        } else {
            candidate = value
            candidateSince = now
        }
    }

    // ---------- Sheets & Panel ----------
    fun openAdd() {
        if (scanPhase is ScanPhase.Locked) addOpen = true
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

    fun startCreate(mode: CreateMode) {
        if (mode == CreateMode.ADD) addOpen = false
        creating = CreateRequest(mode, to)
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

    // ---------- Listen-Aktionen ----------
    private fun lockedRaw(): Double? = (scanPhase as? ScanPhase.Locked)?.raw

    /** Aktuell gescannte Position zu bestehender Liste hinzufügen (Screen 4). */
    fun addToExisting(listId: String) {
        val raw = lockedRaw() ?: return
        val list = lists.value.find { it.id == listId } ?: return
        val item = ListItem(
            id = uid(), raw = raw, from = from,
            value = convert(raw, from, list.currency, rates.value.rates),
            ts = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            listsRepo.addItem(listId, item)
            addOpen = false
            showToast("Zu „${list.name}“ hinzugefügt")
        }
    }

    /** „Neue Liste“-Sheet bestätigt (Screen 5). */
    fun doCreate(name: String, currency: String, budget: Double?) {
        val req = creating ?: return
        viewModelScope.launch {
            if (req.mode == CreateMode.ADD) {
                val raw = lockedRaw()
                val first = raw?.let {
                    ListItem(
                        id = uid(), raw = it, from = from,
                        value = convert(it, from, currency, rates.value.rates),
                        ts = System.currentTimeMillis(),
                    )
                }
                listsRepo.createList(name, currency, budget, first)
                showToast("Zu „$name“ hinzugefügt")
            } else {
                val id = listsRepo.createList(name, currency, budget)
                selectedListId = id
                panelOpen = true
            }
            creating = null
            addOpen = false
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
}
