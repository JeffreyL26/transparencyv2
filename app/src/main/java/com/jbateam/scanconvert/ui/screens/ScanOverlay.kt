package com.jbateam.scanconvert.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.domain.ScanPhase
import com.jbateam.scanconvert.domain.convert
import com.jbateam.scanconvert.domain.fmtNum
import com.jbateam.scanconvert.domain.fmtPlain
import com.jbateam.scanconvert.domain.fmtRate
import com.jbateam.scanconvert.domain.parseAmount
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcArrow
import com.jbateam.scanconvert.ui.components.IcCheck
import com.jbateam.scanconvert.ui.components.IcEdit
import com.jbateam.scanconvert.ui.components.IcPlus
import com.jbateam.scanconvert.ui.components.LiveDot
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.LocalMotionScale
import com.jbateam.scanconvert.ui.theme.Motion
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import com.jbateam.scanconvert.ui.theme.motionTween

private val ScanBoxWidth = 230.dp
private val ScanBoxHeight = 116.dp

/**
 * Scan-Rahmen + Hint + Ergebnis-Karte (Screen 2, Punkte 4+6; Timings §11).
 * Außerhalb des Rahmens wird bewusst NICHT abgedunkelt (§12).
 */
@Composable
fun ScanLayer(
    phase: ScanPhase,
    from: String,
    to: String,
    rates: Map<String, Double>,
    dim: Boolean,
    onRescan: () -> Unit,
    onAdd: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locked = phase is ScanPhase.Locked

    // Letzte erkannte Werte merken, damit die Karte beim Ausfahren ihren Inhalt behält.
    var lastRaws by remember { mutableStateOf(listOf<Double>()) }
    (phase as? ScanPhase.Locked)?.let { if (it.raws.isNotEmpty()) lastRaws = it.raws }

    Box(modifier) {
        ScanBox(
            locked = locked,
            onRescan = onRescan,
            modifier = Modifier.align(Alignment.Center),
        )

        // Hint-Pill (scan-hint): nur im Scanning und ohne offene Overlays.
        val hintAlpha by animateFloatAsState(
            targetValue = if (!locked && !dim) 1f else 0f,
            animationSpec = motionTween(300, Motion.EaseCss),
            label = "hint",
        )
        if (hintAlpha > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .graphicsLayer { alpha = hintAlpha }
                    .shadow(6.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Tokens.HintBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                LiveDot(8.dp, color = Color.White)
                Txt(stringResource(R.string.scan_hint), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        ResultCard(
            locked = locked,
            raws = lastRaws,
            from = from,
            to = to,
            rates = rates,
            onAdd = onAdd,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Hebt die Karte über die Tastatur, wenn ein Betrag händisch angepasst wird.
                .imePadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 22.dp),
        )
    }
}

/** Scan-Box 230×116: Fenster, Eck-Winkel, Scan-Linie, Glow, „Erkannt“-Badge. */
@Composable
private fun ScanBox(locked: Boolean, onRescan: () -> Unit, modifier: Modifier = Modifier) {
    // Rahmen weiß→accent in 0.3 s (§11).
    val frameColor by animateColorAsState(
        targetValue = if (locked) Tokens.Accent else Color(0xE6FFFFFF),
        animationSpec = motionTween(300, Motion.EaseCss),
        label = "frame",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = motionTween(300, Motion.EaseCss),
        label = "glow",
    )

    Box(
        modifier
            .size(ScanBoxWidth, ScanBoxHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRescan,
            )
    ) {
        // Glow hinter dem Fenster (scan-glow + box-shadow accent-glow)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowAlpha }
                .shadow(26.dp, RoundedCornerShape(18.dp), ambientColor = Tokens.AccentGlow, spotColor = Tokens.AccentGlow)
                .clip(RoundedCornerShape(18.dp))
                .background(Tokens.AccentSoft)
        )

        // Fenster (win): border 2.5, Radius 18
        Box(
            Modifier
                .fillMaxSize()
                .border(2.5.dp, frameColor, RoundedCornerShape(18.dp))
        )

        // 4 Eck-Winkel (win-corner): 18×18, Strich 3, Inset 7
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val inset = 7.dp.toPx()
                    val len = 18.dp.toPx()
                    val sw = 3.dp.toPx()
                    val w = size.width
                    val h = size.height
                    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(frameColor, Offset(x, y), Offset(x + len * dx, y), sw, StrokeCap.Square)
                        drawLine(frameColor, Offset(x, y), Offset(x, y + len * dy), sw, StrokeCap.Square)
                    }
                    corner(inset, inset, 1f, 1f)
                    corner(w - inset, inset, -1f, 1f)
                    corner(inset, h - inset, 1f, -1f)
                    corner(w - inset, h - inset, -1f, -1f)
                }
        )

        // Scan-Linie (scanline): Y 8→(H−10)→8, 1.5 s ease-in-out loop; bei locked aus.
        val motionScale = LocalMotionScale.current
        if (!locked && motionScale > 0f) {
            val transition = rememberInfiniteTransition(label = "sweep")
            val p by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        (750 * motionScale).toInt().coerceAtLeast(1),
                        easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "sweep",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .offset {
                        // CSS sweep: top 8px → calc(100% − 10px)
                        val travel = (ScanBoxHeight - 10.dp - 8.dp).toPx()
                        androidx.compose.ui.unit.IntOffset(0, (8.dp.toPx() + travel * p).toInt())
                    }
                    .graphicsLayer { alpha = 0.4f + 0.6f * p }
                    .height(2.dp)
                    .drawBehind {
                        // weicher Glow um die Linie
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Tokens.AccentGlow, Color.Transparent)
                            ),
                            topLeft = Offset(0f, -5.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width, 12.dp.toPx()),
                        )
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White, Color.Transparent)
                            )
                        )
                    }
            )
        }

        // „Erkannt“-Badge (lockbadge): scale 0→1 mit Overshoot (§11).
        val badgeScale by animateFloatAsState(
            targetValue = if (locked) 1f else 0f,
            animationSpec = motionTween(300, Motion.EaseBadge),
            label = "badge",
        )
        if (badgeScale > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-13).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .shadow(6.dp, RoundedCornerShape(999.dp), ambientColor = Tokens.AccentGlow, spotColor = Tokens.AccentGlow)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Tokens.Accent)
                    .padding(horizontal = 11.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Ic(IcCheck, tint = Color.White, modifier = Modifier.size(16.dp))
                Txt(stringResource(R.string.scan_detected), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Hinweis am unteren Rahmen-Rand: bei erkanntem Wert „Tippen, um neu zu scannen".
        val rescanHintAlpha by animateFloatAsState(
            targetValue = if (locked) 1f else 0f,
            animationSpec = motionTween(300, Motion.EaseCss),
            label = "rescanHint",
        )
        if (rescanHintAlpha > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 21.dp)
                    .graphicsLayer { alpha = rescanHintAlpha }
                    .clip(RoundedCornerShape(999.dp))
                    .background(Tokens.HintBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(
                    stringResource(R.string.scan_rescan),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Ergebnis-Karte (res-card): slidet bei locked ein (0.42 s, §11). Eine erkannte Zahl
 * wird prominent gezeigt (mit Add-Button), mehrere Zahlen gestapelt — jede Zeile mit
 * eigenem „+" zum Hinzufügen.
 */
@Composable
private fun ResultCard(
    locked: Boolean,
    raws: List<Double>,
    from: String,
    to: String,
    rates: Map<String, Double>,
    onAdd: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slide by animateFloatAsState(
        targetValue = if (locked) 0f else 1f,
        animationSpec = motionTween(420, Motion.EaseSheet),
        label = "resCard",
    )
    val cardShape = RoundedCornerShape(26.dp)
    val multi = raws.size > 1

    // Händisch korrigierbare Rohwerte: bei jedem neuen Scan (geänderte raws) zurückgesetzt.
    // Quelle der Wahrheit für Umrechnung UND „Hinzufügen", falls die OCR daneben lag.
    val effective = remember(raws) { mutableStateListOf<Double>().apply { addAll(raws) } }
    var editingIndex by remember(raws) { mutableStateOf<Int?>(null) }
    fun valueAt(i: Int): Double = effective.getOrElse(i) { raws.getOrElse(i) { 0.0 } }
    // Beim Entsperren (neu scannen) Bearbeitung beenden → Tastatur schließt sich.
    LaunchedEffect(locked) { if (!locked) editingIndex = null }

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = 1.4f * size.height * slide }
            .shadow(14.dp, cardShape)
            .clip(cardShape)
            .background(Tokens.GlassStrong)
            .border(1.dp, Color(0xB3FFFFFF), cardShape)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Kopfzeile: Status + aktueller Kurs.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(bottom = if (multi) 8.dp else 14.dp),
        ) {
            LiveDot(7.dp)
            Txt(
                if (multi) stringResource(R.string.res_count, raws.size) else stringResource(R.string.res_converted),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = Tokens.AccentDeep,
            )
            Spacer(Modifier.weight(1f))
            Txt("1 $from = ", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tokens.Ink2)
            Txt(
                fmtRate(from, to, rates),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Grotesk,
                color = Tokens.Ink,
            )
            Txt(" $to", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tokens.Ink2)
        }

        if (multi) {
            raws.forEachIndexed { i, _ ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .height(1.dp)
                            .background(Tokens.Line)
                    )
                }
                StackedRow(
                    raw = valueAt(i),
                    from = from,
                    to = to,
                    rates = rates,
                    editing = editingIndex == i,
                    onStartEdit = { editingIndex = i },
                    onValue = { v -> if (i < effective.size) effective[i] = v },
                    onDoneEdit = { editingIndex = null },
                    onAdd = { editingIndex = null; onAdd(valueAt(i)) },
                )
            }
        } else {
            SingleRow(
                raw = valueAt(0),
                from = from,
                to = to,
                rates = rates,
                editing = editingIndex == 0,
                onStartEdit = { editingIndex = 0 },
                onValue = { v -> if (effective.isEmpty()) effective.add(v) else effective[0] = v },
                onDoneEdit = { editingIndex = null },
            )
            AddBar(onClick = { editingIndex = null; onAdd(valueAt(0)) })
        }
    }
}

