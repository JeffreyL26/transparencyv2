package com.jbateam.scanconvert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.data.CustomCurrency
import com.jbateam.scanconvert.ui.components.Field
import com.jbateam.scanconvert.ui.components.FieldInput
import com.jbateam.scanconvert.ui.components.FieldLabel
import com.jbateam.scanconvert.ui.components.Flag
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcTrash
import com.jbateam.scanconvert.ui.components.PrimaryButton
import com.jbateam.scanconvert.ui.components.SheetScaffold
import com.jbateam.scanconvert.ui.components.SheetTitle
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt

/**
 * „Eigener Kurs" (Bottom-Sheet, über dem Picker, §F2): vorhandene Custom-Währungen
 * verwalten und neue anlegen — Name, optionale Abkürzung, Emoji (statt Flagge),
 * Referenzwährung + Kurs „1 Ref = X".
 */
@Composable
fun CustomRateSheet(
    customs: List<CustomCurrency>,
    allCodes: List<String>,
    onCreate: (name: String, abbrev: String?, emoji: String, refCode: String, perRef: Double) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val realCodes = remember(allCodes) { allCodes.filterNot { CurrencyMeta.isCustom(it) } }
    var name by remember { mutableStateOf("") }
    var abbrev by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf(realCodes.firstOrNull { it == "EUR" } ?: realCodes.firstOrNull() ?: "EUR") }
    var perRefText by remember { mutableStateOf("") }

    val perRef = perRefText.trim().replace(',', '.').toDoubleOrNull()
    val full = customs.size >= 5
    val valid = name.trim().isNotEmpty() && emoji.trim().isNotEmpty() && (perRef ?: 0.0) > 0.0
    val customLabel = stringResource(R.string.custom_default_label)

    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.custom_title))
        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            customs.forEach { c -> CustomRow(c, onDelete = { onDelete(c.code) }) }

            if (full) {
                Txt(
                    stringResource(R.string.custom_max),
                    fontSize = 12.5.sp,
                    color = Tokens.Ink2,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            } else {
                FieldLabel(stringResource(R.string.field_name))
                Field { FieldInput(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.custom_name_placeholder), autoFocus = true) }

                FieldLabel(stringResource(R.string.abbrev_optional))
                Field { FieldInput(value = abbrev, onValueChange = { abbrev = it }, placeholder = stringResource(R.string.abbrev_placeholder)) }

                FieldLabel(stringResource(R.string.field_emoji))
                Field {
                    FieldInput(value = emoji, onValueChange = { emoji = it.takeLast(2) }, placeholder = "🎰")
                    if (emoji.isNotBlank()) Txt(emoji, fontSize = 22.sp, maxLines = 1)
                }

                FieldLabel(stringResource(R.string.reference_currency))
                RefScroller(codes = realCodes, selected = ref, onSelect = { ref = it })

                FieldLabel(stringResource(R.string.field_rate))
                Field {
                    Txt(stringResource(R.string.custom_rate_prefix, ref), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Ink2, maxLines = 1)
                    FieldInput(value = perRefText, onValueChange = { perRefText = it }, placeholder = "0", keyboardType = KeyboardType.Decimal)
                    Txt(
                        abbrev.trim().ifBlank { name.trim().ifBlank { customLabel } },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Tokens.Ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                PrimaryButton(
                    text = stringResource(R.string.action_add),
                    enabled = valid,
                    modifier = Modifier.padding(top = 6.dp),
                    onClick = {
                        val p = perRef
                        if (p != null && p > 0.0 && name.trim().isNotEmpty() && emoji.trim().isNotEmpty()) {
                            onCreate(name, abbrev.ifBlank { null }, emoji, ref, p)
                            name = ""; abbrev = ""; emoji = ""; perRefText = ""
                        }
                    },
                )
            }
        }
    }
}

/** Vorhandene Custom-Währung: Emoji + Name/Code + Kursformel + Löschen. */
@Composable
private fun CustomRow(c: CustomCurrency, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Tokens.Surface)
            .border(1.dp, Tokens.Line, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Flag(c.code, 32.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(c.code, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Tokens.Ink, maxLines = 1)
                Txt(c.name, fontSize = 12.sp, color = Tokens.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Txt(
                stringResource(R.string.custom_rate_formula, c.refCode, trimNum(c.perRef), c.code),
                fontSize = 12.sp,
                fontFamily = Grotesk,
                letterSpacing = NumSpacing,
                color = Tokens.Ink3,
                maxLines = 1,
            )
        }
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
            Ic(IcTrash, tint = if (pressed) Tokens.Danger else Tokens.Ink3, modifier = Modifier.size(17.dp))
        }
    }
}

/** Horizontaler Flaggen-Scroller zur Wahl der Referenzwährung. */
@Composable
private fun RefScroller(codes: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(codes, key = { it }) { code ->
            val sel = code == selected
            Row(
                Modifier
                    .scaleClick(onClick = { onSelect(code) })
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Tokens.AccentSoft else Tokens.Surface)
                    .border(1.5.dp, if (sel) Tokens.Accent else Tokens.Line, RoundedCornerShape(12.dp))
                    .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Flag(code, 24.dp)
                Txt(code, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Tokens.Ink)
            }
        }
    }
}

private fun trimNum(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
