package com.jbateam.scanconvert.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.data.FxRates
import com.jbateam.scanconvert.domain.PickerSlot
import com.jbateam.scanconvert.domain.fmtRate
import com.jbateam.scanconvert.ui.components.Flag
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcChat
import com.jbateam.scanconvert.ui.components.IcImage
import com.jbateam.scanconvert.ui.components.IcList
import com.jbateam.scanconvert.ui.components.IcSwap
import com.jbateam.scanconvert.ui.components.LiveDot
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.Motion
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import com.jbateam.scanconvert.ui.theme.motionTween
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glas-Menü (Handoff §13 Screen 2 Punkt 3, app.css .menu):
 * VON-Chip | Swap | ZU-Chip + Kurszeile. Backdrop-Blur entfällt auf Android
 * (Kamera darunter) — nur die Glass-Farbe.
 */
@Composable
fun GlassMenu(
    from: String,
    to: String,
    rates: FxRates,
    swapAngle: Float,
    pickerOpen: PickerSlot?,
    onPick: (PickerSlot) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .offset(y = 60.dp)
            .padding(horizontal = 14.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Tokens.Glass)
            // rgba(255,255,255,0.65)
            .border(1.dp, Color(0xA6FFFFFF), shape)
            .padding(14.dp)
    ) {
        // menu-row: minmax(0,1fr) auto minmax(0,1fr), gap 8, stretch
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            ChipColumn(
                label = stringResource(R.string.menu_from),
                code = from,
                open = pickerOpen == PickerSlot.FROM,
                onClick = { onPick(PickerSlot.FROM) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SwapColumn(swapAngle = swapAngle, onSwap = onSwap)
            Spacer(Modifier.width(8.dp))
            ChipColumn(
                label = stringResource(R.string.menu_to),
                code = to,
                open = pickerOpen == PickerSlot.TO,
                onClick = { onPick(PickerSlot.TO) },
                modifier = Modifier.weight(1f),
            )
        }

        // rate-row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Tokens.SurfaceWarm)
                .border(1.dp, Tokens.Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Punkt pulsiert nur live; offline statisch gedämpft (§1).
            if (rates.live) {
                LiveDot(8.dp)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Tokens.Ink3))
            }
            Spacer(Modifier.width(12.dp))
            RateText(from = from, to = to, rates = rates)
            Spacer(Modifier.weight(1f))
            // Online: „LIVE". Verbindung verloren: Datum + Uhrzeit des letzten Stands (§1).
            val live = stringResource(R.string.status_live)
            val offline = stringResource(R.string.status_offline)
            val status = when {
                rates.live -> live
                rates.updatedAt > 0L -> remember(rates.updatedAt) {
                    SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(rates.updatedAt))
                }
                else -> offline
            }
            Txt(
                status,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = Tokens.AccentDeep,
                maxLines = 1,
            )
        }
    }
}

/** "1 {from} = {rate} {to}" — Codes/Kurs fett im Zahlen-Font. */
@Composable
private fun RowScope.RateText(from: String, to: String, rates: FxRates) {
    @Composable
    fun plain(text: String) = Txt(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = Tokens.Ink2,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
    )

    @Composable
    fun bold(text: String) = Txt(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Grotesk,
        letterSpacing = NumSpacing,
        color = Tokens.Ink,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
    )

    plain("1 ")
    bold(from)
    plain(" = ")
    bold(fmtRate(from, to, rates.rates))
    plain(" $to")
}

/** Label (VON/ZU) + Währungs-Chip. */
@Composable
private fun ChipColumn(
    label: String,
    code: String,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight()) {
        MenuLabel(label)
        CurChip(code = code, open = open, onClick = onClick)
    }
}

