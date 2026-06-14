package com.jbateam.scanconvert.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.domain.fmt
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.LocalMotionScale
import com.jbateam.scanconvert.ui.theme.Motion
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import com.jbateam.scanconvert.ui.theme.motionTween

/**
 * Klick mit Press-Scale-Feedback (§11: scale 0.98–0.99, ~0.12 s) ohne Ripple.
 */
fun Modifier.scaleClick(
    scale: Float = 0.98f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = motionTween(120, Motion.EaseCss),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Pulsierender Live-Punkt (§11: Ring scale .7→1.35, opacity .55→0, 1.8 s loop).
 */
@Composable
fun LiveDot(size: Dp = 8.dp, color: Color = Tokens.Accent, modifier: Modifier = Modifier) {
    val motionScale = LocalMotionScale.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(color, CircleShape)
        )
        if (motionScale > 0f) {
            val transition = rememberInfiniteTransition(label = "livepulse")
            val p by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween((1800 * motionScale).toInt().coerceAtLeast(1), easing = Motion.EaseOut),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "livepulse",
            )
            // Ringbox = Punkt + 6 (CSS inset −3px)
            Box(
                Modifier
                    .requiredSize(size + 6.dp)
                    .graphicsLayer {
                        val sc = 0.7f + 0.65f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = 0.55f * (1f - p)
                    }
                    .border(1.5.dp, color, CircleShape)
            )
        }
    }
}

/**
 * Budget-Balken (§11/§13): Track 8dp, Fill-Verlauf accent→#38b07f bzw.
 * budget-over-Verlauf; Breite animiert 0.5 s cubic-bezier(.3,1,.4,1).
 */
@Composable
fun BudgetBar(
    total: Double,
    budget: Double,
    currency: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val pct = if (budget > 0) (total / budget).coerceIn(0.0, 1.0) else 0.0
    val over = total > budget
    val rem = budget - total
    val animPct by animateFloatAsState(
        targetValue = pct.toFloat(),
        animationSpec = motionTween(500, Motion.EaseBudget),
        label = "budget",
    )
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Tokens.SurfaceWarm)
                .border(1.dp, Tokens.Line, RoundedCornerShape(5.dp))
        ) {
            if (animPct > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animPct)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (over) listOf(Tokens.BudgetOver1, Tokens.BudgetOver2)
                                else listOf(Tokens.Accent, Tokens.AccentFill2)
                            )
                        )
                )
            }
        }
        if (!compact) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(stringResource(R.string.budget_prefix), fontSize = 12.sp, color = Tokens.Ink2)
                    Txt(
                        fmt(budget, currency),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Grotesk,
                        color = Tokens.Ink,
                    )
                }
                Txt(
                    text = if (over) stringResource(R.string.budget_over, fmt(-rem, currency)) else stringResource(R.string.budget_left, fmt(rem, currency)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (over) Tokens.Danger else Tokens.AccentDeep,
                )
            }
        }
    }
}

/**
 * Primär-Button (btn-primary): accent, radius 15, weiß 15/700,
 * Glow-Schatten, disabled opacity .4.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .then(
                if (enabled) Modifier.shadow(
                    8.dp, shape,
                    ambientColor = Tokens.AccentGlow,
                    spotColor = Tokens.AccentGlow,
                ) else Modifier
            )
            .clip(shape)
            .background(Tokens.Accent)
            .scaleClick(scale = 0.99f, enabled = enabled, onClick = onClick)
            .padding(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Ic(icon, tint = Color.White, modifier = Modifier.size(19.dp))
            Box(Modifier.size(9.dp))
        }
        Txt(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Icon-Button des Panels (icon-btn): 40dp, radius 12, border line, surface.
 */
@Composable
fun IconBtn(
    vector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tokens.Ink2,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Surface)
            .border(1.dp, Tokens.Line, RoundedCornerShape(12.dp))
            .scaleClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ic(vector, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/**
 * Toast (Screen 8): dunkle Pill, accent-Punkt mit Häkchen,
 * Einblenden 0.25 s cubic-bezier(.3,1.3,.5,1), translateY 20→0.
 */
@Composable
fun AppToast(msg: String, visible: Boolean, modifier: Modifier = Modifier) {
    // Transform mit Overshoot-Kurve, Opacity mit eigener ease-Kurve (CSS .toast).
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motionTween(250, Motion.EaseToast),
        label = "toast",
    )
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motionTween(250, Motion.EaseCss),
        label = "toastFade",
    )
    if ((progress > 0.01f || fade > 0.01f) && msg.isNotEmpty()) {
        Row(
            modifier = modifier
                .padding(bottom = 120.dp)
                .graphicsLayer {
                    alpha = fade
                    translationY = (1f - progress) * 20.dp.toPx()
                }
                .shadow(14.dp, RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .background(Tokens.Ink)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(Tokens.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Ic(IcCheck, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Txt(msg, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
