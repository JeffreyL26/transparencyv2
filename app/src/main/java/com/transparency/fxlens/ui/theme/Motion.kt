package com.transparency.fxlens.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Animations-Kurven & -Dauern (Handoff §11) + Respekt der System-Animationsskala
 * („prefers-reduced-motion“): bei Skala 0 werden Endzustände direkt gezeigt.
 */
object Motion {
    /** Bottom-Sheets, Listen-Panel, Ergebnis-Karte: cubic-bezier(.2,.9,.3,1) */
    val EaseSheet: Easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

    /** „Erkannt“-Badge: cubic-bezier(.3,1.5,.5,1) — Overshoot */
    val EaseBadge: Easing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

    /** Swap-Button: cubic-bezier(.5,1.4,.5,1) — federnd */
    val EaseSwap: Easing = CubicBezierEasing(0.5f, 1.4f, 0.5f, 1f)

    /** Toast: cubic-bezier(.3,1.3,.5,1) */
    val EaseToast: Easing = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)

    /** Budget-Balken: cubic-bezier(.3,1,.4,1) */
    val EaseBudget: Easing = CubicBezierEasing(0.3f, 1f, 0.4f, 1f)

    /** CSS-Default „ease“: cubic-bezier(.25,.1,.25,1) — Rahmen-Lock, Scrim, Tiles, Press. */
    val EaseCss: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** CSS „ease-out“: cubic-bezier(0,0,.58,1) — Live-Punkt-Puls. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}

/** System-Animationsskala (0 = Animationen aus). */
val LocalMotionScale = compositionLocalOf { 1f }

/** Tween mit §11-Dauer, skaliert mit der System-Animationsskala. */
@Composable
fun <T> motionTween(durationMs: Int, easing: Easing): TweenSpec<T> {
    val scale = LocalMotionScale.current
    return tween(durationMillis = (durationMs * scale).toInt().coerceAtLeast(1), easing = easing)
}

@Composable
fun FxTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motionScale = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    CompositionLocalProvider(LocalMotionScale provides motionScale) {
        content()
    }
}
