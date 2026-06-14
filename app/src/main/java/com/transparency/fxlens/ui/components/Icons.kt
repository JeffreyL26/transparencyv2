package com.transparency.fxlens.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Stroke-Icons — Pfaddaten 1:1 aus den Referenz-SVGs (components.jsx / v2.jsx),
 * 24er-Viewport, Strichstärken 1.8–2.6, runde Kappen.
 */

@Composable
fun Ic(vector: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Image(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

private fun builder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
)

private fun ImageVector.Builder.strokePath(
    width: Float,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
    d: PathBuilder.() -> Unit,
) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = cap,
    strokeLineJoin = join,
    pathBuilder = d,
)

private fun ImageVector.Builder.fillPath(d: PathBuilder.() -> Unit) =
    path(fill = SolidColor(Color.Black), pathBuilder = d)

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
}

/** M12 5v14 M5 12h14 (2.4) */
val IcPlus: ImageVector by lazy {
    builder("IcPlus").apply {
        strokePath(2.4f) {
            moveTo(12f, 5f); verticalLineToRelative(14f)
            moveTo(5f, 12f); horizontalLineToRelative(14f)
        }
    }.build()
}

/** Listen-Icon: 3 Linien + 3 Punkte (2.0) */
val IcList: ImageVector by lazy {
    builder("IcList").apply {
        strokePath(2f) {
            moveTo(8f, 6f); horizontalLineToRelative(12f)
            moveTo(8f, 12f); horizontalLineToRelative(12f)
            moveTo(8f, 18f); horizontalLineToRelative(12f)
        }
        fillPath {
            circle(3.5f, 6f, 1.2f)
            circle(3.5f, 12f, 1.2f)
            circle(3.5f, 18f, 1.2f)
        }
    }.build()
}

/** Sprechblase mit drei Punkten (Sprachauswahl, §F4). */
val IcChat: ImageVector by lazy {
    builder("IcChat").apply {
        strokePath(1.9f) {
            moveTo(7f, 4f)
            horizontalLineTo(18f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 3f, dy1 = 3f)
            verticalLineTo(12f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -3f, dy1 = 3f)
            horizontalLineTo(8f)
            lineTo(4f, 19f)
            verticalLineTo(7f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 3f, dy1 = -3f)
            close()
        }
        fillPath {
            circle(8.8f, 9.3f, 1.0f)
            circle(12f, 9.3f, 1.0f)
            circle(15.2f, 9.3f, 1.0f)
        }
    }.build()
}

