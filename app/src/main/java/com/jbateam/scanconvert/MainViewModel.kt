package com.jbateam.scanconvert

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.data.CustomCurrency
import com.jbateam.scanconvert.data.FxRates
import com.jbateam.scanconvert.data.LocaleStore
import com.jbateam.scanconvert.data.ads.AdsInitializer
import com.jbateam.scanconvert.data.billing.Entitlements
import com.jbateam.scanconvert.data.billing.PaywallContext
import com.jbateam.scanconvert.data.billing.ProductInfo
import com.jbateam.scanconvert.data.export.PdfExporter
import com.google.android.gms.ads.nativead.NativeAd
import com.jbateam.scanconvert.domain.CreateMode
import com.jbateam.scanconvert.domain.ListItem
import com.jbateam.scanconvert.domain.PickerSlot
import com.jbateam.scanconvert.domain.ScanPhase
import com.jbateam.scanconvert.domain.TravelList
import com.jbateam.scanconvert.domain.convert
import com.jbateam.scanconvert.domain.total
import com.jbateam.scanconvert.scan.Detection
import com.jbateam.scanconvert.scan.PhotoDetection
import com.jbateam.scanconvert.scan.PhotoScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Anfrage für das „Neue Liste“-Sheet; `itemLabel` benennt die erste Position (ADD-Flow). */
data class CreateRequest(val mode: CreateMode, val currency: String, val itemLabel: String? = null)

