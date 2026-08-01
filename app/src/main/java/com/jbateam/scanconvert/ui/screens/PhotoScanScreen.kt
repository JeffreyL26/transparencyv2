package com.jbateam.scanconvert.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.MainViewModel
import com.jbateam.scanconvert.PhotoScanStatus
import com.jbateam.scanconvert.PhotoScanUi
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.domain.convert
import com.jbateam.scanconvert.domain.fmtNum
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcBack
import com.jbateam.scanconvert.ui.components.IcPlus
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import kotlin.math.min

/**
 * Foto-Scan-Screen (§Galerie-Scan, Google-Translate-Foto-Modus): das gewählte Foto
 * füllt den Screen; über jeder erkannten Zahl liegt ein Overlay-Chip mit dem in die
 * Zielwährung umgerechneten Betrag. Tippen auf ein Overlay öffnet die Aktions-Karte
 * (Bearbeiten / Zu Liste / Entfernen); unten sammelt „Alle zu Liste hinzufügen“.
 *
 * Das Bild ist zoom- und verschiebbar (Pinch + Drag); die Overlays folgen der
 * Transformation, da sie in derselben Ebene wie das Bild positioniert werden.
 */
@Composable
fun PhotoScanScreen(vm: MainViewModel, scan: PhotoScanUi) {
    BackHandler(onBack = vm::closePhoto)

    // Aktuell angetipptes Overlay (Aktions-Karte); null = keins. Hier oben gehalten,
    // damit die Aktions-Karte ÜBER Kopf-Button und „Alle hinzufügen“-Leiste liegt.
    var activeId by remember(scan.uri) { mutableStateOf<Int?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF14110D))
    ) {
        when (scan.status) {
            PhotoScanStatus.LOADING -> PhotoStatusText(stringResource(R.string.gallery_scanning))
            PhotoScanStatus.ERROR -> PhotoStatusText(stringResource(R.string.gallery_scan_error))
            PhotoScanStatus.DONE -> PhotoWithOverlays(
                vm = vm,
                scan = scan,
                activeId = activeId,
                onToggle = { id -> activeId = if (activeId == id) null else id },
            )
        }

        // Kopf: Zurück-Pfeil (über Scrim, damit auf hellem Foto lesbar).
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Tokens.GlassStrong)
                    .scaleClick(onClick = vm::closePhoto),
                contentAlignment = Alignment.Center,
            ) {
                Ic(IcBack, tint = Tokens.Ink2, modifier = Modifier.size(22.dp))
            }
        }

        // Fuß: „Alle zu Liste hinzufügen“, nur wenn Overlays vorhanden.
        if (scan.status == PhotoScanStatus.DONE && scan.detections.isNotEmpty()) {
            AddAllBar(
                count = scan.detections.size,
                onClick = vm::openAddAll,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Aktions-Karte des angetippten Overlays — als oberste Schicht (modaler Scrim
        // deckt Kopf-Button und Leiste ab).
        activeId?.let { id ->
            scan.detections.find { it.id == id }?.let { det ->
                OverlayActionSheet(det = det, vm = vm, onClose = { activeId = null })
            }
        }
    }
}

/** Bild + positionierte Overlay-Chips innerhalb einer gemeinsam transformierten Ebene. */
@Composable
private fun PhotoWithOverlays(
    vm: MainViewModel,
    scan: PhotoScanUi,
    activeId: Int?,
    onToggle: (Int) -> Unit,
) {
    val bitmap = scan.bitmap ?: return
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    // Zoom/Pan-State (Pinch + Drag). Reset bei neuem Foto.
    var scale by remember(scan.uri) { mutableStateOf(1f) }
    var offset by remember(scan.uri) { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            // Oben (Zurück-Button) und unten (Alle-hinzufügen-Leiste) Platz reservieren,
            // damit keine Overlays hinter den Bedienelementen liegen bzw. deren Taps
            // fangen. System-Insets + fixe Zusatzhöhe der Bedienelemente. Das Bild
            // wird in den verbleibenden Bereich eingepasst.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 52.dp, bottom = 92.dp)
            .onSizeChanged { container = it }
            .pointerInput(scan.uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale <= 1.01f) {
                        scale = newScale
                        offset = Offset.Zero
                    } else {
                        // Pan begrenzen, damit die Bildränder nicht in den Container
                        // hineinwandern (Bild bliebe sonst leer aus dem Sichtfeld).
                        val fit = min(container.width / imgW, container.height / imgH)
                        val dispW = imgW * fit * newScale
                        val dispH = imgH * fit * newScale
                        val maxX = ((dispW - container.width) / 2f).coerceAtLeast(0f)
                        val maxY = ((dispH - container.height) / 2f).coerceAtLeast(0f)
                        val next = offset + pan
                        offset = Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
                        scale = newScale
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (container == IntSize.Zero) return@Box

        // „fit“-Abbildung des Bildes in den Container (Contain), dann Zoom/Pan darüber.
        val fit = min(container.width / imgW, container.height / imgH)
        val baseScale = fit * scale
        val dispW = imgW * baseScale
        val dispH = imgH * baseScale
        // Linke/obere Ecke des dargestellten Bildes im Container (zentriert + Pan).
        val originX = (container.width - dispW) / 2f + offset.x
        val originY = (container.height - dispH) / 2f + offset.y

        val density = LocalDensity.current

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        // Bild-Pixel → Container-Pixel: origin + boxPx * baseScale. Absolute Offsets
        // (kein start/end), damit die Overlays unter RTL nicht spiegeln (§i18n).
        // Chips an TopStart verankert, danach per graphicsLayer verschoben.
        scan.detections.forEach { det ->
            val cxPx = originX + det.box.centerX() * baseScale
            val cyPx = originY + det.box.centerY() * baseScale
            val boxHpx = det.box.height() * baseScale
            OverlayChip(
                text = overlayText(det.value, vm),
                centerXpx = cxPx,
                centerYpx = cyPx,
                heightHint = with(density) { boxHpx.toDp() },
                active = activeId == det.id,
                onClick = { onToggle(det.id) },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/** Umgerechneter Betrag „→ 12,90 USD“ für den Chip. */
private fun overlayText(raw: Double, vm: MainViewModel): String {
    val conv = convert(raw, vm.from, vm.to, vm.rates.value.rates)
    return fmtNum(conv, vm.to) + " " + vm.to
}

/**
 * Ein Overlay-Chip, zentriert über der erkannten Zahl. [modifier] muss den Chip am
 * Container-Ursprung (TopStart) verankern; die Positionierung erfolgt danach über
 * absolute Pixel-Offsets in der Bildebene (kein start/end → RTL-sicher). Die
 * Chip-Größe skaliert dezent mit der Boxhöhe, bleibt aber lesbar begrenzt.
 */
@Composable
private fun OverlayChip(
    text: String,
    centerXpx: Float,
    centerYpx: Float,
    heightHint: androidx.compose.ui.unit.Dp,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontSize = heightHint.value.coerceIn(11f, 20f).sp
    var chipSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .graphicsLayer {
                // Chip um seinen Mittelpunkt auf (centerXpx, centerYpx) legen.
                translationX = centerXpx - chipSize.width / 2f
                translationY = centerYpx - chipSize.height / 2f
            }
            .onSizeChanged { chipSize = it }
            .shadow(4.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) Tokens.AccentDeep else Tokens.Accent)
            .border(
                if (active) 2.dp else 1.dp,
                Color.White.copy(alpha = if (active) 0.9f else 0.5f),
                RoundedCornerShape(7.dp),
            )
            .scaleClick(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Txt(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = Grotesk,
            letterSpacing = NumSpacing,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** „Alle zu Liste hinzufügen“-Leiste am unteren Rand. */
@Composable
private fun AddAllBar(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Tokens.AccentGlow, spotColor = Tokens.AccentGlow)
            .clip(shape)
            .background(Tokens.Accent)
            .scaleClick(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(IcPlus, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Txt(
            stringResource(R.string.add_all_to_list, count),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun PhotoStatusText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Txt(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
    }
}
