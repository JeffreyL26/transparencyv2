package com.jbateam.scanconvert.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcChevron
import com.jbateam.scanconvert.ui.components.SheetScaffold
import com.jbateam.scanconvert.ui.components.SheetTitle
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.LocalMotionScale
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Einstellungen (§7.2). Einstieg über das Zahnrad im ListsPanel-Kopf. Enthält den
 * prominenten „Werbung entfernen"-CTA, Restore, den von UMP vorgeschriebenen
 * Privacy-Options-Eintrag (nur wenn erforderlich) und die rechtlichen Links (Play
 * verlangt eine Datenschutz-URL bei Ads/IAP). Die Sprachwahl liegt bewusst NICHT
 * hier — sie ist über den Sprach-Button in der Haupt-UI (§F4) erreichbar.
 */
@Composable
fun SettingsSheet(
    isAdFree: Boolean,
    privacyOptionsRequired: Boolean,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTerms: () -> Unit,
    onClose: () -> Unit,
) {
    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.settings_title))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Breiter, animierter „Werbung entfernen"-CTA als primärer Upgrade-Pfad.
            if (!isAdFree) {
                AdFreeButton(onClick = onUpgrade)
            }
            SettingRow(title = stringResource(R.string.restore_purchases), onClick = onRestore)
            if (privacyOptionsRequired) {
                SettingRow(title = stringResource(R.string.privacy_options), onClick = onPrivacyOptions)
            }

            Spacer(Modifier.size(4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LinkText(stringResource(R.string.settings_privacy_policy), onPrivacyPolicy)
                LinkText(stringResource(R.string.settings_terms), onTerms)
            }
        }
    }
}

/**
 * Prominenter „Werbung entfernen"-CTA: breiter, accent-gefüllter Button, der pro
 * Zyklus EINMAL kurz wackelt und aufleuchtet (sonst ruhig) — eine Aufmerksamkeits-
 * Einladung zum Tippen. `prefers-reduced-motion` / Animationsskala = 0 → statischer,
 * dezenter Glow ohne Wackeln (analog [com.jbateam.scanconvert.ui.components.LiveDot]).
 */
@Composable
private fun AdFreeButton(onClick: () -> Unit) {
    val motionScale = LocalMotionScale.current
    val envelope: Float
    val wiggle: Float
    if (motionScale > 0f) {
        val transition = rememberInfiniteTransition(label = "adfreePulse")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((4200 * motionScale).toInt().coerceAtLeast(1), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "adfreePulse",
        )
        // Aktives Fenster = erste ~22 % des Zyklus, danach Ruhe bis zum nächsten Impuls.
        val active = 0.22f
        val a = (t / active).coerceIn(0f, 1f)
        envelope = if (t < active) sin(a * PI).toFloat() else 0f
        wiggle = if (t < active) sin(a * PI * 4f).toFloat() * envelope else 0f
    } else {
        envelope = 0.5f
        wiggle = 0f
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationZ = wiggle * 2f
                val s = 1f + 0.02f * envelope
                scaleX = s
                scaleY = s
            }
            .shadow(
                elevation = (6 + 12 * envelope).dp,
                shape = shape,
                ambientColor = Tokens.AccentGlow,
                spotColor = Tokens.AccentGlow,
            )
            .clip(shape)
            .background(Tokens.Accent)
            .scaleClick(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(
            stringResource(R.string.adfree_cta).uppercase(Locale.ROOT),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.04.em,
            color = Color.White,
        )
        Txt(
            stringResource(R.string.adfree_cta_sub),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(shape)
            .background(if (accent) Tokens.AccentSoft else Tokens.Surface)
            .border(1.dp, if (accent) Tokens.Accent else Tokens.Line, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (accent) Tokens.AccentInk else Tokens.Ink,
            )
            if (subtitle != null) {
                Txt(subtitle, modifier = Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Tokens.Ink2)
            }
        }
        Ic(IcChevron, tint = Tokens.Ink3, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Box(Modifier.scaleClick(scale = 0.98f, onClick = onClick)) {
        Txt(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tokens.Ink2,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        )
    }
}
