package com.jbateam.scanconvert.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.nativead.NativeAd
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.domain.ListItem
import com.jbateam.scanconvert.domain.TravelList
import com.jbateam.scanconvert.domain.fmt
import com.jbateam.scanconvert.domain.fmtNum
import com.jbateam.scanconvert.domain.total
import com.jbateam.scanconvert.ui.components.BudgetBar
import com.jbateam.scanconvert.ui.components.Flag
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcBack
import com.jbateam.scanconvert.ui.components.IcChevron
import com.jbateam.scanconvert.ui.components.IcClose
import com.jbateam.scanconvert.ui.components.IcEdit
import com.jbateam.scanconvert.ui.components.IcGear
import com.jbateam.scanconvert.ui.components.IcList
import com.jbateam.scanconvert.ui.components.IcLock
import com.jbateam.scanconvert.ui.components.IcPlus
import com.jbateam.scanconvert.ui.components.IcShare
import com.jbateam.scanconvert.ui.components.IcTrash
import com.jbateam.scanconvert.ui.components.IconBtn
import com.jbateam.scanconvert.ui.components.NativeAdCard
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.LocalMotionScale
import com.jbateam.scanconvert.ui.theme.Motion
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt
import com.jbateam.scanconvert.ui.theme.motionTween
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** shadow-card: rgba(30,40,28,0.26) */
private val CardShadow = Color(0x421E281C)

/**
 * Listen-Panel (Handoff §13, Screens 6 + 7): Vollbild, slidet von unten
 * (§11: 400 ms, cubic-bezier(.2,.9,.3,1)). Immer komponiert, damit die
 * Slide-Animation auch beim Schließen läuft.
 */
@Composable
fun ListsPanel(
    show: Boolean,
    lists: List<TravelList>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit,
    onNew: () -> Unit,
    canCreateList: Boolean,
    isAdFree: Boolean,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onExport: (String) -> Unit,
    nativeAd: NativeAd?,
    onEdit: (String) -> Unit,
    onDeleteItem: (listId: String, itemId: String) -> Unit,
    onEditItem: (listId: String, itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p by animateFloatAsState(
        targetValue = if (show) 0f else 1f,
        animationSpec = motionTween(400, Motion.EaseSheet),
        label = "panelSlide",
    )
    val list = lists.find { it.id == selectedId }

    BackHandler(enabled = show) {
        if (list != null) onSelect(null) else onClose()
    }

    Box(modifier.graphicsLayer { translationY = p * size.height }) {
        // Vollständig ausgeblendet keinen Inhalt rendern — sonst überdeckt
        // das unsichtbare Panel den Scanner und fängt Klicks ab.
        if (p < 0.999f) {
            val bgInteraction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Tokens.Canvas)
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                0f to Tokens.RadialPanelTop,
                                0.7f to Tokens.RadialPanelTop.copy(alpha = 0f),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.width * 1.2f,
                            )
                        )
                    }
                    // Klicks konsumieren, damit nichts zum Scanner durchfällt.
                    .clickable(interactionSource = bgInteraction, indication = null, onClick = {})
            ) {
                if (list != null) {
                    DetailHead(list = list, onSelect = onSelect, onEdit = onEdit, onExport = onExport, onClose = onClose)
                    DetailBody(list = list, onDeleteItem = onDeleteItem, onEditItem = onEditItem)
                } else {
                    OverviewHead(onSettings = onSettings, onClose = onClose, isAdFree = isAdFree)
                    // Prominenter, animierter Werbefrei-CTA direkt in „Meine Listen" —
                    // öffnet die Pläne direkt, ohne Umweg über das Einstellungs-Menü.
                    if (!isAdFree) {
                        AdFreeCtaButton(
                            onClick = onUpgrade,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        )
                    }
                    OverviewBody(
                        lists = lists,
                        onSelect = onSelect,
                        onNew = onNew,
                        canCreateList = canCreateList,
                        nativeAd = nativeAd,
                    )
                }
            }
        }
    }
}

/* ---------- Kopf (.panel-head) ---------- */