/**
 * Prominente Einzel-Umrechnung: VON-Betrag (antippbar → händisch korrigierbar) → ZU-Betrag.
 * Bei falsch erkannter Zahl tippt der Nutzer den VON-Betrag an und passt ihn an; die
 * Umrechnung rechts aktualisiert sich live und „Hinzufügen" übernimmt den korrigierten Wert.
 */
@Composable
private fun SingleRow(
    raw: Double,
    from: String,
    to: String,
    rates: Map<String, Double>,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onValue: (Double) -> Unit,
    onDoneEdit: () -> Unit,
) {
    val conv = convert(raw, from, to, rates)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                from,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
                color = Tokens.Ink3,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            if (editing) {
                AmountField(
                    value = raw,
                    code = from,
                    style = numberStyle(24.sp, Tokens.Ink2),
                    onValue = onValue,
                    onDone = onDoneEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    Modifier.scaleClick(scale = 0.97f, onClick = onStartEdit),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Txt(
                        fmtNum(raw, from),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Grotesk,
                        letterSpacing = NumSpacing,
                        color = Tokens.Ink2,
                        maxLines = 1,
                    )
                    Ic(IcEdit, tint = Tokens.Ink3, modifier = Modifier.size(15.dp))
                }
            }
        }
        Box(
            Modifier
                .size(34.dp)
                .background(Tokens.AccentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Ic(IcArrow, tint = Tokens.AccentDeep, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Txt(
                to,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
                color = Tokens.Ink3,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Txt(
                fmtNum(conv, to),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Grotesk,
                letterSpacing = NumSpacing,
                color = Tokens.Ink,
                maxLines = 1,
            )
        }
    }
}

/** Volle Add-Leiste (rc-add) für den Einzelfall. */
@Composable
private fun AddBar(onClick: () -> Unit) {
    val addShape = RoundedCornerShape(15.dp)
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .shadow(8.dp, addShape, ambientColor = Tokens.AccentGlow, spotColor = Tokens.AccentGlow)
            .clip(addShape)
            .background(Tokens.Accent)
            .scaleClick(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(IcPlus, tint = Color.White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(9.dp))
        Txt(
            stringResource(R.string.add_to_list),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
            color = Color.White,
        )
    }
}

/**
 * Kompakte Zeile im Mehrfach-Fall: umgerechneter Betrag + (antippbar/korrigierbare)
 * Herkunft + eigenes „+". Tippen auf die „aus …"-Zeile öffnet das Anpassen des Rohwerts.
 */
@Composable
private fun StackedRow(
    raw: Double,
    from: String,
    to: String,
    rates: Map<String, Double>,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onValue: (Double) -> Unit,
    onDoneEdit: () -> Unit,
    onAdd: () -> Unit,
) {
    val conv = convert(raw, from, to, rates)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(
                    fmtNum(conv, to),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = NumSpacing,
                    color = Tokens.Ink,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                Txt(
                    to,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tokens.Ink3,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (editing) {
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AmountField(
                        value = raw,
                        code = from,
                        style = numberStyle(13.sp, Tokens.Ink),
                        onValue = onValue,
                        onDone = onDoneEdit,
                        modifier = Modifier.weight(1f),
                    )
                    Txt(from, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Ink3)
                }
            } else {
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .scaleClick(scale = 0.98f, onClick = onStartEdit),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Txt(
                        stringResource(R.string.from_amount, fmtNum(raw, from), from),
                        fontSize = 12.sp,
                        color = Tokens.Ink2,
                    )
                    Ic(IcEdit, tint = Tokens.Ink3, modifier = Modifier.size(12.dp))
                }
            }
        }
        Box(
            Modifier
                .size(38.dp)
                .shadow(6.dp, CircleShape, ambientColor = Tokens.AccentGlow, spotColor = Tokens.AccentGlow)
                .clip(CircleShape)
                .background(Tokens.Accent)
                .scaleClick(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Ic(IcPlus, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Zahlen-TextStyle (Space Grotesk) für statische Anzeige und Eingabefeld identisch. */
private fun numberStyle(size: TextUnit, color: Color): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = FontWeight.Bold,
    fontFamily = Grotesk,
    letterSpacing = NumSpacing,
)

/**
 * Inline-Eingabefeld zum händischen Anpassen eines erkannten Betrags. Hält einen eigenen
 * Text-Puffer; jeder gültig geparste Wert wird sofort via [onValue] gemeldet (Umrechnung
 * aktualisiert live). Numerische Tastatur, „Fertig" schließt die Bearbeitung.
 */
@Composable
private fun AmountField(
    value: Double,
    code: String,
    style: TextStyle,
    onValue: (Double) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(fmtPlain(value, code)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { txt ->
            val filtered = txt.filter { it.isDigit() || it == ',' || it == '.' }
            text = filtered
            parseAmount(filtered)?.let(onValue)
        },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Tokens.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .widthIn(min = 32.dp)
            .focusRequester(focusRequester),
    )
}
