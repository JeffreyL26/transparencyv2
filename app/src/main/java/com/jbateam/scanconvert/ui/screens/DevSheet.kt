package com.jbateam.scanconvert.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.data.billing.DevOverride
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcCheck
import com.jbateam.scanconvert.ui.components.SheetScaffold
import com.jbateam.scanconvert.ui.components.SheetTitle
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Verstecktes Entwickler-Sheet (CLAUDE.md §13.2) — NUR im Debug-Build erreichbar
 * (Long-Press auf den Sprach-Button, Aufruf hinter `BuildConfig.DEBUG`). Schaltet die
 * lokalen Entitlement-Overrides der [com.jbateam.scanconvert.data.billing.DebugEntitlementSource]:
 * Werbefrei (§5/§7.4), unbegrenzte Listen (§4) und Export (§7.2). „Vacation-Pass"
 * setzt `adFree` zeitbasiert auf 7 Tage (§3); „Override aus" kehrt zur echten
 * Play-Quelle zurück.
 *
 * Texte sind bewusst hartkodiert (kein Release-Artefakt, keine Lokalisierung nötig).
 */
@Composable
fun DevSheet(
    override: DevOverride,
    onSetAdFree: (Boolean) -> Unit,
    onSetUnlimited: (Boolean) -> Unit,
    onSetExport: (Boolean) -> Unit,
    onGrantVacationPass: () -> Unit,
    onClearOverride: () -> Unit,
    onClose: () -> Unit,
) {
    SheetScaffold(onDismiss = onClose) {
        SheetTitle("DEV · ENTITLEMENTS (nur Debug)")
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Zustandshinweis: greift der Override oder die echte Play-Quelle?
            Txt(
                if (override.active) "Override AKTIV — überschreibt die echte Play-Quelle"
                else "Override aus — Werte kommen von Google Play",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (override.active) Tokens.AccentDeep else Tokens.Ink3,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )

            DevSwitchRow(
                title = "Werbefrei (adFree)",
                subtitle = "§5/§7.4 — keine Native-Ad",
                checked = override.adFree,
                onCheckedChange = onSetAdFree,
            )
            DevSwitchRow(
                title = "Unbegrenzte Listen (unlimitedLists)",
                subtitle = "§4 — keine 3-Listen-Sperre",
                checked = override.unlimitedLists,
                onCheckedChange = onSetUnlimited,
            )
            DevSwitchRow(
                title = "Export (listExport)",
                subtitle = "§7.2 — PDF-Export ohne Paywall",
                checked = override.listExport,
                onCheckedChange = onSetExport,
            )

            Spacer(Modifier.size(2.dp))

            val passActive = override.vacationPassUntil > System.currentTimeMillis()
            DevActionRow(
                title = if (passActive) "Vacation-Pass aktiv bis ${formatTs(override.vacationPassUntil)}"
                else "Vacation-Pass: 7 Tage gewähren",
                subtitle = "§3 — schaltet adFree zeitbasiert frei",
                onClick = onGrantVacationPass,
            )
            DevActionRow(
                title = "Override aus (zurück zur echten Quelle)",
                subtitle = "§13.2 — löscht den Debug-Zustand",
                danger = true,
                onClick = onClearOverride,
            )
        }
    }
}

/** Schalter-Zeile im Sheet-Stil (vgl. SettingRow), mit eigenem Toggle (kein Material). */
@Composable
private fun DevSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = { onCheckedChange(!checked) })
            .clip(shape)
            .background(if (checked) Tokens.AccentSoft else Tokens.Surface)
            .border(1.dp, if (checked) Tokens.Accent else Tokens.Line, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (checked) Tokens.AccentInk else Tokens.Ink,
            )
            Txt(subtitle, modifier = Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Tokens.Ink2)
        }
        DevToggle(checked = checked)
    }
}

/** Mini-Toggle: Track + gleitender Knopf, accent wenn an. */
@Composable
private fun DevToggle(checked: Boolean) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Tokens.Accent else Tokens.Line,
        label = "devToggleTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "devToggleKnob",
    )
    Box(
        Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Ic(IcCheck, tint = Tokens.Accent, modifier = Modifier.size(14.dp))
        }
    }
}

/** Aktions-Zeile (Button-artig) im Sheet-Stil. */
@Composable
private fun DevActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(shape)
            .background(if (danger) Tokens.DangerSoft else Tokens.SurfaceWarm)
            .border(1.dp, if (danger) Tokens.Danger else Tokens.Line, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (danger) Tokens.DangerDark else Tokens.Ink,
            )
            Txt(subtitle, modifier = Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Tokens.Ink2)
        }
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(ts))
