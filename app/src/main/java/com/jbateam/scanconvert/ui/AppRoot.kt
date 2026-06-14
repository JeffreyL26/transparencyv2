package com.jbateam.scanconvert.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jbateam.scanconvert.MainViewModel
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.LocaleStore
import com.jbateam.scanconvert.data.billing.PaywallContext
import com.jbateam.scanconvert.domain.CreateMode
import com.jbateam.scanconvert.domain.ScanPhase
import com.jbateam.scanconvert.scan.CameraScanPreview
import com.jbateam.scanconvert.ui.components.AppToast
import com.jbateam.scanconvert.ui.screens.AddToListSheet
import com.jbateam.scanconvert.ui.screens.CreateListSheet
import com.jbateam.scanconvert.ui.screens.CustomRateSheet
import com.jbateam.scanconvert.ui.screens.EdgeTab
import com.jbateam.scanconvert.ui.screens.EditItemSheet
import com.jbateam.scanconvert.ui.screens.EditListSheet
import com.jbateam.scanconvert.ui.screens.GlassMenu
import com.jbateam.scanconvert.ui.screens.LanguageButton
import com.jbateam.scanconvert.ui.screens.LanguageSheet
import com.jbateam.scanconvert.ui.screens.ListsPanel
import com.jbateam.scanconvert.ui.screens.OnboardingScreen
import com.jbateam.scanconvert.ui.screens.PaywallSheet
import com.jbateam.scanconvert.ui.screens.PickerSheet
import com.jbateam.scanconvert.ui.screens.ScanLayer
import com.jbateam.scanconvert.ui.screens.SettingsSheet
import com.jbateam.scanconvert.ui.theme.FxTheme

/**
 * Schichtung wie im Prototyp (z-Reihenfolge von unten):
 * Kamera → Scan-Layer (Rahmen + Ergebnis-Karte) → Glas-Menü → Edge-Tab →
 * Picker/Add-Sheets (z50) → Listen-Panel (z60) → Create/Edit-Sheets (z65) →
 * Onboarding (z70) → Toast (z80).
 */
