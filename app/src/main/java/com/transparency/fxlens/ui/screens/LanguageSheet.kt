package com.transparency.fxlens.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transparency.fxlens.R
import com.transparency.fxlens.data.LocaleStore
import com.transparency.fxlens.ui.components.Flag
import com.transparency.fxlens.ui.components.Ic
import com.transparency.fxlens.ui.components.IcCheck
import com.transparency.fxlens.ui.components.SheetScaffold
import com.transparency.fxlens.ui.components.SheetTitle
import com.transparency.fxlens.ui.components.scaleClick
import com.transparency.fxlens.ui.theme.Tokens
import com.transparency.fxlens.ui.theme.Txt

/**
 * Sprachauswahl (§F5): Flaggen-Buttons mit Sprachnamen in der aktuell eingestellten
 * Sprache. Die Wahl ändert sofort alle App-Texte (Activity-Recreate).
 */
@Composable
fun LanguageSheet(current: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.language_title))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LocaleStore.SUPPORTED.forEach { lang ->
                LanguageRow(
                    flag = LocaleStore.FLAGS[lang] ?: lang,
                    name = stringResource(langNameRes(lang)),
                    selected = lang == current,
                    onClick = { onSelect(lang) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(flag: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(shape)
            .background(if (selected) Tokens.AccentSoft else Tokens.Surface)
            .border(1.5.dp, if (selected) Tokens.Accent else Tokens.Line, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Flag(flag, 30.dp)
        Txt(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.Ink)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Ic(IcCheck, tint = Tokens.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun langNameRes(lang: String): Int = when (lang) {
    "de" -> R.string.lang_de
    "en" -> R.string.lang_en
    "es" -> R.string.lang_es
    "fr" -> R.string.lang_fr
    "pt" -> R.string.lang_pt
    "ar" -> R.string.lang_ar
    else -> R.string.lang_ja
}
