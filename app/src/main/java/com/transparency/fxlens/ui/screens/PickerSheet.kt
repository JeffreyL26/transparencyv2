package com.transparency.fxlens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.transparency.fxlens.R
import com.transparency.fxlens.data.CurrencyMeta
import com.transparency.fxlens.domain.PickerSlot
import com.transparency.fxlens.ui.components.Flag
import com.transparency.fxlens.ui.components.Ic
import com.transparency.fxlens.ui.components.IcCheck
import com.transparency.fxlens.ui.components.IcPin
import com.transparency.fxlens.ui.components.IcPinFilled
import com.transparency.fxlens.ui.components.IcPlus
import com.transparency.fxlens.ui.components.IcSearch
import com.transparency.fxlens.ui.components.SheetScaffold
import com.transparency.fxlens.ui.components.SheetTitle
import com.transparency.fxlens.ui.components.scaleClick
import com.transparency.fxlens.ui.theme.Grotesk
import com.transparency.fxlens.ui.theme.Jakarta
import com.transparency.fxlens.ui.theme.NumSpacing
import com.transparency.fxlens.ui.theme.Tokens
import com.transparency.fxlens.ui.theme.Txt

/**
 * Screen 3 — Währungs-Picker (Bottom-Sheet, Handoff §13).
 * Suche über Code + Name; ohne Suche: Pins in fester Reihenfolge,
 * Trenner, dann übrige Codes alphabetisch (allCodes ist sortiert).
 */
@Composable
fun PickerSheet(
    slot: PickerSlot,
    from: String,
    to: String,
    allCodes: List<String>,
    pinned: List<String>,
    onTogglePin: (String) -> Unit,
    onChoose: (String) -> Unit,
    onAddCustom: () -> Unit,
    onClose: () -> Unit,
) {
    val customCodes = remember(allCodes) { allCodes.filter { CurrencyMeta.isCustom(it) } }
    var q by remember { mutableStateOf("") }
    val active = if (slot == PickerSlot.FROM) from else to
    val query = q.trim().lowercase()
    val searching = query.isNotEmpty()
    val full = pinned.size >= 4
    // Bei offener Tastatur die Trefferliste verkürzen, damit das Suchfeld oben
    // über der Tastatur sichtbar bleibt (zusätzlich zum imePadding des Sheets, §2).
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val listMax = if (imeOpen) 232.dp else 372.dp

    SheetScaffold(onDismiss = onClose) {
        SheetTitle(if (slot == PickerSlot.FROM) stringResource(R.string.picker_from) else stringResource(R.string.picker_to))

        // Suchfeld (.sheet-search): surface-warm, radius 14, padding 13/11, gap 9
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Tokens.SurfaceWarm)
                .border(1.dp, Tokens.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Ic(IcSearch, tint = Tokens.Ink3, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) {
                    Txt(stringResource(R.string.search_placeholder), fontSize = 14.5.sp, color = Tokens.Ink3)
                }
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Jakarta,
                        fontSize = 14.5.sp,
                        color = Tokens.Ink,
                    ),
                    cursorBrush = SolidColor(Tokens.Ink3),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Liste (.sheet-list): max-Höhe 372, bei offener Tastatur verkürzt
        LazyColumn(Modifier.heightIn(max = listMax)) {
            if (searching) {
                val hits = allCodes.filter { code ->
                    code.lowercase().contains(query) ||
                        CurrencyMeta.info(code).name.lowercase().contains(query)
                }
                if (hits.isEmpty()) {
                    item(key = "empty") {
                        Txt(
                            stringResource(R.string.no_hits, q),
                            fontSize = 13.sp,
                            color = Tokens.Ink2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    items(hits, key = { it }) { code ->
                        CurRow(code, code == active, code in pinned, full, onChoose, onTogglePin)
                    }
                }
            } else {
                // Eigene Kurse (§F2): eigene Sektion oben, aus den übrigen ausgeschlossen.
                item(key = "label-custom") { PickerSectionLabel(stringResource(R.string.section_custom)) }
                items(customCodes, key = { it }) { code ->
                    CurRow(code, code == active, isPinned = code in pinned, full = full, onChoose = onChoose, onTogglePin = onTogglePin)
                }
                item(key = "add-custom") { AddCustomRow(count = customCodes.size, onClick = onAddCustom) }

                item(key = "label-pinned") { PickerSectionLabel(stringResource(R.string.section_pinned)) }
                items(pinned.filter { it in allCodes && it !in customCodes }, key = { it }) { code ->
                    CurRow(code, code == active, isPinned = true, full = full, onChoose = onChoose, onTogglePin = onTogglePin)
                }
                item(key = "divider") { PickerDivider(stringResource(R.string.section_all)) }
                items(allCodes.filter { it !in pinned && it !in customCodes }, key = { it }) { code ->
                    CurRow(code, code == active, isPinned = false, full = full, onChoose = onChoose, onTogglePin = onTogglePin)
                }
            }
        }
    }
}

/** „+ Eigener Kurs"-Zeile im Picker (§F2): öffnet das Custom-Kurs-Sheet. */
@Composable
private fun AddCustomRow(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.AccentSoft)
            .border(1.dp, Tokens.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Tokens.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Ic(IcPlus, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(stringResource(R.string.custom_rate_add), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Tokens.Ink)
            Txt(stringResource(R.string.custom_count, count), fontSize = 12.sp, color = Tokens.Ink2)
        }
    }
}

/** .sheet-section-label: 10/700, +0.12em, ink-3, Margin 4/12/4. */
@Composable
private fun PickerSectionLabel(text: String) {
    Txt(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.12.em,
        color = Tokens.Ink3,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** .sheet-divider: Linie + Label + Linie, gap 10, Margin 12/10/6. */
@Composable
private fun PickerDivider(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Tokens.Line))
        Txt(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.em,
            color = Tokens.Ink3,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Tokens.Line))
    }
}

/** Picker-Zeile (.cur-opt): Hauptbereich wählt, Pin-Button togglet nur. */
@Composable
private fun CurRow(
    code: String,
    active: Boolean,
    isPinned: Boolean,
    full: Boolean,
    onChoose: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    val info = remember(code) { CurrencyMeta.info(code) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Tokens.AccentSoft else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .scaleClick(scale = 0.99f) { onChoose(code) }
                .padding(start = 10.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Flag(code, 32.dp)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Txt(code, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    if (active) {
                        Ic(IcCheck, tint = Tokens.Accent, modifier = Modifier.size(16.dp))
                    }
                }
                Txt(
                    info.name,
                    fontSize = 12.sp,
                    color = Tokens.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // .cur-sym-mini: Zahlen-Font, rechtsbündig, min-width 30
            Txt(
                info.sym,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Grotesk,
                letterSpacing = NumSpacing,
                color = Tokens.Ink3,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 30.dp),
            )
        }
        val pinEnabled = isPinned || !full
        Box(
            Modifier
                .padding(end = 2.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .graphicsLayer { alpha = if (pinEnabled) 1f else 0.25f }
                .scaleClick(enabled = pinEnabled) { onTogglePin(code) },
            contentAlignment = Alignment.Center,
        ) {
            Ic(
                if (isPinned) IcPinFilled else IcPin,
                tint = if (isPinned) Tokens.Accent else Tokens.Ink3,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
