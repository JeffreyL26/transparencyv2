package com.jbateam.scanconvert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt

private val CardShadow = Color(0x421E281C)

/**
 * Banner-Anzeige (300×250) als eigene Karte, gestylt wie [NativeAdCard] (Tokens.Surface,
 * Radius 20, „Anzeige"-Label oben links). Anders als bei der Native-Ad lädt sich die
 * [AdView] selbst beim Eintritt in die Komposition — kein separater Loader/State nötig,
 * dafür wird sie beim Verlassen der Komposition zerstört ([AndroidView] `onRelease`).
 */
@Composable
fun BannerAdCard(adUnitId: String, adLabel: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .wrapContentWidth()
            .shadow(18.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
            .background(Tokens.Surface, shape)
            .border(1.dp, Tokens.Line, shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(
            adLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
            color = Tokens.AccentDeep,
            modifier = Modifier
                .align(Alignment.Start)
                .clip(RoundedCornerShape(6.dp))
                .background(Tokens.AccentSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        AndroidView(
            modifier = Modifier.padding(top = 10.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.MEDIUM_RECTANGLE)
                    this.adUnitId = adUnitId
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