@Composable
private fun MenuLabel(text: String, color: Color = Tokens.Ink3) {
    Txt(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.12.em,
        color = color,
        maxLines = 1,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** Chip (.chip): Flagge + Code + Symbol + Caret; Offen-State mit Accent-Border + 3dp-Ring. */
@Composable
private fun CurChip(code: String, open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val info = remember(code) { CurrencyMeta.info(code) }
    Row(
        Modifier
            .fillMaxWidth()
            // Ring „box-shadow 0 0 0 3px accent-soft": 3dp-Stroke außen am Rand,
            // vor clip() gezeichnet, damit er nicht beschnitten wird.
            .drawBehind {
                if (open) {
                    val w = 3.dp.toPx()
                    drawRoundRect(
                        color = Tokens.AccentSoft,
                        topLeft = Offset(-w / 2f, -w / 2f),
                        size = Size(size.width + w, size.height + w),
                        cornerRadius = CornerRadius(16.dp.toPx() + w / 2f),
                        style = Stroke(width = w),
                    )
                }
            }
            .clip(shape)
            .background(Tokens.Surface)
            .border(1.dp, if (open) Tokens.Accent else Tokens.Line, shape)
            .scaleClick(scale = 0.98f, onClick = onClick)
            .padding(start = 10.dp, top = 9.dp, end = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Flag(code, 30.dp)
        Spacer(Modifier.width(9.dp))
        Row {
            Txt(
                code,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.01).em,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(5.dp))
            Txt(
                info.sym,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Grotesk,
                color = Tokens.Ink3,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.weight(1f))
        Txt("▼", fontSize = 9.sp, color = Tokens.Ink3, maxLines = 1)
    }
}

/** Swap-Spalte (.swap-col): unsichtbarer Label-Platzhalter + runder Swap-Button. */
@Composable
private fun SwapColumn(swapAngle: Float, onSwap: () -> Unit) {
    val angle by animateFloatAsState(
        targetValue = swapAngle,
        animationSpec = motionTween(350, Motion.EaseSwap),
        label = "swapSpin",
    )
    Column(
        Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Platzhalter in Label-Höhe, damit der Button auf Chip-Mitte sitzt.
        MenuLabel(" ", color = Color.Transparent)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(42.dp)
                    .graphicsLayer { rotationZ = angle }
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Tokens.Surface)
                    .border(1.dp, Tokens.Line, CircleShape)
                    .scaleClick(onClick = onSwap),
                contentAlignment = Alignment.Center,
            ) {
                Ic(IcSwap, tint = Tokens.AccentDeep, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/**
 * Edge-Tab (Handoff §13 Screen 2 Punkt 5, app.css .edge-tab):
 * Glas-Handle 42×64 am rechten Rand, links gerundet, Grip-Strich + Listen-Icon.
 */
@Composable
fun EdgeTab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    Box(
        modifier
            .size(width = 42.dp, height = 64.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Tokens.GlassStrong)
            .scaleClick(onClick = onClick)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = 5.dp)
                .size(width = 3.dp, height = 26.dp)
                .background(Tokens.Line, RoundedCornerShape(3.dp))
        )
        Ic(
            IcList,
            tint = Tokens.AccentDeep,
            modifier = Modifier
                .align(Alignment.Center)
                // padding-left 4 der Referenz verschiebt den Inhalt um 2 nach rechts
                .offset(x = 2.dp)
                .size(22.dp),
        )
    }
}

/**
 * Sprach-Button (§F4): runder Glas-Button mit Sprechblasen-Icon, unten links.
 * Gleicher Glas-Stil wie Edge-Tab / Swap-Button.
 *
 * [onLongClick] ist optional und bleibt in Release `null`. Im Debug-Build dient der
 * Long-Press als versteckter Einstieg ins Dev-Sheet (§13.2).
 */
@Composable
fun LanguageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(52.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Tokens.GlassStrong)
            .border(1.dp, Color(0xA6FFFFFF), CircleShape)
            .scaleClick(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ic(IcChat, tint = Tokens.AccentDeep, modifier = Modifier.size(25.dp))
    }
}

/** Runder Glas-Button mit Galerie-Icon — öffnet die In-App-Galerie (Foto-Scan). */
@Composable
fun GalleryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Tokens.GlassStrong)
            .border(1.dp, Color(0xA6FFFFFF), CircleShape)
            .scaleClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ic(IcImage, tint = Tokens.AccentDeep, modifier = Modifier.size(26.dp))
    }
}
