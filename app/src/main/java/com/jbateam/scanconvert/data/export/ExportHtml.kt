package com.jbateam.scanconvert.data.export

import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.domain.ListItem
import com.jbateam.scanconvert.domain.TravelList
import com.jbateam.scanconvert.domain.fmt
import com.jbateam.scanconvert.domain.fmtNum
import com.jbateam.scanconvert.domain.total
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Baut die A4-Export-HTML einer [TravelList] nach der verbindlichen PDF-Vorlage
 * („Listen-Export (PDF)"). Wird per Offscreen-WebView zu echtem A4-PDF gedruckt
 * ([PdfExporter]). CSS/Markup sind 1:1 aus der Spec übernommen; einzige Abweichung:
 * die Fonts werden als gebündelte TTFs aus `assets/export/fonts/` geladen (statt woff2).
 *
 * Wortwahl im PDF ist verbindlich „Ausgaben" (nicht „Einträge"/„Positionen") — bewusst
 * abweichend von der App-UI, weil ein Reise-Beleg „Ausgaben" auflistet.
 */
fun buildListExportHtml(list: TravelList): String {
    val cur = list.currency
    val curSym = htmlEscape(CurrencyMeta.info(cur).sym)
    val curCode = htmlEscape(cur)
    val count = list.items.size
    val total = list.total()
    val date = SimpleDateFormat("d. MMMM yyyy", Locale.GERMANY).format(Date())
    val totalNum = htmlEscape(fmtNum(total, cur))
    val listName = htmlEscape(list.name)

    // Leere Liste (§7): dezenter Hinweis STATT der Ausgaben-Karte (kein Header, keine Karte).
    val posContent = if (list.items.isEmpty()) {
        """      <div class="empty">Noch keine Ausgaben erfasst.</div>"""
    } else {
        val rows = list.items.sortedByDescending { it.ts }
            .mapIndexed { i, item -> itemRow(item, cur, curSym, i + 1) }
            .joinToString("\n")
        """      <div class="pos-head">
        <span class="ph-l">Ausgaben</span>
        <span class="ph-r">Betrag in $curCode</span>
      </div>
      <div class="pos-card">
$rows
      </div>"""
    }
    val budgetHtml = list.budget?.let { budgetBlock(total, it, cur) }.orEmpty()

    return """<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8" />
<style>
$EXPORT_CSS
</style>
</head>
<body>
  <div class="sheet">

    <div class="head">
      <div class="brand">
        $LOGO_SVG
        <div class="bt"><span class="name">ScanConvert</span><span class="tag">Travel Tool</span></div>
      </div>
      <div class="meta-r"><div class="ml">Erstellt am</div><div class="mv">$date</div></div>
    </div>

    <div class="rule"></div>

    <h1 class="title">$listName</h1>
    <div class="subline">$count Ausgaben · Zielwährung <b>$curCode ($curSym)</b></div>

    <div class="total-card">
      <span class="brk tl"></span><span class="brk tr"></span><span class="brk bl"></span><span class="brk br"></span>
      <div class="total-row">
        <div>
          <div class="tc-lbl">Gesamt ausgegeben</div>
          <div class="tc-val">$totalNum<span class="cur">$curSym</span></div>
        </div>
        <div class="tc-count"><div class="n">$count</div><div class="c">Ausgaben</div></div>
      </div>
$budgetHtml
    </div>

    <div class="pos-wrap">
$posContent
    </div>

    <div class="foot">
      <div class="fl">
        $FOOT_SVG
        Erstellt mit <b>ScanConvert</b> · Travel Tool
      </div>
      <div class="fr">Kurse zum Zeitpunkt des Scans fixiert</div>
    </div>

  </div>
</body>
</html>"""
}

/** Eine Ausgaben-Zeile. [index] = 1-basierte Position in der angezeigten (neueste-zuerst) Reihenfolge. */
private fun itemRow(item: ListItem, cur: String, curSym: String, index: Int): String {
    val name = item.label?.takeIf { it.isNotBlank() }
    val nameHtml = if (name != null) htmlEscape(name) else "Ausgabe $index"
    val nameClass = if (name != null) "" else " placeholder"
    val srcSym = htmlEscape(CurrencyMeta.info(item.from).sym)
    val src = "aus " + htmlEscape(fmtNum(item.raw, item.from)) + " " + htmlEscape(item.from)
    val amt = htmlEscape(fmtNum(item.value, cur))
    return """        <div class="item">
          <span class="chip">$srcSym</span>
          <div class="mid">
            <div class="nm$nameClass">$nameHtml</div>
            <div class="src">$src</div>
          </div>
          <div class="amt">$amt<span class="as">$curSym</span></div>
        </div>"""
}

/** Farbcodierte Budget-Leiste (§5 der Spec). Nur wenn ein Budget gesetzt ist. */
private fun budgetBlock(total: Double, budget: Double, cur: String): String {
    val ratio = if (budget > 0) total / budget else 0.0
    val pct = (ratio * 100).roundToInt()
    val width = min(100, pct).coerceAtLeast(0)
    val over = total > budget
    val fillClass = when {
        over -> "over"
        total > budget * 0.8 -> "mid"
        else -> ""
    }
    val rem = budget - total
    val remClass = if (over) "over" else ""
    val remaining = if (over) "+" + htmlEscape(fmt(-rem, cur)) + " über" else htmlEscape(fmt(rem, cur)) + " übrig"
    val budgetFmt = htmlEscape(fmt(budget, cur))
    return """      <div class="budget">
        <div class="bud-head">
          <span class="bh-l">Budget</span>
          <span class="bh-r"><b>$pct&#8201;%</b> ausgeschöpft</span>
        </div>
        <div class="bud-track"><div class="bud-fill $fillClass" style="width:$width%"></div></div>
        <div class="bud-meta">
          <span class="bm-l">Budget <b>$budgetFmt</b></span>
          <span class="bm-r $remClass">$remaining</span>
        </div>
      </div>"""
}

private fun htmlEscape(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/** Verbindliches CSS aus der Spec §9 — unverändert, nur @font-face auf gebündelte TTFs umgestellt. */
private val EXPORT_CSS = """
  @font-face{ font-family:"Plus Jakarta Sans"; font-weight:400; src:url("fonts/plus_jakarta_sans_regular.ttf") format("truetype"); }
  @font-face{ font-family:"Plus Jakarta Sans"; font-weight:500; src:url("fonts/plus_jakarta_sans_medium.ttf") format("truetype"); }
  @font-face{ font-family:"Plus Jakarta Sans"; font-weight:600; src:url("fonts/plus_jakarta_sans_semibold.ttf") format("truetype"); }
  @font-face{ font-family:"Plus Jakarta Sans"; font-weight:700; src:url("fonts/plus_jakarta_sans_bold.ttf") format("truetype"); }
  @font-face{ font-family:"Plus Jakarta Sans"; font-weight:800; src:url("fonts/plus_jakarta_sans_extrabold.ttf") format("truetype"); }
  @font-face{ font-family:"Space Grotesk"; font-weight:500; src:url("fonts/space_grotesk_medium.ttf") format("truetype"); }
  @font-face{ font-family:"Space Grotesk"; font-weight:600; src:url("fonts/space_grotesk_semibold.ttf") format("truetype"); }
  @font-face{ font-family:"Space Grotesk"; font-weight:700; src:url("fonts/space_grotesk_bold.ttf") format("truetype"); }
  :root{
    --ink:#1e261d; --ink-2:#5e6b5c; --ink-3:#93a08f;
    --surface:#ffffff; --surface-warm:#f1f6ef; --line:#e1eade;
    --accent:#1f9d6b; --accent-deep:#14774f;
    --accent-soft:rgba(31,157,107,0.13); --danger:#c0533a;
    --shadow-card:0 18px 50px -22px rgba(30,40,28,0.30);
    --sans:"Plus Jakarta Sans", system-ui, sans-serif;
    --num:"Space Grotesk", "Plus Jakarta Sans", sans-serif;
  }
  *{ box-sizing:border-box; }
  html,body{ margin:0; background:#fff; }
  body{ font-family:var(--sans); color:var(--ink); -webkit-font-smoothing:antialiased; }
  .sheet{ width:210mm; min-height:297mm; background:#fff; display:flex; flex-direction:column;
    padding:16mm 15mm 12mm; position:relative; }

  .head{ display:flex; align-items:flex-start; justify-content:space-between; gap:20px; }
  .brand{ display:flex; align-items:center; gap:14px; }
  .brand .mark{ width:54px; height:54px; flex:none; border-radius:15px; }
  .brand .bt{ display:flex; flex-direction:column; line-height:1.05; }
  .brand .bt .name{ font-size:21px; font-weight:800; letter-spacing:-0.02em; }
  .brand .bt .tag{ font-size:11px; font-weight:700; letter-spacing:0.16em; text-transform:uppercase; color:var(--accent-deep); margin-top:5px; }
  .head .meta-r{ text-align:right; padding-top:3px; flex:none; }
  .head .meta-r .ml{ font-size:10.5px; font-weight:700; letter-spacing:0.14em; text-transform:uppercase; color:var(--ink-3); }
  .head .meta-r .mv{ font-size:14px; font-weight:700; color:var(--ink); margin-top:4px; white-space:nowrap; }

  .rule{ height:1px; background:var(--line); margin:18px 0 24px; }
  .title{ font-size:34px; font-weight:800; letter-spacing:-0.025em; margin:0; line-height:1.05; }
  .subline{ font-size:14px; color:var(--ink-2); margin-top:7px; font-weight:500; }
  .subline b{ color:var(--ink); font-weight:700; }

  .total-card{ margin-top:20px; background:var(--surface); border:1px solid var(--line);
    border-radius:22px; box-shadow:var(--shadow-card); padding:22px 26px; position:relative; }
  .brk{ position:absolute; width:18px; height:18px; border:2.5px solid var(--accent-soft); }
  .brk.tl{ top:13px; left:13px; border-right:none; border-bottom:none; border-radius:6px 0 0 0; }
  .brk.tr{ top:13px; right:13px; border-left:none; border-bottom:none; border-radius:0 6px 0 0; }
  .brk.bl{ bottom:13px; left:13px; border-right:none; border-top:none; border-radius:0 0 0 6px; }
  .brk.br{ bottom:13px; right:13px; border-left:none; border-top:none; border-radius:0 0 6px 0; }
  .total-row{ display:flex; align-items:flex-end; justify-content:space-between; gap:24px; }
  .tc-lbl{ font-size:11px; font-weight:700; letter-spacing:0.14em; text-transform:uppercase; color:var(--ink-3); }
  .tc-val{ font-family:var(--num); font-weight:700; font-size:46px; letter-spacing:-0.03em; line-height:0.95; margin-top:8px; }
  .tc-val .cur{ font-size:24px; color:var(--ink-2); margin-left:7px; letter-spacing:0; }
  .tc-count{ text-align:right; }
  .tc-count .n{ font-family:var(--num); font-weight:700; font-size:26px; color:var(--ink); line-height:1; }
  .tc-count .c{ font-size:12px; color:var(--ink-2); margin-top:5px; font-weight:600; }

  .budget{ margin-top:18px; padding-top:16px; border-top:1px solid var(--line); }
  .bud-head{ display:flex; align-items:baseline; justify-content:space-between; margin-bottom:9px; }
  .bud-head .bh-l{ font-size:11px; font-weight:700; letter-spacing:0.12em; text-transform:uppercase; color:var(--ink-3); }
  .bud-head .bh-r{ font-family:var(--num); font-size:13px; font-weight:600; color:var(--ink-2); white-space:nowrap; }
  .bud-head .bh-r b{ color:var(--accent-deep); }
  .bud-track{ height:8px; border-radius:5px; background:var(--surface-warm); border:1px solid var(--line); overflow:hidden; }
  .bud-fill{ height:100%; border-radius:5px; min-width:6px; background:linear-gradient(90deg, var(--accent), #38b07f); }
  .bud-fill.mid{ background:linear-gradient(90deg, #d9a23f, #e0b94f); }
  .bud-fill.over{ background:linear-gradient(90deg, #d9663f, #e07a4f); }
  .bud-meta{ display:flex; align-items:baseline; justify-content:space-between; margin-top:9px; }
  .bud-meta .bm-l{ font-size:13px; color:var(--ink-2); white-space:nowrap; }
  .bud-meta .bm-l b{ color:var(--ink); font-family:var(--num); font-weight:700; }
  .bud-meta .bm-r{ font-family:var(--num); font-size:16px; font-weight:700; color:var(--accent-deep); white-space:nowrap; }
  .bud-meta .bm-r.over{ color:var(--danger); }

  .pos-wrap{ margin-top:22px; flex:1; }
  .pos-head{ display:flex; align-items:baseline; justify-content:space-between; margin:0 4px 11px; }
  .pos-head .ph-l, .pos-head .ph-r{ font-size:11px; font-weight:700; letter-spacing:0.14em; text-transform:uppercase; color:var(--ink-3); }
  .pos-card{ background:var(--surface); border:1px solid var(--line); border-radius:18px; box-shadow:var(--shadow-card); overflow:hidden; }
  .item{ display:flex; align-items:center; gap:13px; padding:12px 20px; break-inside:avoid; }
  .item + .item{ border-top:1px solid var(--line); }
  .item .chip{ width:36px; height:36px; flex:none; border-radius:50%; background:var(--surface-warm); border:1px solid var(--line);
    display:grid; place-items:center; font-family:var(--num); font-weight:700; font-size:15px; color:var(--accent-deep); }
  .item .mid{ flex:1; min-width:0; }
  .item .nm{ font-size:15.5px; font-weight:700; letter-spacing:-0.01em; color:var(--ink); }
  .item .nm.placeholder{ color:var(--ink-3); font-weight:600; }
  .item .src{ font-size:12.5px; color:var(--ink-2); margin-top:2px; font-weight:500; }
  .item .amt{ font-family:var(--num); font-weight:700; font-size:20px; letter-spacing:-0.02em; color:var(--ink); white-space:nowrap; }
  .item .amt .as{ font-size:13.5px; color:var(--ink-3); margin-left:3px; font-weight:600; }

  .foot{ margin-top:24px; padding-top:16px; border-top:1px solid var(--line); display:flex; align-items:center; justify-content:space-between; gap:16px; }
  .foot .fl{ display:flex; align-items:center; gap:10px; font-size:12px; color:var(--ink-2); font-weight:500; }
  .foot .fl .fmark{ width:22px; height:22px; border-radius:6px; flex:none; }
  .foot .fl b{ color:var(--ink); font-weight:700; }
  .foot .fr{ font-size:11.5px; color:var(--ink-3); font-family:var(--num); }

  .empty{ text-align:center; color:var(--ink-2); font-size:13px; padding:26px 16px; font-weight:500; }

  @page{ size:A4; margin:0; }
""".trimIndent()

private val LOGO_SVG = """
<svg class="mark" viewBox="0 0 1024 1024" aria-label="ScanConvert">
          <defs><radialGradient id="bg" cx="42%" cy="38%" r="70%">
            <stop offset="0%" stop-color="#1E1B14"/><stop offset="100%" stop-color="#0D0B08"/></radialGradient></defs>
          <rect width="1024" height="1024" rx="230" fill="url(#bg)"/>
          <rect x="210" y="486" width="604" height="52" rx="26" fill="#1F9D6B" opacity="0.22"/>
          <path d="M210 414 L210 210 L414 210" fill="none" stroke="white" stroke-width="56" stroke-linecap="round" stroke-linejoin="round" opacity="0.93"/>
          <path d="M610 210 L814 210 L814 414" fill="none" stroke="white" stroke-width="56" stroke-linecap="round" stroke-linejoin="round" opacity="0.93"/>
          <path d="M210 610 L210 814 L414 814" fill="none" stroke="white" stroke-width="56" stroke-linecap="round" stroke-linejoin="round" opacity="0.93"/>
          <path d="M610 814 L814 814 L814 610" fill="none" stroke="white" stroke-width="56" stroke-linecap="round" stroke-linejoin="round" opacity="0.93"/>
          <text x="512" y="512" font-family="Space Grotesk, sans-serif" font-weight="800" font-size="314" fill="white" text-anchor="middle" dominant-baseline="middle" opacity="0.95">€</text>
          <rect x="210" y="498" width="604" height="28" rx="14" fill="#1F9D6B" opacity="0.84"/>
        </svg>
""".trimIndent()

private val FOOT_SVG = """
<svg class="fmark" viewBox="0 0 1024 1024">
          <rect width="1024" height="1024" rx="230" fill="#13110c"/>
          <path d="M250 430 L250 250 L430 250 M594 250 L774 250 L774 430 M250 594 L250 774 L430 774 M594 774 L774 774 L774 594" fill="none" stroke="white" stroke-width="64" stroke-linecap="round" stroke-linejoin="round" opacity="0.92"/>
          <text x="512" y="540" font-family="Space Grotesk, sans-serif" font-weight="800" font-size="330" fill="white" text-anchor="middle" dominant-baseline="middle">€</text>
        </svg>
""".trimIndent()
