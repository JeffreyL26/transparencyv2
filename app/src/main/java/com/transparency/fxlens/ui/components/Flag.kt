package com.transparency.fxlens.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transparency.fxlens.data.CurrencyMeta
import com.transparency.fxlens.ui.theme.Grotesk
import com.transparency.fxlens.ui.theme.Tokens
import com.transparency.fxlens.ui.theme.Txt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Runde Flagge (Handoff §14): lokales Asset, rund maskiert,
 * 1px-Innenrand rgba(0,0,0,0.08) + leichter Schatten.
 * Ohne Flaggenbild: Symbol-Fallback im Kreis (wie Prototyp).
 */

private val flagCache = ConcurrentHashMap<String, ImageBitmap>()
private val flagMisses = ConcurrentHashMap.newKeySet<String>()

@Composable
private fun rememberFlagBitmap(cc: String?): ImageBitmap? {
    if (cc == null) return null
    val context = LocalContext.current
    // produceState behält bei Key-Wechsel den alten value; deshalb pro cc explizit
    // neu setzen (sonst bleibt beim Swap die Flagge der vorherigen Währung stehen).
    val bmp by produceState<ImageBitmap?>(initialValue = flagCache[cc], key1 = cc) {
        flagCache[cc]?.let { value = it; return@produceState }
        if (cc in flagMisses) { value = null; return@produceState }
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("flags/$cc.png").use {
                    BitmapFactory.decodeStream(it).asImageBitmap()
                }
            }.getOrNull().also { decoded ->
                if (decoded != null) flagCache[cc] = decoded else flagMisses.add(cc)
            }
        }
    }
    return bmp
}

@Composable
fun Flag(code: String, size: Dp, modifier: Modifier = Modifier) {
    val emoji = CurrencyMeta.emoji(code)
    val info = remember(code) { CurrencyMeta.info(code) }
    val bitmap = if (emoji == null) rememberFlagBitmap(info.cc) else null
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Tokens.SurfaceWarm),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            // Custom-Währung: Emoji statt Flagge (§F2).
            Txt(
                text = emoji,
                fontSize = (size.value * 0.5f).coerceIn(12f, 26f).sp,
                maxLines = 1,
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = code,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Txt(
                text = info.sym,
                fontSize = (size.value * 0.34f).coerceIn(8f, 13f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Grotesk,
                color = Tokens.Ink2,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color(0x14000000), CircleShape)
        )
    }
}
