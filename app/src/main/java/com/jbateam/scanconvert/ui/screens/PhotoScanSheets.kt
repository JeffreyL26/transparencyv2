package com.jbateam.scanconvert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.MainViewModel
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.domain.CreateMode
import com.jbateam.scanconvert.domain.TravelList
import com.jbateam.scanconvert.domain.convert
import com.jbateam.scanconvert.domain.fmt
import com.jbateam.scanconvert.domain.fmtNum
import com.jbateam.scanconvert.domain.fmtPlain
import com.jbateam.scanconvert.domain.parseAmount
import com.jbateam.scanconvert.domain.total
import com.jbateam.scanconvert.scan.PhotoDetection
import com.jbateam.scanconvert.ui.components.Flag
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcArrow
import com.jbateam.scanconvert.ui.components.IcEdit
import com.jbateam.scanconvert.ui.components.IcTrash
import com.jbateam.scanconvert.ui.components.SheetScaffold
import com.jbateam.scanconvert.ui.components.SheetTitle
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt

/**
 * Aktions-Karte eines angetippten Overlays (§Galerie-Scan): umgerechneter Betrag,
 * antippbar zum händischen Korrigieren des Rohwerts; darunter „Zu Liste hinzufügen“,
 * „Entfernen“. Nutzt für den Add-Flow denselben [AddToListSheet] wie der Kamera-Scan.
 */
@Composable
fun OverlayActionSheet(
    det: PhotoDetection,
    vm: MainViewModel,
    onClose: () -> Unit,
) {
    val from = vm.from
    val to = vm.to
    val rates = vm.rates.value.rates
    var editing by remember(det.id) { mutableStateOf(false) }
    val conv = convert(det.value, from, to, rates)

    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.overlay_title))

        // amount-chip: umgerechneter Zielbetrag groß, Rohwert darunter (antippbar).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Tokens.SurfaceWarm)
                .border(1.dp, Tokens.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt(
                    fmt(conv, to),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = NumSpacing,
                    lineHeight = 26.sp,
                    color = Tokens.Ink,
                )
                if (editing) {
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OverlayAmountField(
                            value = det.value,
                            code = from,
                            onValue = { vm.setPhotoDetectionValue(det.id, it) },
                            onDone = { editing = false },
                        )
                        Txt(from, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Ink3)
                    }
                } else {
                    Row(
                        Modifier
                            .padding(top = 5.dp)
                            .scaleClick(scale = 0.98f, onClick = { editing = true }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Txt(
                            stringResource(R.string.from_scanned, fmt(det.value, from)),
                            fontSize = 12.sp,
                            color = Tokens.Ink2,
                        )
                        Ic(IcEdit, tint = Tokens.Ink3, modifier = Modifier.size(13.dp))
                    }
                }
            }
            Flag(to, 38.dp)
        }

        // „Zu Liste hinzufügen“ — öffnet den bestehenden Add-Flow mit diesem Rohwert.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Tokens.Accent)
                .scaleClick(scale = 0.99f, onClick = {
                    onClose()
                    vm.openAddFromPhoto(det.value)
                })
                .padding(13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(IcArrow, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Txt(
                stringResource(R.string.add_to_list),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // „Entfernen“ — dieses Overlay verwerfen.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Tokens.DangerSoft)
                .scaleClick(scale = 0.99f, onClick = {
                    onClose()
                    vm.removePhotoDetection(det.id)
                })
                .padding(13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(IcTrash, tint = Tokens.Danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Txt(
                stringResource(R.string.overlay_remove),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.Danger,
            )
        }
    }
}

/**
 * „Alle zu Liste hinzufügen“-Sheet: Zielwährung ist die Session-Zielwährung; nur
 * Listen dieser Währung werden angeboten. Auswahl fügt ALLE verbliebenen Overlays
 * als Positionen ein; „Neue Liste“ legt eine ADD_ALL-Liste an.
 */
@Composable
fun AddAllSheet(
    to: String,
    from: String,
    detections: List<PhotoDetection>,
    rates: Map<String, Double>,
    lists: List<TravelList>,
    onAddAll: (listId: String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    val sum = detections.sumOf { convert(it.value, from, to, rates) }
    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.add_all_title))

        // Summen-Chip: Anzahl + Gesamtsumme in Zielwährung.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Tokens.SurfaceWarm)
                .border(1.dp, Tokens.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt(
                    fmt(sum, to),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = NumSpacing,
                    lineHeight = 26.sp,
                    color = Tokens.Ink,
                )
                Txt(
                    stringResource(R.string.add_all_count, detections.size),
                    fontSize = 12.sp,
                    color = Tokens.Ink2,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Flag(to, 38.dp)
        }

        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (lists.isEmpty()) {
                Txt(
                    stringResource(R.string.no_list_in, to),
                    fontSize = 12.5.sp,
                    color = Tokens.Ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                )
            }
            lists.forEach { list ->
                AddAllListRow(list = list, onClick = { onAddAll(list.id) })
            }
            DashedNewRow(text = stringResource(R.string.new_list_for, to), onClick = onNew)
        }
    }
}

/** Listen-Zeile im „Alle hinzufügen“-Sheet (analog AddToListSheet). */
@Composable
private fun AddAllListRow(list: TravelList, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.Surface)
            .border(1.dp, Tokens.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Flag(list.currency, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(list.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.Ink, maxLines = 1)
            Txt(stringResource(R.string.positions, list.items.size), fontSize = 12.sp, color = Tokens.Ink2)
        }
        Txt(
            fmt(list.total(), list.currency),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Grotesk,
            letterSpacing = NumSpacing,
            color = Tokens.AccentDeep,
            maxLines = 1,
        )
    }
}

/** Inline-Zahlenfeld zum Korrigieren des Overlay-Rohwerts. */
@Composable
private fun OverlayAmountField(
    value: Double,
    code: String,
    onValue: (Double) -> Unit,
    onDone: () -> Unit,
) {
    var text by remember { mutableStateOf(fmtPlain(value, code)) }
    BasicTextField(
        value = text,
        onValueChange = { txt ->
            val filtered = txt.filter { it.isDigit() || it == ',' || it == '.' }
            text = filtered
            parseAmount(filtered)?.let(onValue)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = Tokens.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Grotesk,
            letterSpacing = NumSpacing,
        ),
        cursorBrush = SolidColor(Tokens.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.Surface)
            .border(1.dp, Tokens.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
