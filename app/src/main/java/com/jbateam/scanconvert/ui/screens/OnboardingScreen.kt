package com.jbateam.scanconvert.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.ui.components.Flag
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcCheck
import com.jbateam.scanconvert.ui.components.PrimaryButton
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Motion
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import com.jbateam.scanconvert.ui.theme.motionTween

/**
 * Screen 1 — Onboarding (§13): Favoriten wählen, max 4, persistent.
 * Vollbild über allem (z70); Status-Bar liegt darüber (top-Padding 64 fix).
 */
/** Reihenfolge der §6-Tabelle — im Onboarding-Grid zuerst (wie Referenz/Screenshot). */
private val FeaturedOrder = listOf(
    "EUR", "USD", "GBP", "CHF", "JPY", "AUD", "CAD", "CNY",
    "INR", "BRL", "SEK", "NOK", "MXN", "ZAR", "SGD", "HKD",
)

@Composable
fun OnboardingScreen(allCodes: List<String>, onDone: (List<String>) -> Unit) {
    var selection by remember { mutableStateOf(listOf<String>()) }
    val orderedCodes = remember(allCodes) {
        FeaturedOrder.filter { it in allCodes } + allCodes.filterNot { it in FeaturedOrder }
    }

    fun toggle(code: String) {
        selection = when {
            code in selection -> selection - code
            selection.size >= 4 -> selection
            else -> selection + code
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Surface)
            .drawBehind {
                // CSS: radial-gradient(110% 50% at 50% 0%, #e6f2e3, transparent 70%)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Tokens.RadialOnbTop, Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = (size.width * 1.1f).coerceAtLeast(1f),
                    )
                )
            }
            .pointerInput(Unit) { } // Eingaben konsumieren — darunter liegt der Scanner
            .padding(start = 22.dp, end = 22.dp, top = 64.dp, bottom = 22.dp)
            .navigationBarsPadding()
    ) {
        // App-Icon (adaptives Launcher-Icon: dunkler Hintergrund + neues Logo-Vordergrund
        // aus @mipmap/ic_launcher_fg), gerundet wie eine Launcher-Kachel — identisch zum
        // realen Launcher-Icon, nicht das alte Monochrom-Motiv.
        Box(
            Modifier
                .padding(bottom = 18.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Tokens.AccentGlow, spotColor = Tokens.AccentGlow)
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.mipmap.ic_launcher_fg),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Txt(
            stringResource(R.string.onb_title),
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).em,
            lineHeight = 28.75.sp,
        )
        Txt(
            stringResource(R.string.onb_subtitle),
            fontSize = 14.sp,
            color = Tokens.Ink2,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            Modifier.padding(start = 2.dp, end = 2.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (i < selection.size) Tokens.Accent else Tokens.Line, CircleShape)
                    )
                }
            }
            Txt(
                stringResource(R.string.onb_selected, selection.size),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.AccentDeep,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            items(orderedCodes, key = { it }) { code ->
                val selected = code in selection
                val disabled = !selected && selection.size >= 4
                OnboardingTile(
                    code = code,
                    selected = selected,
                    disabled = disabled,
                    onClick = { if (!disabled) toggle(code) },
                )
            }
        }

        PrimaryButton(
            text = if (selection.isEmpty()) stringResource(R.string.onb_min) else stringResource(R.string.onb_go),
            enabled = selection.isNotEmpty(),
            modifier = Modifier.padding(top = 6.dp),
            onClick = { onDone(selection) },
        )
    }
}

/** Kachel (onb-tile): Border/Background-Übergang 0.15 s (§11). */
@Composable
private fun OnboardingTile(code: String, selected: Boolean, disabled: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        if (selected) Tokens.Accent else Tokens.Line,
        motionTween(150, Motion.EaseCss), label = "tileBorder",
    )
    val bgColor by animateColorAsState(
        if (selected) Tokens.AccentSoft else Tokens.Surface,
        motionTween(150, Motion.EaseCss), label = "tileBg",
    )
    val checkAlpha by animateFloatAsState(
        if (selected) 1f else 0f,
        motionTween(150, Motion.EaseCss), label = "tileCheck",
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .graphicsLayer { alpha = if (disabled) 0.4f else 1f }
            .clip(shape)
            .background(bgColor)
            .border(1.5.dp, borderColor, shape)
            .scaleClick(enabled = !disabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Flag(code, 32.dp)
        Column(Modifier.weight(1f)) {
            Txt(code, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Txt(
                CurrencyMeta.info(code).name,
                fontSize = 11.sp,
                color = Tokens.Ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.graphicsLayer { alpha = checkAlpha }) {
            Ic(IcCheck, tint = Tokens.Accent, modifier = Modifier.size(16.dp))
        }
    }
}
