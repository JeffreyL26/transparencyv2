package com.transparency.fxlens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.transparency.fxlens.MainViewModel
import com.transparency.fxlens.domain.CreateMode
import com.transparency.fxlens.domain.ScanPhase
import com.transparency.fxlens.scan.CameraScanPreview
import com.transparency.fxlens.ui.components.AppToast
import com.transparency.fxlens.ui.screens.AddToListSheet
import com.transparency.fxlens.ui.screens.CreateListSheet
import com.transparency.fxlens.ui.screens.EdgeTab
import com.transparency.fxlens.ui.screens.EditListSheet
import com.transparency.fxlens.ui.screens.GlassMenu
import com.transparency.fxlens.ui.screens.ListsPanel
import com.transparency.fxlens.ui.screens.OnboardingScreen
import com.transparency.fxlens.ui.screens.PickerSheet
import com.transparency.fxlens.ui.screens.ScanLayer
import com.transparency.fxlens.ui.theme.FxTheme

/**
 * Schichtung wie im Prototyp (z-Reihenfolge von unten):
 * Kamera → Scan-Layer (Rahmen + Ergebnis-Karte) → Glas-Menü → Edge-Tab →
 * Picker/Add-Sheets (z50) → Listen-Panel (z60) → Create/Edit-Sheets (z65) →
 * Onboarding (z70) → Toast (z80).
 */
@Composable
fun AppRoot(vm: MainViewModel, hasCameraPermission: Boolean) {
    val rates by vm.rates.collectAsState()
    val lists by vm.lists.collectAsState()
    val pins by vm.pins.collectAsState()
    val allCodes by vm.allCodes.collectAsState()
    val onboarded by vm.onboarded.collectAsState()

    val overlayOpen = vm.picker != null || vm.addOpen || vm.creating != null || vm.panelOpen

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
                onValue = vm::onAnalyzerValue,
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
                    onClose = vm::closePicker,
                )
            }

            // „Zu Liste hinzufügen" (Screen 4)
            if (vm.addOpen) {
                val raw = (vm.scanPhase as? ScanPhase.Locked)?.raw ?: 0.0
                AddToListSheet(
                    from = vm.from,
                    to = vm.to,
                    raw = raw,
                    rates = rates.rates,
                    lists = lists.filter { it.currency == vm.to },
                    onAdd = vm::addToExisting,
                    onNew = { vm.startCreate(CreateMode.ADD) },
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
                onNew = { vm.startCreate(CreateMode.PANEL) },
                onEdit = vm::startEdit,
                onDeleteItem = vm::deleteItem,
                modifier = Modifier.fillMaxSize(),
            )

            // „Neue Liste" (Screen 5, z65 — über dem Panel)
            vm.creating?.let { req ->
                CreateListSheet(
                    mode = req.mode,
                    currency = req.currency,
                    allCodes = allCodes,
                    onCreate = vm::doCreate,
                    onClose = vm::cancelCreate,
                )
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
