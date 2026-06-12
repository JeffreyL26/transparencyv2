package com.transparency.fxlens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transparency.fxlens.data.CurrencyMeta
import com.transparency.fxlens.domain.CreateMode
import com.transparency.fxlens.domain.TravelList
import com.transparency.fxlens.domain.convert
import com.transparency.fxlens.domain.fmt
import com.transparency.fxlens.domain.total
import com.transparency.fxlens.ui.components.Field
import com.transparency.fxlens.ui.components.FieldInput
import com.transparency.fxlens.ui.components.FieldLabel
import com.transparency.fxlens.ui.components.Flag
import com.transparency.fxlens.ui.components.Ic
import com.transparency.fxlens.ui.components.IcPlus
import com.transparency.fxlens.ui.components.PrimaryButton
import com.transparency.fxlens.ui.components.SheetScaffold
import com.transparency.fxlens.ui.components.SheetTitle
import com.transparency.fxlens.ui.components.scaleClick
import com.transparency.fxlens.ui.theme.Grotesk
import com.transparency.fxlens.ui.theme.Jakarta
import com.transparency.fxlens.ui.theme.NumSpacing
import com.transparency.fxlens.ui.theme.Tokens
import com.transparency.fxlens.ui.theme.Txt

/* ============================================================
   Screen 4 — „Zu Liste hinzufügen" (Bottom-Sheet)
   ============================================================ */

@Composable
fun AddToListSheet(
    from: String,
    to: String,
    raw: Double,
    rates: Map<String, Double>,
    lists: List<TravelList>,
    onAdd: (String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    val conv = convert(raw, from, to, rates)
    SheetScaffold(onDismiss = onClose) {
        SheetTitle("Zu Liste hinzufügen")

        // amount-chip
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Tokens.SurfaceWarm)
                .border(1.dp, Tokens.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Txt(
                    fmt(conv, to),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Grotesk,
                    letterSpacing = NumSpacing,
                    lineHeight = 26.sp,
                    color = Tokens.Ink,
                )
                Txt(
                    "aus ${fmt(raw, from)} gescannt",
                    fontSize = 12.sp,
                    color = Tokens.Ink2,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Flag(to, 38.dp)
        }

        Column(
            Modifier
                .heightIn(max = 372.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (lists.isEmpty()) {
                SheetNote(to)
            }
            lists.forEach { list ->
                ListRow(list, onClick = { onAdd(list.id) })
            }
            DashedNewRow(text = "Neue $to-Liste", onClick = onNew)
        }
    }
}

/** .list-row — eine bestehende Liste der Zielwährung. */
@Composable
private fun ListRow(list: TravelList, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.Surface)
            .border(1.dp, Tokens.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Flag(list.currency, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(
                list.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Txt("${list.items.size} Positionen", fontSize = 12.sp, color = Tokens.Ink2)
        }
        Txt(
            fmt(list.total(), list.currency),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Grotesk,
            letterSpacing = NumSpacing,
            color = Tokens.AccentDeep,
            maxLines = 1,
        )
    }
}

/** .sheet-note — Hinweis bei leerer Listenauswahl, {to} fett in Ink. */
@Composable
private fun SheetNote(to: String) {
    val text = buildAnnotatedString {
        append("Noch keine Liste in ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Tokens.Ink)) { append(to) }
        append(". Lege eine an, um Preise in dieser Währung zu sammeln.")
    }
    BasicText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        style = TextStyle(
            fontFamily = Jakarta,
            fontSize = 12.5.sp,
            lineHeight = 18.75.sp,
            color = Tokens.Ink2,
            textAlign = TextAlign.Center,
        ),
    )
}

/** .list-row.new — gestrichelte „Neue …-Liste"-Zeile. */
@Composable
private fun DashedNewRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = onClick)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val dash = 3.dp.toPx()
                drawRoundRect(
                    color = Tokens.Line,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    ) {
        Ic(IcPlus, Tokens.AccentDeep, Modifier.size(19.dp))
        Txt(text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Tokens.AccentDeep)
    }
}

/* ============================================================
   Screen 5 — „Neue Liste" (Bottom-Sheet, z65 über dem Panel)
   ============================================================ */

