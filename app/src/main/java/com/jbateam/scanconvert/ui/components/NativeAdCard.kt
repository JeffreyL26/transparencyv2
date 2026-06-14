package com.jbateam.scanconvert.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.jbateam.scanconvert.R

/**
 * Native-Anzeige als Listen-Karte (§7.4). Bettet die XML-inflated [NativeAdView]
 * via AndroidView ein — der inflated Root ist load-bearing fürs Tracking; eine
 * reine Compose-Asset-Registrierung ist nicht unterstützt. Das „Anzeige"-Label
 * wird lokalisiert von außen gesetzt. Den Lebenszyklus der [NativeAd] besitzt der
 * Loader (zerstört sie beim Ersetzen/Verwerfen), daher hier kein destroy().
 */
@Composable
fun NativeAdCard(nativeAd: NativeAd, adLabel: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val adView = LayoutInflater.from(ctx)
                .inflate(R.layout.native_ad_card, null) as NativeAdView
            // inflate(..., null) verwirft die Root-LayoutParams → explizit setzen,
            // sonst kann die Breite im AndroidView kollabieren.
            adView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            adView.findViewById<TextView>(R.id.ad_label).text = adLabel
            adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline)
            adView.bodyView = adView.findViewById<TextView>(R.id.ad_body)
            adView.iconView = adView.findViewById<ImageView>(R.id.ad_icon)
            adView.callToActionView = adView.findViewById<Button>(R.id.ad_cta)
            bind(adView, nativeAd)
            adView
        },
        update = { adView -> bind(adView, nativeAd) },
    )
}

private fun bind(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline
    (adView.bodyView as? TextView)?.let { v ->
        val b = ad.body
        if (b == null) v.visibility = View.GONE
        else { v.text = b; v.visibility = View.VISIBLE }
    }
    (adView.iconView as? ImageView)?.let { v ->
        val icon = ad.icon
        if (icon == null) v.visibility = View.GONE
        else { v.setImageDrawable(icon.drawable); v.visibility = View.VISIBLE }
    }
    (adView.callToActionView as? Button)?.let { v ->
        val c = ad.callToAction
        if (c == null) v.visibility = View.GONE
        else { v.text = c; v.visibility = View.VISIBLE }
    }
    // MUSS zuletzt erfolgen, nachdem alle Asset-Views verdrahtet sind.
    adView.setNativeAd(ad)
}