/** M6 6l12 12 M18 6 6 18 (2.2) */
val IcClose: ImageVector by lazy {
    builder("IcClose").apply {
        strokePath(2.2f) {
            moveTo(6f, 6f); lineToRelative(12f, 12f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }.build()
}

/** m15 6-6 6 6 6 (2.2) */
val IcBack: ImageVector by lazy {
    builder("IcBack").apply {
        strokePath(2.2f) {
            moveTo(15f, 6f); lineToRelative(-6f, 6f); lineToRelative(6f, 6f)
        }
    }.build()
}

/** m9 6 6 6-6 6 (2.2) */
val IcChevron: ImageVector by lazy {
    builder("IcChevron").apply {
        strokePath(2.2f) {
            moveTo(9f, 6f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f)
        }
    }.build()
}

/** Papierkorb (1.9) */
val IcTrash: ImageVector by lazy {
    builder("IcTrash").apply {
        strokePath(1.9f) {
            moveTo(4f, 7f); horizontalLineToRelative(16f)
            moveTo(9f, 7f); verticalLineTo(5f); horizontalLineToRelative(6f); verticalLineTo(7f)
            moveTo(6f, 7f); lineToRelative(1f, 13f); horizontalLineToRelative(10f); lineToRelative(1f, -13f)
        }
    }.build()
}

/** Globus (1.8) */
val IcGlobe: ImageVector by lazy {
    builder("IcGlobe").apply {
        strokePath(1.8f) {
            circle(12f, 12f, 9f)
            moveTo(3f, 12f); horizontalLineToRelative(18f)
            moveTo(12f, 3f)
            curveToRelative(2.8f, 3f, 2.8f, 15f, 0f, 18f)
            moveTo(12f, 3f)
            curveToRelative(-2.8f, 3f, -2.8f, 15f, 0f, 18f)
        }
    }.build()
}

/** Stift (1.9) */
val IcEdit: ImageVector by lazy {
    builder("IcEdit").apply {
        strokePath(1.9f) {
            moveTo(4f, 20f); horizontalLineToRelative(4f); lineTo(19f, 9f); lineToRelative(-4f, -4f); lineTo(4f, 16f); verticalLineToRelative(4f); close()
            moveTo(13.5f, 6.5f); lineToRelative(4f, 4f)
        }
    }.build()
}

/** Tausch-Pfeile (2.2) */
val IcSwap: ImageVector by lazy {
    builder("IcSwap").apply {
        strokePath(2.2f) {
            moveTo(7f, 4f); lineTo(4f, 7f); lineToRelative(3f, 3f)
            moveTo(4f, 7f); horizontalLineToRelative(12f)
            moveTo(17f, 20f); lineToRelative(3f, -3f); lineToRelative(-3f, -3f)
            moveTo(20f, 17f); horizontalLineTo(8f)
        }
    }.build()
}

/** Pfeil rechts (2.4) */
val IcArrow: ImageVector by lazy {
    builder("IcArrow").apply {
        strokePath(2.4f) {
            moveTo(5f, 12f); horizontalLineToRelative(14f)
            moveTo(13f, 6f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f)
        }
    }.build()
}

/** Häkchen (2.6) */
val IcCheck: ImageVector by lazy {
    builder("IcCheck").apply {
        strokePath(2.6f) {
            moveTo(5f, 13f); lineToRelative(4f, 4f); lineTo(19f, 7f)
        }
    }.build()
}

/** Lupe (2.0) */
val IcSearch: ImageVector by lazy {
    builder("IcSearch").apply {
        strokePath(2f) {
            circle(11f, 11f, 7f)
            moveTo(21f, 21f); lineToRelative(-4.3f, -4.3f)
        }
    }.build()
}

private fun ImageVector.Builder.pinBody(filled: Boolean) {
    if (filled) {
        path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9f, 10.6f); verticalLineTo(4f); horizontalLineToRelative(6f); verticalLineToRelative(6.6f)
            lineToRelative(2.2f, 3.4f); horizontalLineTo(6.8f); lineTo(9f, 10.6f); close()
        }
    } else {
        strokePath(1.8f) {
            moveTo(9f, 10.6f); verticalLineTo(4f); horizontalLineToRelative(6f); verticalLineToRelative(6.6f)
            lineToRelative(2.2f, 3.4f); horizontalLineTo(6.8f); lineTo(9f, 10.6f); close()
        }
    }
    strokePath(1.8f) {
        moveTo(12f, 17f); verticalLineToRelative(5f)
    }
}

/** Zahnrad (Einstellungen): Nabe + 8 Speichen (1.8). */
val IcGear: ImageVector by lazy {
    builder("IcGear").apply {
        strokePath(1.8f) {
            circle(12f, 12f, 3.2f)
        }
        strokePath(1.8f) {
            moveTo(12f, 3f); verticalLineTo(5.2f)
            moveTo(12f, 18.8f); verticalLineTo(21f)
            moveTo(3f, 12f); horizontalLineTo(5.2f)
            moveTo(18.8f, 12f); horizontalLineTo(21f)
            moveTo(5.6f, 5.6f); lineTo(7.1f, 7.1f)
            moveTo(16.9f, 16.9f); lineTo(18.4f, 18.4f)
            moveTo(18.4f, 5.6f); lineTo(16.9f, 7.1f)
            moveTo(7.1f, 16.9f); lineTo(5.6f, 18.4f)
        }
    }.build()
}

/** Schloss (gesperrt): Bügel + Korpus (1.9). */
val IcLock: ImageVector by lazy {
    builder("IcLock").apply {
        strokePath(1.9f) {
            // Bügel
            moveTo(8f, 10f); verticalLineTo(7.5f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 8f, dy1 = 0f)
            verticalLineTo(10f)
        }
        strokePath(1.9f) {
            // Korpus
            moveTo(6.5f, 10f); horizontalLineTo(17.5f); verticalLineTo(19f); horizontalLineTo(6.5f); close()
        }
    }.build()
}

/** Teilen/Export: Pfeil nach oben aus einer Ablage (1.9). */
val IcShare: ImageVector by lazy {
    builder("IcShare").apply {
        strokePath(1.9f) {
            moveTo(12f, 15f); verticalLineTo(4f)
            moveTo(8.5f, 7.5f); lineTo(12f, 4f); lineTo(15.5f, 7.5f)
            moveTo(5f, 12f); verticalLineTo(19f); horizontalLineTo(19f); verticalLineTo(12f)
        }
    }.build()
}

/** Pin, Umriss */
val IcPin: ImageVector by lazy {
    builder("IcPin").apply { pinBody(filled = false) }.build()
}

/** Pin, gefüllt (gepinnt) */
val IcPinFilled: ImageVector by lazy {
    builder("IcPinFilled").apply { pinBody(filled = true) }.build()
}
