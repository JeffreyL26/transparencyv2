package com.transparency.fxlens.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.transparency.fxlens.ui.theme.Jakarta
import com.transparency.fxlens.ui.theme.Motion
import com.transparency.fxlens.ui.theme.Tokens
import com.transparency.fxlens.ui.theme.Txt
import com.transparency.fxlens.ui.theme.motionTween

/**
 * Bottom-Sheet-Gerüst (app.css .sheet): Scrim rgba(16,24,12,0.34) mit
 * Fade 0.2 s, Sheet surface, Radius 28/28/42/42, Padding 10/16/26,
 * Einfahren 0.28 s cubic-bezier(.2,.9,.3,1), Grab-Handle.
 * Schließen entfernt sofort (wie Referenz — nur Eintritts-Animation).
 */
@Composable
fun SheetScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Zurück-Taste schließt das oberste Sheet (z. B. „Neue Liste") statt durchzufallen (§7).
    BackHandler(onBack = onDismiss)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val slide by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = motionTween(280, Motion.EaseSheet),
        label = "sheetRise",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = motionTween(200, Motion.EaseCss),
        label = "scrimFade",
    )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Tokens.SheetScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Hebt das gesamte Sheet dynamisch über die Tastatur (§2).
                .imePadding()
                .graphicsLayer { translationY = slide * size.height }
                .shadow(20.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 42.dp, bottomEnd = 42.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 42.dp, bottomEnd = 42.dp))
                .background(Tokens.Surface)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp)
                .navigationBarsPadding()
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .background(Tokens.Line, RoundedCornerShape(3.dp))
            )
            content()
        }
    }
}

/** Sheet-Titel: 13/700 ink-2, Margin 0 4 12. */
@Composable
fun SheetTitle(text: String) {
    Txt(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Tokens.Ink2,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
    )
}

/** Feld-Label (field-label): 11/700, +0.08em, uppercase, ink-3. */
@Composable
fun FieldLabel(text: String) {
    Txt(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.em,
        color = Tokens.Ink3,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp),
    )
}

/** Eingabe-Container (field): surface-warm, border line, radius 14, Padding 14/12. */
@Composable
fun Field(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.SurfaceWarm)
            .border(1.dp, Tokens.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Text-Eingabe im Field: 15sp Jakarta, Placeholder ink-3, Caret ink-3. */
@Composable
fun RowScope.FieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    Box(modifier.weight(1f)) {
        if (value.isEmpty()) {
            Txt(placeholder, fontSize = 15.sp, color = Tokens.Ink3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Jakarta,
                fontSize = 15.sp,
                color = Tokens.Ink,
            ),
            cursorBrush = SolidColor(Tokens.Ink3),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}
