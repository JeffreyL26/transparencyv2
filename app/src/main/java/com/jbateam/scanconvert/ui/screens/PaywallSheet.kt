package com.jbateam.scanconvert.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.billing.PaywallContext
import com.jbateam.scanconvert.data.billing.ProductInfo
import com.jbateam.scanconvert.data.billing.Products
import com.jbateam.scanconvert.ui.components.SheetScaffold
import com.jbateam.scanconvert.ui.components.SheetTitle
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Grotesk
import com.jbateam.scanconvert.ui.theme.NumSpacing
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt

/**
 * Paywall (§7.1). Zeigt die 4 Produkte mit dem von Google gelieferten
 * `formattedPrice` (NIE hartkodiert, §11). Full Premium ist als „Beliebteste
 * Wahl" markiert. Kontext-abhängige Headline.
 */
@Composable
fun PaywallSheet(
    context: PaywallContext,
    products: List<ProductInfo>,
    onBuy: (productId: String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    val byId = products.associateBy { it.id }
    SheetScaffold(onDismiss = onClose) {
        SheetTitle(stringResource(R.string.paywall_eyebrow))
        Txt(
            stringResource(
                when (context) {
                    PaywallContext.LISTS -> R.string.paywall_title_lists
                    PaywallContext.EXPORT -> R.string.paywall_title_export
                    PaywallContext.GENERIC -> R.string.paywall_title_generic
                }
            ),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Tokens.Ink,
        )
        Txt(
            stringResource(R.string.paywall_subtitle),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
            fontSize = 13.sp,
            color = Tokens.Ink2,
            lineHeight = 19.sp,
        )

        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Products.ALL.forEach { id ->
                ProductCard(
                    id = id,
                    price = byId[id]?.formattedPrice,
                    highlighted = id == Products.FULL_PREMIUM,
                    onClick = { onBuy(id) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .scaleClick(scale = 0.99f, onClick = onRestore)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(
                stringResource(R.string.restore_purchases),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.AccentDeep,
            )
        }
    }
}

@Composable
private fun ProductCard(id: String, price: String?, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .scaleClick(scale = 0.99f, onClick = onClick)
            .clip(shape)
            .background(if (highlighted) Tokens.AccentSoft else Tokens.Surface)
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) Tokens.Accent else Tokens.Line, shape)
            .padding(16.dp),
    ) {
        if (highlighted) {
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tokens.Accent)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Txt(
                    stringResource(R.string.paywall_popular),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(
                    stringResource(productNameRes(id)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Tokens.Ink,
                )
                Txt(
                    stringResource(productDescRes(id)),
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 12.5.sp,
                    color = Tokens.Ink2,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.padding(horizontal = 6.dp))
            Txt(
                price ?: stringResource(R.string.paywall_price_loading),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Grotesk,
                letterSpacing = NumSpacing,
                color = Tokens.AccentDeep,
            )
        }
    }
}

private fun productNameRes(id: String): Int = when (id) {
    Products.ADFREE -> R.string.product_adfree_name
    Products.VACATION_PASS -> R.string.product_vacation_name
    Products.BUSINESS -> R.string.product_business_name
    else -> R.string.product_full_name
}

private fun productDescRes(id: String): Int = when (id) {
    Products.ADFREE -> R.string.product_adfree_desc
    Products.VACATION_PASS -> R.string.product_vacation_desc
    Products.BUSINESS -> R.string.product_business_desc
    else -> R.string.product_full_desc
}