@Composable
fun CreateListSheet(
    mode: CreateMode,
    currency: String,
    allCodes: List<String>,
    onCreate: (name: String, currency: String, budget: Double?) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf(currency) }
    var budget by remember { mutableStateOf("") }

    SheetScaffold(onDismiss = onClose) {
        SheetTitle("Neue Liste")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldLabel("Name")
            Field {
                FieldInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "z. B. Hongkong Reise",
                    autoFocus = true,
                )
            }

            FieldLabel("Währung der Liste")
            if (mode == CreateMode.ADD) {
                FixedCurrencyField(currency)
            } else {
                CurScroller(allCodes = allCodes, selected = cur, onSelect = { cur = it })
            }

            FieldLabel("Budget (optional)")
            BudgetField(value = budget, onValueChange = { budget = it }, currency = cur)

            PrimaryButton(
                text = if (mode == CreateMode.ADD) "Erstellen & hinzufügen" else "Liste erstellen",
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.padding(top = 6.dp),
                onClick = { onCreate(name.trim(), cur, parseBudget(budget)) },
            )
        }
    }
}

/* ============================================================
   Screen 7 (Verwalten) — „Liste bearbeiten" (Bottom-Sheet, z65)
   ============================================================ */

@Composable
fun EditListSheet(
    list: TravelList,
    onSave: (name: String, budget: Double?) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(list.name) }
    var budget by remember { mutableStateOf(budgetText(list.budget)) }
    var confirm by remember { mutableStateOf(false) }

    SheetScaffold(onDismiss = onClose) {
        SheetTitle("Liste bearbeiten")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldLabel("Name")
            Field {
                FieldInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Listenname",
                    autoFocus = true,
                )
            }

            FieldLabel("Währung der Liste")
            FixedCurrencyField(list.currency)

            FieldLabel("Budget (optional)")
            BudgetField(value = budget, onValueChange = { budget = it }, currency = list.currency)

            PrimaryButton(
                text = "Speichern",
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.padding(top = 6.dp),
                onClick = { onSave(name.trim(), parseBudget(budget)) },
            )

            if (!confirm) {
                // .btn-danger — transparent, volle Breite
                Box(
                    Modifier
                        .fillMaxWidth()
                        .scaleClick(scale = 0.99f, onClick = { confirm = true })
                        .clip(RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("Liste löschen", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tokens.Danger)
                }
            } else {
                // .confirm-row — Inline-Bestätigung
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("Wirklich löschen?", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConfirmActionBtn("Abbrechen", bg = Tokens.SurfaceWarm, fg = Tokens.Ink2) { confirm = false }
                        ConfirmActionBtn("Löschen", bg = Tokens.Danger, fg = Color.White, onClick = onDelete)
                    }
                }
            }
        }
    }
}

/* ============================================================
   gemeinsame Bausteine
   ============================================================ */

/** Fixe Listen-Währung: Chip mit Flagge, „{code} · {name}" und „fest". */
@Composable
private fun FixedCurrencyField(code: String) {
    Field {
        Flag(code, 26.dp)
        Txt(
            "$code · ${CurrencyMeta.info(code).name}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Txt("fest", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Tokens.Ink3)
    }
}

/** Budget-Eingabe mit Währungssymbol als Suffix (.field .unit). */
@Composable
private fun BudgetField(value: String, onValueChange: (String) -> Unit, currency: String) {
    Field {
        FieldInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
        )
        Txt(
            CurrencyMeta.info(currency).sym,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Grotesk,
            color = Tokens.Ink3,
        )
    }
}

/** Horizontaler Flaggen-Scroller (CurScroller aus v2.jsx). */
@Composable
private fun CurScroller(allCodes: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(allCodes, key = { it }) { code ->
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

/** Bestätigungs-Button der confirm-row: 13/700, Radius 12, Padding 9/14. */
@Composable
private fun ConfirmActionBtn(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .scaleClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Txt(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** Budget-Parsing: Komma→Punkt, nur Werte > 0 (Referenz: `budget || null`), leer → null. */
private fun parseBudget(text: String): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.takeIf { it > 0.0 }
}

/** Vorbefüllung des Budget-Felds: ganzzahlig ohne „.0" (1500.0 → "1500"). */
private fun budgetText(budget: Double?): String = when {
    budget == null -> ""
    budget % 1.0 == 0.0 -> budget.toLong().toString()
    else -> budget.toString()
}
