package com.jbateam.scanconvert.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcChevron
import com.jbateam.scanconvert.ui.components.SheetScaffold
import com.jbateam.scanconvert.ui.components.SheetTitle
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt

/**
 * Einstellungen (§7.2). Einstieg über das Zahnrad im ListsPanel-Kopf. Enthält
 * Upgrade/Restore, den von UMP vorgeschriebenen Privacy-Options-Eintrag (nur wenn
 * erforderlich), die Sprachwahl und die rechtlichen Links (Play verlangt eine
 * Datenschutz-URL bei Ads/IAP).
 */
@Composable
fun SettingsSheet(
    isAdFree: Boolean,
    privacyOptionsRequired: Boolean,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onLanguage: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTerms: () -> Unit,
    onClose: () -> Unit,
) {
    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.settings_title))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isAdFree) {
                SettingRow(
                    title = stringResource(R.string.settings_upgrade),
                    subtitle = stringResource(R.string.settings_upgrade_sub),
                    accent = true,
                    onClick = onUpgrade,
                )
            }
            SettingRow(title = stringResource(R.string.restore_purchases), onClick = onRestore)
            SettingRow(title = stringResource(R.string.settings_language), onClick = onLanguage)
            if (privacyOptionsRequired) {
                SettingRow(title = stringResource(R.string.privacy_options), onClick = onPrivacyOptions)
            }

            Spacer(Modifier.size(4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LinkText(stringResource(R.string.settings_privacy_policy), onPrivacyPolicy)
                LinkText(stringResource(R.string.settings_terms), onTerms)
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(shape)
            .background(if (accent) Tokens.AccentSoft else Tokens.Surface)
            .border(1.dp, if (accent) Tokens.Accent else Tokens.Line, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (accent) Tokens.AccentInk else Tokens.Ink,
            )
            if (subtitle != null) {
                Txt(subtitle, modifier = Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Tokens.Ink2)
            }
        }
        Ic(IcChevron, tint = Tokens.Ink3, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Box(Modifier.scaleClick(scale = 0.98f, onClick = onClick)) {
        Txt(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tokens.Ink2,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        )
    }
}