@Composable
private fun HeadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Tokens.Line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(start = 18.dp, top = 56.dp, end = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun OverviewHead(onSettings: () -> Unit, onClose: () -> Unit, isAdFree: Boolean) {
    HeadRow {
        Column(Modifier.weight(1f)) {
            Txt(
                stringResource(R.string.lists_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
            )
            Txt(
                stringResource(R.string.lists_subtitle),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Tokens.Ink2,
            )
        }
        // Zahnrad nur für werbefreie Nutzer; sonst übernimmt der Werbefrei-CTA darunter
        // den Einstellungs-Einstieg (öffnet dasselbe Menü).
        if (isAdFree) IconBtn(IcGear, onClick = onSettings)
        IconBtn(IcClose, onClick = onClose)
    }
}

/**
 * Prominenter, animierter Werbefrei-CTA in der „Meine Listen"-Übersicht: breiter,
 * accent-gefüllter Button, der pro Zyklus EINMAL kurz wackelt + aufleuchtet (sonst ruhig)
 * — eine niedrigschwellige Einladung zum Freischalten. Öffnet das Einstellungs-Menü
 * (Pläne, Käufe wiederherstellen, Datenschutz). `prefers-reduced-motion` /
 * Animationsskala = 0 → statischer Glow ohne Wackeln (analog [LiveDot]).
 */
@Composable
private fun AdFreeCtaButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motionScale = LocalMotionScale.current
    val envelope: Float
    val wiggle: Float
    if (motionScale > 0f) {
        val transition = rememberInfiniteTransition(label = "adfreeCta")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((4200 * motionScale).toInt().coerceAtLeast(1), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "adfreeCta",
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
        modifier
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
private fun DetailHead(
    list: TravelList,
    onSelect: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onExport: (String) -> Unit,
    onClose: () -> Unit,
) {
    HeadRow {
        IconBtn(IcBack, onClick = { onSelect(null) })
        Column(Modifier.weight(1f)) {
            Txt(
                list.name,
                modifier = Modifier.padding(bottom = 2.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Txt(
                stringResource(R.string.positions_currency, list.items.size, list.currency),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Tokens.Ink2,
            )
        }
        // Export self-gated: ohne listExport öffnet der Tap die Paywall (§6.4).
        IconBtn(IcShare, onClick = { onExport(list.id) })
        IconBtn(IcEdit, onClick = { onEdit(list.id) })
        IconBtn(IcClose, onClick = onClose)
    }
}

/* ---------- Screen 6: Übersicht ---------- */

@Composable
private fun ColumnScope.OverviewBody(
    lists: List<TravelList>,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    canCreateList: Boolean,
    nativeAd: NativeAd?,
) {
    val adLabel = stringResource(R.string.ad_label)
    // Eine native Einheit nach den ersten 2–3 echten Karten (§5).
    val adAfter = if (lists.size >= 3) 2 else lists.lastIndex
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        if (lists.isEmpty()) EmptyState()
        lists.forEachIndexed { i, l ->
            ListCard(l, onClick = { onSelect(l.id) })
            if (nativeAd != null && i == adAfter) {
                NativeAdCard(
                    nativeAd = nativeAd,
                    adLabel = adLabel,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
        }
        if (canCreateList) NewListRow(onNew = onNew) else LockedNewListRow(onNew = onNew)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(Tokens.SurfaceWarm, RoundedCornerShape(18.dp))
                .border(1.dp, Tokens.Line, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Ic(IcList, tint = Tokens.Ink3, modifier = Modifier.size(26.dp))
        }
        Txt(
            stringResource(R.string.no_lists),
            modifier = Modifier.padding(top = 14.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.Ink,
        )
        Txt(
            stringResource(R.string.no_lists_hint),
            modifier = Modifier.padding(top = 5.dp),
            fontSize = 13.sp,
            color = Tokens.Ink2,
            lineHeight = 19.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ListCard(l: TravelList, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val total = l.total()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .scaleClick(scale = 0.995f, onClick = onClick)
            .shadow(18.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
            .background(Tokens.Surface, shape)
            .border(1.dp, Tokens.Line, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Flag(l.currency, 44.dp)
        Column(Modifier.weight(1f)) {
            Txt(
                l.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.01).em,
            )
            Txt(
                stringResource(R.string.positions_currency, l.items.size, l.currency),
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 12.5.sp,
                color = Tokens.Ink2,
            )
            l.budget?.let { b ->
                BudgetBar(
                    total = total,
                    budget = b,
                    currency = l.currency,
                    compact = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Txt(
            fmt(total, l.currency),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Grotesk,
            letterSpacing = NumSpacing,
        )
        Ic(IcChevron, tint = Tokens.Ink3, modifier = Modifier.size(18.dp))
    }
}

/** Gestrichelte „Neue Liste“-Zeile (.list-row.new). */
@Composable
private fun NewListRow(onNew: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .scaleClick(scale = 0.99f, onClick = onNew)
            .drawBehind {
                val inset = 0.5.dp.toPx()
                drawRoundRect(
                    color = Tokens.Line,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f,
                        ),
                    ),
                )
            }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    ) {
        Ic(IcPlus, tint = Tokens.AccentDeep, modifier = Modifier.size(19.dp))
        Txt(stringResource(R.string.new_list), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Tokens.AccentDeep)
    }
}

/**
 * Gesperrte „Neue Liste"-Kachel (§4): Schloss-Icon, „Neue Liste" gedämpft,
 * pulsierender Akzent-Glow-Rand (analog [com.jbateam.scanconvert.ui.components.LiveDot],
 * `prefers-reduced-motion` respektiert) und Hinweiszeile. Tap → Paywall (via onNew,
 * das bei erreichtem Limit die Paywall öffnet).
 */
@Composable
private fun LockedNewListRow(onNew: () -> Unit) {
    val motionScale = LocalMotionScale.current
    val pulse = if (motionScale > 0f) {
        val transition = rememberInfiniteTransition(label = "lockGlow")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((1800 * motionScale).toInt().coerceAtLeast(1), easing = Motion.EaseOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "lockGlow",
        )
        p
    } else 0.6f
    val glowAlpha = 0.28f + 0.62f * pulse
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .scaleClick(scale = 0.99f, onClick = onNew)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = Tokens.AccentGlow.copy(alpha = glowAlpha),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ic(IcLock, tint = Tokens.Accent, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Txt(
                stringResource(R.string.new_list),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.AccentDeep,
                modifier = Modifier.graphicsLayer { alpha = 0.55f },
            )
            Txt(
                stringResource(R.string.lists_locked_hint),
                fontSize = 12.sp,
                color = Tokens.Ink2,
            )
        }
    }
}

/* ---------- Screen 7: Detail ---------- */

@Composable
private fun ColumnScope.DetailBody(
    list: TravelList,
    onDeleteItem: (listId: String, itemId: String) -> Unit,
    onEditItem: (listId: String, itemId: String) -> Unit,
) {
    val total = list.total()
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        // Summen-Karte (.detail-total)
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
                .shadow(18.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
                .background(Tokens.Surface, shape)
                .border(1.dp, Tokens.Line, shape)
                .padding(20.dp),
        ) {
            Txt(
                stringResource(R.string.total),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = Tokens.Ink3,
            )
            Row(Modifier.padding(top = 4.dp)) {
                Txt(
                    fmtNum(total, list.currency),
                    modifier = Modifier.alignByBaseline(),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = (-0.03).em,
                )
                Txt(
                    CurrencyMeta.info(list.currency).sym,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 4.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = (-0.03).em,
                    color = Tokens.Ink2,
                )
            }
            list.budget?.let { b ->
                BudgetBar(
                    total = total,
                    budget = b,
                    currency = list.currency,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Txt(
            stringResource(R.string.positions_label),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 10.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.em,
            color = Tokens.Ink3,
        )

        if (list.items.isEmpty()) {
            Txt(
                stringResource(R.string.no_items_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 14.dp),
                fontSize = 12.5.sp,
                color = Tokens.Ink2,
                lineHeight = 18.75.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Neueste zuerst
        list.items.reversed().forEach { item ->
            ItemRow(
                item = item,
                currency = list.currency,
                onEdit = { onEditItem(list.id, item.id) },
                onDelete = { onDeleteItem(list.id, item.id) },
            )
        }
    }
}

@Composable
private fun ItemRow(item: ListItem, currency: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    val name = item.label?.takeIf { it.isNotBlank() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Tokens.Surface, shape)
            .border(1.dp, Tokens.Line, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Antippbarer Bereich → Position benennen/umbenennen (§3).
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .scaleClick(scale = 0.99f, onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Flag(item.from, 30.dp)
            if (name != null) {
                Column(Modifier.weight(1f)) {
                    Txt(
                        name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tokens.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Txt(stringResource(R.string.from_amount, fmtNum(item.raw, item.from), item.from), fontSize = 12.sp, color = Tokens.Ink2)
                }
                Txt(
                    fmt(item.value, currency),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = NumSpacing,
                    color = Tokens.AccentDeep,
                    maxLines = 1,
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Txt(
                        fmt(item.value, currency),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Grotesk,
                        letterSpacing = NumSpacing,
                        color = Tokens.Ink,
                    )
                    Txt(stringResource(R.string.from_amount, fmtNum(item.raw, item.from), item.from), fontSize = 12.sp, color = Tokens.Ink2)
                }
            }
        }
        // Lösch-Button (.item-del): Pressed-Look danger-soft/danger
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (pressed) Tokens.DangerSoft else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Ic(
                IcTrash,
                tint = if (pressed) Tokens.Danger else Tokens.Ink3,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