/**
 * Zentraler App-State (Session-State nach Handoff §7) + Aktionen.
 * Persistenz: Pins/Onboarded über DataStore, Listen über Room, Kurse mit Datei-Cache.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as ScanConvertApp).container
    private val prefs = container.prefs
    private val listsRepo = container.listsRepository
    private val ratesRepo = container.ratesRepository
    private val billingRepo = container.billingRepository
    // Lese-Quelle für Entitlements/Produkte (§13.2): in Release == billingRepo,
    // im Debug-Build die DebugEntitlementSource (lokaler Override). Käufe/Restore
    // laufen weiterhin direkt über billingRepo (echte Play-Aktionen).
    private val entitlementsSource = container.entitlementsSource

    // ---------- persistenter State ----------
    /** Vom Nutzer angelegte Custom-Währungen (§F2). */
    val customs: StateFlow<List<CustomCurrency>> = prefs.customs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Live-Kurse inkl. Custom-Währungen. Jede Custom-Währung wird an ihre
     * Referenzwährung gekoppelt (Kurs = rate[ref] · perRef) und folgt damit
     * deren Live-Kurs. Nebenbei wird die CurrencyMeta-Registry aktualisiert.
     */
    val rates: StateFlow<FxRates> = combine(ratesRepo.rates, prefs.customs) { fx, cs ->
        CurrencyMeta.setCustoms(cs)
        mergeCustoms(fx, cs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ratesRepo.rates.value)

    /** Alle Codes (alphabetisch, ohne ausgeblendete) inkl. Custom — Picker, Listen (§8). */
    val allCodes: StateFlow<List<String>> = rates
        .map { visibleCodes(it.rates.keys) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, visibleCodes(ratesRepo.rates.value.rates.keys))

    /** null = noch nicht geladen (kein Onboarding-Flackern beim Start). */
    val onboarded: StateFlow<Boolean?> = prefs.onboarded
        .map { v -> v as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pins: StateFlow<List<String>> = prefs.pins
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.jbateam.scanconvert.data.CurrencyMeta.DEFAULT_PINNED)

    /** Zuletzt genutzte Währungen (neueste zuerst) — Vorschlagsreihenfolge bei Listenanlage (§6). */
    val recents: StateFlow<List<String>> = prefs.recents
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lists: StateFlow<List<TravelList>> = listsRepo.observeLists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- Monetarisierung (§2/§6.3) ----------
    /** Abgeleitete Entitlements (Source of Truth = Play, Cache = sofortige UX; §13.2-Naht). */
    val entitlements: StateFlow<Entitlements> = entitlementsSource.entitlements
        .stateIn(viewModelScope, SharingStarted.Eagerly, Entitlements())

    /** Produkte inkl. von Google geliefertem `formattedPrice` (nie hartkodiert, §11). */
    val products: StateFlow<List<ProductInfo>> = entitlementsSource.products
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Free: max. 3 Listen; mit unlimitedLists unbegrenzt (§4). */
    val canCreateList: StateFlow<Boolean> =
        combine(lists, entitlements) { ls, e -> e.unlimitedLists || ls.size < Entitlements.FREE_LIST_LIMIT }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Offener Paywall-Kontext (null = geschlossen). */
    var paywallOpen by mutableStateOf<PaywallContext?>(null)
        private set
    /** Offenes Einstellungen-Sheet. */
    var settingsOpen by mutableStateOf(false)
        private set

    fun openPaywall(ctx: PaywallContext) { paywallOpen = ctx }
    fun closePaywall() { paywallOpen = null }
    fun openSettings() { settingsOpen = true }
    fun closeSettings() { settingsOpen = false }

    fun purchase(activity: Activity, productId: String) = billingRepo.launchPurchase(activity, productId)
    fun restorePurchases() {
        viewModelScope.launch { billingRepo.restore() }
        showToast(str(R.string.restore_started))
    }

    // ---------- Werbung & Consent (§5/§8/§11) ----------
    /** Aktuell geladene Native-Ad (oder null) — die UI rendert nur, wenn !adFree. */
    val nativeAd: StateFlow<NativeAd?> = container.nativeAdLoader.nativeAd
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** UMP verlangt einen Privacy-Options-Eintrag im SettingsSheet (nach Consent gesetzt). */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    /**
     * Wird von [MainActivity] nach dem Consent-Flow aufgerufen. Lädt Ads NUR, wenn
     * Consent erlaubt, der Nutzer nicht werbefrei ist und es nicht die erste Session
     * ist (§5/§11). Sonst passiert nichts (kein SDK-Init, kein Ad-Load).
     */
    fun onConsentResolved(canRequestAds: Boolean, firstSession: Boolean) {
        privacyOptionsRequired = container.consentManager.isPrivacyOptionsRequired
        if (!canRequestAds || entitlements.value.adFree || firstSession) return
        val app = getApplication<Application>()
        AdsInitializer.ensureInitialized(app, viewModelScope) {
            container.nativeAdLoader.load()
            container.rewardedAdManager.load()
        }
    }

    fun showPrivacyOptions(activity: Activity) = container.consentManager.showPrivacyOptions(activity)

    /** Rewarded als Gratis-Alternative zum Export-Kauf (§5/Phase 6). */
    fun watchAdToExport(activity: Activity, listId: String) {
        container.rewardedAdManager.show(activity) { exportList(listId, force = true) }
    }

    init {
        // adFree gekauft → laufende Native-Ad verwerfen, nichts mehr laden (§11).
        viewModelScope.launch {
            entitlements.collect { e -> if (e.adFree) container.nativeAdLoader.clear() }
        }
    }

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
    /** „Custom-Kurs anlegen"-Sheet offen (über dem Picker, §F2). */
    var customOpen by mutableStateOf(false)
        private set

    /** Akkumulierter Drehwinkel des Swap-Buttons (+180° je Tap, §11). */
    var swapAngle by mutableStateOf(0f)
        private set

    var toastMsg by mutableStateOf("")
        private set
    var toastVisible by mutableStateOf(false)
        private set
    private var toastJob: Job? = null

    // Connectivity-Überwachung (vor init deklariert, da init sie registriert).
    private val connectivityManager =
        app.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { ratesRepo.refresh() }
        }
        override fun onLost(network: Network) {
            ratesRepo.markOffline()
        }
        // „Verbunden, aber kein Internet" (z. B. Captive Portal): sofort als offline
        // markieren bzw. bei Validierung aktualisieren — ohne den 15-min-Poll abzuwarten.
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                if (!ratesRepo.rates.value.live) viewModelScope.launch { ratesRepo.refresh() }
            } else {
                ratesRepo.markOffline()
            }
        }
    }

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
        // Sofort auf Verbindungswechsel reagieren: online → Kurs aktualisieren (§3),
        // offline → letzten Stand mit Datum/Uhrzeit zeigen statt „LIVE" (§1).
        runCatching { connectivityManager?.registerDefaultNetworkCallback(networkCallback) }
    }

    override fun onCleared() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
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

    // ---------- Custom-Währungen (§F2) ----------
    fun openCustom() { customOpen = true }
    fun closeCustom() { customOpen = false }

    /** Legt eine Custom-Währung an (max. 5) und schließt das Sheet. */
    fun addCustom(name: String, abbrev: String?, emoji: String, refCode: String, perRef: Double) {
        viewModelScope.launch {
            val cur = customs.value
            if (cur.size >= 5 || perRef <= 0.0 || name.isBlank() || emoji.isBlank()) return@launch
            val code = makeCustomCode(abbrev, cur)
            prefs.setCustoms(cur + CustomCurrency(code, name.trim(), emoji.trim(), refCode, perRef))
            customOpen = false
        }
    }

    fun deleteCustom(code: String) {
        viewModelScope.launch { prefs.setCustoms(customs.value.filterNot { it.code == code }) }
    }

    /** Eindeutiger Code: bereinigte Abkürzung, sonst „C1"…; kollidiert nie mit echten Codes. */
    private fun makeCustomCode(abbrev: String?, existing: List<CustomCurrency>): String {
        val taken = existing.map { it.code }.toSet() + rates.value.rates.keys
        val a = abbrev?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }?.take(6)
        if (!a.isNullOrBlank() && a !in taken) return a
        for (i in 1..99) {
            val g = "C$i"
            if (g !in taken) return g
        }
        return "C" + UUID.randomUUID().toString().take(4).uppercase()
    }

    private fun mergeCustoms(fx: FxRates, cs: List<CustomCurrency>): FxRates {
        if (cs.isEmpty()) return fx
        val merged = fx.rates.toMutableMap()
        for (c in cs) {
            val ref = fx.rates[c.refCode]
            if (ref != null && c.perRef > 0.0) merged[c.code] = ref * c.perRef
        }
        return fx.copy(rates = merged)
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

    // ---------- Galerie-Foto-Scan (§Galerie) ----------
    /** In-App-Galerie sichtbar (Alben + Foto-Grid). */
    var galleryOpen by mutableStateOf(false)
        private set

    /** Laufender/abgeschlossener Foto-Scan (null = kein Foto geöffnet). */
    var photoScan by mutableStateOf<PhotoScanUi?>(null)
        private set

    private var photoScanJob: Job? = null

    fun openGallery() {
        galleryOpen = true
    }

    fun closeGallery() {
        photoScanJob?.cancel()
        photoScan = null
        galleryOpen = false
    }

    /** Foto gewählt: anzeigen und im Hintergrund scannen (Bitmap + OCR + Preise). */
    fun openPhoto(uri: Uri) {
        photoScanJob?.cancel()
        photoScan = PhotoScanUi(uri = uri, status = PhotoScanStatus.LOADING)
        photoScanJob = viewModelScope.launch {
            val result = runCatching { PhotoScanner.scan(getApplication(), uri) }
            // Nur übernehmen, wenn der Nutzer nicht längst ein anderes Foto geöffnet hat.
            if (photoScan?.uri != uri) return@launch
            result.fold(
                onSuccess = { r ->
                    photoScan = PhotoScanUi(
                        uri = uri,
                        bitmap = r.bitmap,
                        detections = r.detections,
                        status = PhotoScanStatus.DONE,
                    )
                },
                onFailure = {
                    photoScan = PhotoScanUi(uri = uri, status = PhotoScanStatus.ERROR)
                },
            )
        }
    }

    /** Zurück zur Galerie (Foto-Screen schließen). */
    fun closePhoto() {
        photoScanJob?.cancel()
        photoScan = null
    }

    /** Overlay entfernen — die Zahl soll nicht übernommen werden. */
    fun removePhotoDetection(id: Int) {
        val s = photoScan ?: return
        photoScan = s.copy(detections = s.detections.filterNot { it.id == id })
    }

    /** Falsch erkannten Rohwert eines Overlays händisch korrigieren. */
    fun setPhotoDetectionValue(id: Int, value: Double) {
        val s = photoScan ?: return
        photoScan = s.copy(
            detections = s.detections.map { if (it.id == id) it.copy(value = value) else it }
        )
    }

    /** Add-Flow aus dem Foto-Modus — ohne das Kamera-Lock-Gate von [openAdd]. */
    fun openAddFromPhoto(raw: Double) {
        addRaw = raw
        addOpen = true
    }

    /** „Alle zu Liste hinzufügen“-Sheet. */
    var addAllOpen by mutableStateOf(false)
        private set

    fun openAddAll() {
        if (photoScan?.detections?.isNotEmpty() == true) addAllOpen = true
    }

    fun closeAddAll() {
        addAllOpen = false
    }

    /** Alle verbliebenen Overlays als Positionen in eine bestehende Liste übernehmen. */
    fun addAllToExisting(listId: String) {
        val dets = photoScan?.detections.orEmpty()
        val list = lists.value.find { it.id == listId } ?: return
        if (dets.isEmpty()) return
        viewModelScope.launch {
            insertDetections(dets, listId, list.currency)
            addAllOpen = false
            showToast(str(R.string.toast_added_n, dets.size, list.name))
        }
    }

    /** Positionen in Erkennungs-Reihenfolge einfügen (ts gestaffelt wie beim Seed). */
    private suspend fun insertDetections(dets: List<PhotoDetection>, listId: String, currency: String) {
        val now = System.currentTimeMillis()
        dets.forEachIndexed { i, d ->
            listsRepo.addItem(
                listId,
                ListItem(
                    id = uid(), raw = d.value, from = from,
                    value = convert(d.value, from, currency, rates.value.rates),
                    ts = now + i,
                ),
            )
        }
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
        if (mode == CreateMode.ADD_ALL) addAllOpen = false
        creating = CreateRequest(mode, to, itemLabel?.trim()?.takeIf { it.isNotEmpty() })
    }

    /**
     * Gegateter Einstieg ins „Neue Liste"-Sheet (§4): bei erreichtem Free-Limit
     * öffnet ein Tap die Paywall statt das Create-Sheet. Gilt an BEIDEN
     * Anlage-Pfaden (Panel + Add-Flow).
     */
    fun requestCreateList(mode: CreateMode, itemLabel: String? = null) {
        if (!canCreateList.value) { openPaywall(PaywallContext.LISTS); return }
        startCreate(mode, itemLabel)
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
            showToast(str(R.string.toast_added_to, list.name))
        }
    }

    /** „Neue Liste“-Sheet bestätigt (Screen 5); benennt die erste Position aus dem ADD-Flow. */
    fun doCreate(name: String, currency: String, budget: Double?) {
        val req = creating ?: return
        viewModelScope.launch {
            when (req.mode) {
                CreateMode.ADD -> {
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
                    showToast(str(R.string.toast_added_to, name))
                }
                // Foto-Scan: alle verbliebenen Overlays als Positionen der neuen Liste.
                CreateMode.ADD_ALL -> {
                    val dets = photoScan?.detections.orEmpty()
                    val id = listsRepo.createList(name, currency, budget)
                    insertDetections(dets, id, currency)
                    addAllOpen = false
                    showToast(str(R.string.toast_added_n, dets.size, name))
                }
                CreateMode.PANEL -> {
                    val id = listsRepo.createList(name, currency, budget)
                    selectedListId = id
                    panelOpen = true
                }
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

    // ---------- Export (§6.4, hinter listExport) ----------
    /** Fertiger Teilen-Intent, den die UI per Activity-Context startet (null = keiner). */
    var pendingShare by mutableStateOf<Intent?>(null)
        private set

    fun consumeShare() { pendingShare = null }

    /**
     * Exportiert eine Liste als A4-PDF (gerendert aus der HTML-Vorlage über eine
     * Offscreen-WebView, [PdfExporter]) und teilt sie über den ACTION_SEND-Chooser —
     * dort erscheinen WhatsApp, Mail, Samsung/Android-Notizen sowie „In Dateien
     * speichern". [force] umgeht das Gate (z. B. nach einem Rewarded-Unlock, §6/Phase 6).
     */
    fun exportList(listId: String, force: Boolean = false) {
        if (!force && !entitlements.value.listExport) { openPaywall(PaywallContext.EXPORT); return }
        val list = lists.value.find { it.id == listId } ?: return
        viewModelScope.launch {
            showToast(str(R.string.export_creating))
            val app = getApplication<Application>()
            val file = withTimeoutOrNull(20_000) {
                PdfExporter.renderListPdf(app, list, exportPdfFile(app, list))
            }
            if (file != null) pendingShare = pdfShareIntent(app, file, list.name)
            else showToast(str(R.string.export_failed))
        }
    }

    /** Zieldatei im Cache: `{Listenname}_{yyyy-MM-dd}.pdf` (FS-unsichere Zeichen ersetzt). */
    private fun exportPdfFile(app: Application, list: TravelList): File {
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val safe = list.name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), "_")
            .take(40)
            .ifBlank { "Liste" }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(dir, "${safe}_$date.pdf")
    }

    private fun pdfShareIntent(app: Application, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            // clipData zusätzlich setzen, damit auch der Vorschau-/Sharesheet-Prozess
            // Leserechte auf die URI erhält (sonst „Permission Denial" beim Preview).
            clipData = ClipData.newUri(app.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ---------- Toast (Screen 8) ----------
    /** Lokalisierter String über einen Context mit der gewählten App-Sprache (§F5). */
    private fun str(resId: Int, vararg args: Any): String =
        LocaleStore.wrap(getApplication<Application>()).getString(resId, *args)

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

/** Zustand des Foto-Scans aus der In-App-Galerie. */
enum class PhotoScanStatus { LOADING, DONE, ERROR }

/**
 * Ein geöffnetes Galerie-Foto samt Scan-Ergebnis: [bitmap] ist das angezeigte Bild,
 * [detections] die verbliebenen Preis-Overlays (entfernte sind herausgefiltert,
 * bearbeitete tragen den korrigierten Rohwert) in dessen Pixelkoordinaten.
 */
data class PhotoScanUi(
    val uri: Uri,
    val bitmap: android.graphics.Bitmap? = null,
    val detections: List<PhotoDetection> = emptyList(),
    val status: PhotoScanStatus = PhotoScanStatus.LOADING,
)

/** API-Codes alphabetisch, ohne ausgeblendete Währungen (§9). */
private fun visibleCodes(codes: Set<String>): List<String> =
    codes.filterNot { it in com.jbateam.scanconvert.data.CurrencyMeta.HIDDEN }.sorted()