@Composable
fun AppRoot(vm: MainViewModel, hasCameraPermission: Boolean, onSetLanguage: (String) -> Unit = {}) {
    val rates by vm.rates.collectAsState()
    val lists by vm.lists.collectAsState()
    val pins by vm.pins.collectAsState()
    val recents by vm.recents.collectAsState()
    val customs by vm.customs.collectAsState()
    val allCodes by vm.allCodes.collectAsState()
    val onboarded by vm.onboarded.collectAsState()
    val entitlements by vm.entitlements.collectAsState()
    val canCreateList by vm.canCreateList.collectAsState()
    val products by vm.products.collectAsState()
    val nativeAd by vm.nativeAd.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    var langOpen by remember { mutableStateOf(false) }
    val overlayOpen = vm.picker != null || vm.addOpen || vm.creating != null || vm.panelOpen ||
        vm.customOpen || langOpen || vm.paywallOpen != null || vm.settingsOpen

    // Fertiger CSV-Export: Teilen-Dialog starten, dann State leeren (§6.4).
    val shareIntent = vm.pendingShare
    LaunchedEffect(shareIntent) {
        if (shareIntent != null) {
            runCatching {
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.export_list))
                )
            }
            vm.consumeShare()
        }
    }

    // Status-Bar-Icons: hell über Kamera, dunkel über hellen Vollbild-Schichten.
    val view = LocalView.current
    val lightBackground = onboarded != true || vm.panelOpen
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBackground
    }

    FxTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF14110D))) {
            // 1. Kamera (CameraX-Preview, Vollbild)
            CameraScanPreview(
                enabled = hasCameraPermission && onboarded == true,
                analyzeActive = vm.scanPhase is ScanPhase.Scanning && !vm.panelOpen && onboarded == true,
                onValues = vm::onAnalyzerValues,
                modifier = Modifier.fillMaxSize(),
            )

            // 2. Top-Scrim für die Status-Bar-Lesbarkeit
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x47000000), Color.Transparent)))
            )

            // 4./6. Scan-Rahmen, Hint, Ergebnis-Karte
            ScanLayer(
                phase = vm.scanPhase,
                from = vm.from,
                to = vm.to,
                rates = rates.rates,
                dim = overlayOpen,
                onRescan = vm::rescan,
                onAdd = vm::openAdd,
                modifier = Modifier.fillMaxSize(),
            )

            // 3. Glas-Menü
            GlassMenu(
                from = vm.from,
                to = vm.to,
                rates = rates,
                swapAngle = vm.swapAngle,
                pickerOpen = vm.picker,
                onPick = vm::openPicker,
                onSwap = vm::swap,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // 5. Edge-Tab (rechter Rand, top ~300)
            EdgeTab(
                onClick = vm::openPanel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 300.dp),
            )

            // Sprach-Button unten links (§F4) — im ruhigen Scan-Zustand sichtbar.
            if (onboarded == true && !overlayOpen && vm.scanPhase is ScanPhase.Scanning) {
                LanguageButton(
                    onClick = { langOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 18.dp, bottom = 22.dp),
                )
            }

            // Währungs-Picker (Screen 3)
            vm.picker?.let { slot ->
                PickerSheet(
                    slot = slot,
                    from = vm.from,
                    to = vm.to,
                    allCodes = allCodes,
                    pinned = pins,
                    onTogglePin = vm::togglePin,
                    onChoose = vm::choose,
                    onAddCustom = vm::openCustom,
                    onClose = vm::closePicker,
                )
            }

            // „Eigener Kurs" (über dem Picker, §F2)
            if (vm.customOpen) {
                CustomRateSheet(
                    customs = customs,
                    allCodes = allCodes,
                    onCreate = vm::addCustom,
                    onDelete = vm::deleteCustom,
                    onClose = vm::closeCustom,
                )
            }

            // „Zu Liste hinzufügen" (Screen 4)
            if (vm.addOpen) {
                val raw = vm.addRaw ?: 0.0
                AddToListSheet(
                    from = vm.from,
                    to = vm.to,
                    raw = raw,
                    rates = rates.rates,
                    lists = lists.filter { it.currency == vm.to },
                    onAdd = { id, label -> vm.addToExisting(id, label) },
                    onNew = { label -> vm.requestCreateList(CreateMode.ADD, label) },
                    onClose = vm::closeAdd,
                )
            }

            // Listen-Panel (Screens 6 + 7, z60)
            ListsPanel(
                show = vm.panelOpen,
                lists = lists,
                selectedId = vm.selectedListId,
                onSelect = vm::selectList,
                onClose = vm::closePanel,
                onNew = { vm.requestCreateList(CreateMode.PANEL) },
                canCreateList = canCreateList,
                isAdFree = entitlements.adFree,
                onSettings = vm::openSettings,
                onExport = { id -> vm.exportList(id) },
                // Native-Anzeige nur im kühlen Pfad und nie für werbefreie Nutzer (§5/§11).
                nativeAd = if (entitlements.adFree) null else nativeAd,
                onEdit = vm::startEdit,
                onDeleteItem = vm::deleteItem,
                onEditItem = vm::startEditItem,
                modifier = Modifier.fillMaxSize(),
            )

            // „Neue Liste" (Screen 5, z65 — über dem Panel)
            vm.creating?.let { req ->
                CreateListSheet(
                    mode = req.mode,
                    currency = req.currency,
                    allCodes = allCodes,
                    pinned = pins,
                    recents = recents,
                    onCreate = vm::doCreate,
                    onClose = vm::cancelCreate,
                )
            }

            // „Position benennen" (z65 — über dem Panel)
            vm.editingItem?.let { target ->
                lists.find { it.id == target.listId }
                    ?.items?.find { it.id == target.itemId }
                    ?.let { item ->
                        EditItemSheet(
                            item = item,
                            currency = lists.find { it.id == target.listId }?.currency ?: vm.to,
                            onSave = vm::saveItemLabel,
                            onDelete = {
                                vm.deleteItem(target.listId, target.itemId)
                                vm.cancelEditItem()
                            },
                            onClose = vm::cancelEditItem,
                        )
                    }
            }

            // „Liste bearbeiten" (z65 — über dem Panel)
            vm.editingListId?.let { id ->
                lists.find { it.id == id }?.let { list ->
                    EditListSheet(
                        list = list,
                        onSave = vm::saveListEdit,
                        onDelete = vm::deleteList,
                        onClose = vm::cancelEdit,
                    )
                }
            }

            // Einstellungen (§7.2, über dem Panel)
            if (vm.settingsOpen) {
                SettingsSheet(
                    isAdFree = entitlements.adFree,
                    privacyOptionsRequired = vm.privacyOptionsRequired,
                    onUpgrade = { vm.closeSettings(); vm.openPaywall(PaywallContext.GENERIC) },
                    onRestore = vm::restorePurchases,
                    onPrivacyOptions = { activity?.let { vm.showPrivacyOptions(it) } },
                    onPrivacyPolicy = { openUrl(context, context.getString(R.string.privacy_url)) },
                    onTerms = { openUrl(context, context.getString(R.string.terms_url)) },
                    onClose = vm::closeSettings,
                )
            }

            // Paywall (§7.1, oberste Schicht im Listen-Bereich)
            vm.paywallOpen?.let { ctx ->
                PaywallSheet(
                    context = ctx,
                    products = products,
                    onBuy = { id -> activity?.let { vm.purchase(it, id) } },
                    onRestore = vm::restorePurchases,
                    onClose = vm::closePaywall,
                )
            }

            // Sprachauswahl (§F5, über Panel/Sheets)
            if (langOpen) {
                val ctx = LocalContext.current
                LanguageSheet(
                    current = LocaleStore.effective(ctx),
                    onSelect = { lang ->
                        langOpen = false
                        onSetLanguage(lang)
                    },
                    onClose = { langOpen = false },
                )
            }

            // Onboarding (z70, nur vor erstem Abschluss)
            if (onboarded == false) {
                OnboardingScreen(allCodes = allCodes, onDone = vm::completeOnboarding)
            }

            // Toast (z80)
            AppToast(
                msg = vm.toastMsg,
                visible = vm.toastVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Öffnet eine URL extern (Datenschutz/Nutzungsbedingungen, §7.2). */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
