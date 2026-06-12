# Handoff: Kamera‑Währungsscanner mit Reise‑Budget‑Listen (Android)

> **Auftrag in einem Satz:** Baue die in `reference/` liegenden HTML‑Prototypen **1:1** (inkl. aller Animationen) als native **Android‑App** nach. Erst Android, andere Plattformen später.

---

## 1. Überblick
Eine App, die über die **offene Kamera** (kein Foto) Preise live in eine andere Währung umrechnet. Statt alle Zahlen im Bild zu überlagern, gibt es **einen dedizierten Scan‑Bereich** in der Bildmitte – nur die dort eingerahmte Zahl wird umgerechnet.

Umgerechnete Preise lassen sich per **„+"** in **Listen** legen. Jede Liste ist an **genau eine Zielwährung** gebunden und funktioniert wie eine laufende Reise‑Rechnung mit optionalem **Budget** (z. B. „Hongkong Reise" in EUR, in die HKD‑Preise konvertiert gesammelt werden, um das Euro‑Budget im Blick zu behalten).

## 2. Über die Design‑Dateien — bitte zuerst lesen
Die Dateien unter `reference/` sind **Design‑Referenzen, die in HTML/CSS/React gebaut wurden** – sie zeigen Aussehen und Verhalten, sind **kein** produktiver Code zum Kopieren. Aufgabe ist, diese Designs in einer **nativen Android‑Umgebung** nachzubauen (Empfehlung **Jetpack Compose**, Kotlin) mit den dort üblichen Patterns. Maße, Farben, Typo und Timings unten sind verbindlich.

So kannst du die Referenz ansehen: `reference/Camera Scanner V2.html` im Browser öffnen. Der Kamera‑Hintergrund ist eine **gefälschte Szene** (CSS) – in der echten App kommt dort der CameraX‑Preview hin.

- `reference/Camera Scanner V2.html` – Einstieg, lädt die drei JS/CSS‑Dateien
- `reference/app.css` – sämtliche Styles + Design‑Tokens
- `reference/components.jsx` – Währungsdaten, Helfer, Flagge, Menü, Picker‑Sheet
- `reference/v2.jsx` – App‑Logik: Onboarding, Live‑Scan, Listen, Persistenz

## 3. Fidelity
**High‑Fidelity.** Pixelgenau nachbauen: finale Farben, Typografie, Abstände, Radien, Schatten und alle Transitions wie unter §11 spezifiziert.

## 4. Plattform & Tech‑Empfehlung (Android‑first)
- **UI:** Jetpack Compose (Material 3 als Basis, aber Styling überschreiben – das Design ist eigenständig, **nicht** Stock‑Material).
- **Kamera:** CameraX `Preview` + `ImageAnalysis`.
- **Texterkennung:** ML Kit **Text Recognition v2** (on‑device) – nur auf den **Scan‑ROI** angewandt (siehe §12).
- **Kurse:** FX‑API mit Caching (siehe §8). Die Kurse im Prototyp sind Platzhalter.
- **Persistenz:** Room (Listen + Positionen) und DataStore (Pins, Onboarding‑Flag) – siehe §7.
- **Min‑SDK:** Vorschlag 26+. Hochformat (Portrait‑lock) für den Scan‑Screen.
- **Schriften:** Plus Jakarta Sans + Space Grotesk als gebündelte Font‑Ressourcen (Google Fonts, OFL).

## 5. Implementierungs‑Plan (Checkliste – „alles abarbeiten")
Bitte in dieser Reihenfolge umsetzen und abhaken:

1. **Projekt‑Setup**: Compose, Fonts (`PlusJakartaSans`, `SpaceGrotesk`), Farb‑/Typo‑Tokens aus §10 als Theme.
2. **Domain & Daten**: Währungs‑Tabelle (§6), `convert()`/`rate()` (§9), Intl‑Formatierung de‑DE (§9).
3. **Persistenz**: Room‑Entities `TravelList`, `ListItem`; DataStore für `pins` + `onboarded` (§7). Demo‑Seed (§6.1).
4. **Onboarding‑Screen** (§ Screen 1) inkl. Auswahl max. 4, Persistenz, Übergang in die App.
5. **Kamera‑Screen Gerüst** (§ Screen 2): CameraX‑Preview, Status‑Bar‑Overlay, Glas‑Menü oben, Scan‑Rahmen‑Overlay, Edge‑Tab rechts.
6. **Live‑Umrechnung**: ML‑Kit auf ROI, Zahl‑in‑Rahmen‑Auswahl, `scanning → locked` State (§12), Ergebnis‑Karte unten.
7. **Währungs‑Picker** (§ Screen 3): Bottom‑Sheet mit Suche, Pins (fix oben) + Trenner + alphabetisch, An‑/Abpinnen (max 4), Swap.
8. **„Zu Liste hinzufügen"** (§ Screen 4): nur Listen mit `currency == to`, plus „Neue Liste".
9. **Neue Liste** (§ Screen 5): Name, Währung (im Add‑Flow fix = Zielwährung), optional Budget.
10. **Listen‑Panel** (§ Screen 6 + 7): Vollbild, Übersicht‑Karten mit Budget‑Balken → Detail mit Summe, Budget, Positionen, Löschen.
11. **Toast** (§ Screen 8) für „Hinzugefügt".
12. **Alle Animationen** exakt nach §11.
13. **Kurse live** anbinden (§8), „Live"‑Indikator + Zeitstempel.
14. **Liste verwalten**: Umbenennen, Budget bearbeiten, Löschen mit Bestätigung (§ Screen 7).
15. **Alle verfügbaren Währungen** der Live‑API nutzen, falls eine kostenlose gefunden wird (§8) — die 16 Demo‑Währungen sind nur Platzhalter.
16. **Lokale Flaggen** bündeln (§13) statt Remote‑URLs.
17. **QA** gegen die Referenz (Maße/Timings).

---

## 6. Währungen & Kurse (Demo)
16 Währungen. `cc` = ISO‑Ländercode für die Flagge, `sym` = Symbol, `rate` = Kurs **relativ zu EUR** (Platzhalter, im Betrieb durch Live‑Kurse ersetzen).

| Code | Name (Anzeige) | sym | cc | rate (zu EUR) |
|---|---|---|---|---|
| EUR | Euro | € | eu | 1.0000 |
| USD | US‑Dollar | $ | us | 1.0850 |
| GBP | Brit. Pfund | £ | gb | 0.8520 |
| CHF | Schw. Franken | Fr | ch | 0.9450 |
| JPY | Japan. Yen | ¥ | jp | 172.00 |
| AUD | Austral. Dollar | $ | au | 1.6300 |
| CAD | Kanad. Dollar | $ | ca | 1.4750 |
| CNY | Renminbi | ¥ | cn | 7.7400 |
| INR | Ind. Rupie | ₹ | in | 90.500 |
| BRL | Brasil. Real | R$ | br | 5.9200 |
| SEK | Schwed. Krone | kr | se | 11.420 |
| NOK | Norweg. Krone | kr | no | 11.680 |
| MXN | Mexik. Peso | $ | mx | 19.850 |
| ZAR | Südafr. Rand | R | za | 19.420 |
| SGD | Singapur‑Dollar | $ | sg | 1.4350 |
| HKD | Hongkong‑Dollar | HK$ | hk | 8.4500 |

**Standard‑Pins** (Fallback, falls Onboarding übersprungen): `EUR, USD, GBP, CHF`. Standard‑Paar beim Start: **EUR → USD**.

### 6.1 Demo‑Seed (beim ersten Start, wenn keine Listen existieren)
- **„USA Roadtrip"**, currency `USD`, budget `1500`, Positionen (raw, from): (12.9, EUR), (8.5, EUR), (34, EUR), (22.4, EUR)
- **„Hongkong Reise"**, currency `EUR`, budget `800`, Positionen: (188, HKD), (65, HKD), (240, HKD), (42, HKD)

`value` jeder Position = `convert(raw, from, listCurrency)`.

## 7. Datenmodell & State
```
TravelList { id: String, name: String, currency: String, budget: Double?, items: List<ListItem> }
ListItem   { id: String, raw: Double, from: String, value: Double, ts: Long }
```
- `value` ist der bereits umgerechnete Betrag in `list.currency` (zum Zeitpunkt des Scannens fixiert – wird **nicht** nachträglich neu umgerechnet).
- `sumList(list) = Σ items.value`.

**Persistente Keys (Prototyp):** `fxlens_pins` (Array max 4), `fxlens_lists` (Array), `fxlens_onboarded` (bool). In Android: Pins/Onboarded → DataStore, Listen → Room.

**Session‑State (nicht persistent):** `from`, `to`, Scan‑`phase` (`scanning|locked`), geöffnete Sheets/Panel, ausgewählte Liste, Toast.

**Regeln:**
- Pins: togglebar, **max 4**, feste Reihenfolge = Pin‑Reihenfolge.
- Beim Tausch/Pick: wenn neue Währung = die andere Seite, **beide tauschen** (kein gleiches Paar).
- Jede Liste ist an **eine** Zielwährung gebunden; Add zeigt nur Listen mit `currency == to`.

## 8. Live‑Kurse
Im Prototyp statisch (§6). Produktiv: FX‑Quelle (z. B. ECB/`exchangerate.host` o. ä.) periodisch laden, in EUR‑Basis normalisieren, cachen (Offline‑Fallback = letzter Stand). Im Menü `„Live"` + Kurszeile `1 {from} = {rate} {to}` mit 4 Nachkommastellen anzeigen; optional Zeitstempel des letzten Updates.

> **Wichtig:** Findest du eine **frei nutzbare** Live‑Kurs‑API, dann verwende **alle dort verfügbaren Währungen** (nicht nur die 16 Demo‑Währungen). Onboarding‑Grid, Picker und Listen führen dann die komplette Währungsliste der API (Flagge je `cc`).

## 9. Umrechnung & Formatierung
- `convert(amt, from, to) = amt / rate[from] * rate[to]`
- `rate(from, to) = rate[to] / rate[from]` → Anzeige **4 Nachkommastellen**, Locale **de‑DE** (Komma).
- Beträge: Locale **de‑DE**, Währungssymbol; **JPY ohne Nachkommastellen**, sonst 2. (Android: `NumberFormat.getCurrencyInstance(Locale.GERMANY)` mit `currency` setzen, bzw. ICU.)
- Beispiel: `24,90 €` → `27,02 $` bei EUR→USD.

---

## 10. Design‑Tokens

### Farben
| Token | Hex / Wert | Einsatz |
|---|---|---|
| canvas | `#E5ECE4` | App‑/Seitenhintergrund (leichter Grünton) |
| canvas‑2 | `#D7E1D5` | dunklere Variante |
| ink | `#1E261D` | Primärtext |
| ink‑2 | `#5E6B5C` | Sekundärtext |
| ink‑3 | `#93A08F` | Tertiär/Hints, Caret |
| surface | `#FFFFFF` | Karten/Chips |
| surface‑warm | `#F1F6EF` | Felder, Kurszeile, leichte Flächen |
| line | `#E1EADE` | Rahmen/Trenner |
| glass | `rgba(246,251,245,0.85)` | Glas‑Menü (Blur ~22) |
| glass‑strong | `rgba(245,250,244,0.93)` | Ergebnis‑Karte, Edge‑Tab |
| accent | `#1F9D6B` | Primär (Scan‑lock, Buttons, Ergebnis) |
| accent‑deep | `#14774F` | Hover/aktiv, „Live", Labels |
| accent‑ink | `#0E5E3D` | dunkelste Akzentschrift |
| accent‑soft | `rgba(31,157,107,0.14)` | Akzent‑Flächen/Tints |
| accent‑glow | `rgba(31,157,107,0.55)` | Glow/Schatten am Akzent |
| danger | `#C0533A` (Text) / Fläche `#FBEAE6` | Löschen, Budget‑Überschreitung |
| budget‑over | Verlauf `#D9663F → #E07A4F` | Budget‑Balken über 100 % |

Kamera‑Szene (nur Mock, in echt = Kamerabild): Hintergrund `linear 155° #8A6E4C → #5D4B35 → #312618`; Preis‑Tags `#FCF8EF → #ECE1CB`; Ziel‑Tag `#FEFCF6 → #F2E9D6`.

### Typografie
- **UI‑Font:** Plus Jakarta Sans (400/500/600/700/800).
- **Zahlen‑Font:** Space Grotesk (500/600/700) – für **alle Beträge/Kurse**, `tabular`/`letter-spacing: -0.02em`.
- Skala (Design‑Breite Phone = 380 px ≈ dp): Statusbar 14/600 · Menü‑Label 10/700 (uppercase, +0.12em) · Währungscode im Chip 17/800, Symbol 13/600 (ink‑3) · Kurszeile 12.5/500 · Scan „Erkannt"‑Badge 11/700 · Hint 12/600 · Ergebnis „Umgerechnet"‑Label 11/700 (uppercase) · Ergebnis‑Zahl Zielwährung 32/700, Quellwährung 24/700 · Add‑Button 14.5/700 · Listen‑Karte Name 17/800, Summe 22/700 · Detail‑Summe 38/700 · Onboarding‑Titel 25/800 · Section‑Labels 10–11/700 uppercase.

### Radius
`sm 12 · md 16 · lg 24` · Phone‑Screen 42 · Chips 16 · Swap/Avatare rund · Sheets `28 28 42 42` (oben gerundet) · Ergebnis‑Karte 26 · Listen‑Karten 20 · Pills/Toast 999.

### Schatten
- card: `0 18px 50px -18px rgba(30,40,28,0.26)`
- float (Sheets/Menü/Toast): `0 14px 36px -10px rgba(16,22,14,0.42)`
- Add‑Button/Akzent: `0 8px 18px -6px accent-glow`

### Spacing
Basis 4 px. Menü‑Padding 14, Chip‑Padding `9/10`, Sheet‑Padding `10/16/26`, Panel‑Body `16`. Scan‑Box **230 × 116** (zentriert). Glas‑Menü: `top 60, left/right 14`. Edge‑Tab: rechts, `top ~300`, `42 × 64`.

---

## 11. Animationen & Transitions (exakt nachbauen)
| Element | Eigenschaft | Dauer | Easing / Detail |
|---|---|---|---|
| Scan: scanning→locked | nach Stabilisierung | **1000 ms** Verzögerung | dann lock |
| Scan‑Linie (scanning) | Y 8px→(H‑10px)→8px | **1.5 s** loop | ease‑in‑out; bei `locked` opacity→0 |
| Rahmen bei lock | Border weiß→accent, Glow ein | **0.3 s** | ease |
| „Erkannt"‑Badge | scale 0→1 | **0.3 s** | cubic‑bezier(.3,1.5,.5,1) (Overshoot) |
| Ergebnis‑Karte | translateY 140%→0 | **0.42 s** | cubic‑bezier(.2,.9,.3,1) |
| Swap‑Button | rotate 0→180° | **0.35 s** | cubic‑bezier(.5,1.4,.5,1) (federnd) |
| Bottom‑Sheets | translateY 100%→0 | **0.28 s** | cubic‑bezier(.2,.9,.3,1); Scrim fade 0.2 s |
| Listen‑Panel | translateY 100%→0 | **0.4 s** | cubic‑bezier(.2,.9,.3,1) |
| Toast | opacity + translateY 20→0 | **0.25 s** ein | cubic‑bezier(.3,1.3,.5,1); Auto‑Hide nach **2200 ms** |
| Live‑Punkt (Puls) | Ring scale .7→1.35, opacity .55→0 | **1.8 s** loop | ease‑out |
| Budget‑Balken | width | **0.5 s** | cubic‑bezier(.3,1,.4,1) |
| Onboarding‑Tile | Border/Background | 0.15 s | ease |
| Chips/Buttons (press) | scale 0.98–0.99 | ~0.12 s | tap‑Feedback |

`prefers-reduced-motion`/System‑Animationsskala respektieren: Endzustände direkt zeigen.

---

## 12. Kamera & Texterkennung (Scan‑ROI)
- CameraX **Preview** füllt den Screen (kein Abdunkeln außerhalb des Rahmens – bewusst so).
- **ROI** = zentriertes Rechteck **230 × 116 dp** (gleich dem sichtbaren Scan‑Rahmen). Nur Erkennungen, deren Bounding‑Box‑Mittelpunkt **innerhalb des ROI** liegt, zählen.
- **Auswahl:** unter den Treffern im ROI die Zahl wählen, deren Mittelpunkt dem ROI‑Zentrum am nächsten ist. Robust gegen mehrere Zahlen.
- Zahl‑Parsing: Dezimal mit `.`/`,` tolerant; Währungssymbole/Tausenderpunkte entfernen; das Ergebnis ist `raw` in der **Von‑Währung** (`from` wird vom Nutzer gewählt, nicht erkannt).
- **State‑Maschine:** solange keine stabile Zahl → `scanning` (Hint „Zahl in den Rahmen halten", Scan‑Linie läuft). Wird über ~1 s eine konsistente Zahl gehalten → `locked`: Rahmen accent, „Erkannt", Ergebnis‑Karte slidet ein. Tippen auf den Rahmen = **erneut scannen** (zurück zu `scanning`).
- Ändert sich `from`/`to` oder Swap → `rescan` (zurück zu `scanning`).
- Live umrechnen mit aktuellen Kursen; Ergebnis‑Karte zeigt `from raw → to value` + Kurs.

---

## 13. Screens im Detail

### Screen 1 — Onboarding (nur 1. Start, persistent)
- Vollbild, Hintergrund `surface` mit grünem Radial oben. Padding `64/22/22`.
- Oben: Akzent‑Badge (52, radius 16, Globe‑Icon weiß). Titel **„Wähle deine Favoriten"** (25/800). Subtext (14, ink‑2): „Bis zu 4 Währungen für den Schnellzugriff beim Umrechnen. Du kannst das später jederzeit ändern."
- Zähler: 4 Punkte (gefüllt = Auswahl) + „{n}/4 ausgewählt" (accent‑deep).
- **Grid** 2 Spalten, scrollbar: alle 16 Währungen als Kachel (Flagge 32, Code 15/800, Name 11 ink‑2, Häkchen rechts wenn gewählt). Auswahl **max 4** – sind 4 erreicht, sind die übrigen Kacheln auf `opacity .4` und nicht klickbar.
- Button unten (`btn-primary`, accent, radius 15): bei 0 Auswahl deaktiviert mit Text „Mindestens 1 wählen", sonst **„Los geht's"**. Klick → Pins speichern, `onboarded=true`, in Kamera‑Screen.

### Screen 2 — Kamera / Live‑Scan (Hauptscreen)
**Layout (Z‑Reihenfolge von unten):**
1. **Kamera** (CameraX) – Vollbild.
2. **Status‑Bar‑Overlay** oben (Zeit links „15:15", rechts Signal/WLAN/Akku), weiß mit leichtem Top‑Scrim für Lesbarkeit. *(In echt: System‑Statusbar nutzen; das Overlay nur, falls Edge‑to‑Edge.)*
3. **Glas‑Menü** (`glass`, Blur ~22, radius 24, Schatten float) bei `top 60, left/right 14`, Padding 14:
   - Grid **`minmax(0,1fr) auto minmax(0,1fr)`**, gap 8, `align-items: stretch` (wichtig, damit die Chips **nicht** über den Panel‑Rand laufen und der Swap exakt mittig sitzt).
   - Spalte 1 & 3: Label oben (`VON`/`ZU`, 10/700 uppercase, ink‑3) + **Chip** (`surface`, border `line`, radius 16): Flagge (rund, 30) + Code (17/800) + Symbol (13/600 ink‑3) + Caret (9, ink‑3). Tap → Picker (Screen 3). Press‑scale .98. Offen‑State: Border accent + Ring `accent-soft`.
   - Spalte 2 (mittig): leeres Label‑Spacer (gleiche Höhe wie `VON/ZU`, damit der Button vertikal auf Chip‑Mitte sitzt) + runder **Swap‑Button** (42, `surface`, border `line`, Icon accent‑deep). Tap → `from/to` tauschen + Rotation 180° + `rescan`.
   - Darunter **Kurszeile** (`surface-warm`, radius 12): pulsender Live‑Punkt + `1 {from} = {rate} {to}` (Code & Kurs in Zahlen‑Font, fett) + rechts `LIVE` (10/700 accent‑deep).
4. **Scan‑Rahmen** zentriert, 230 × 116:
   - Abgerundetes Fenster (border 2.5 weiß→accent bei lock, radius 18) + 4 Eck‑Winkel (weiß→accent) + **Scan‑Linie** (weißer Verlauf mit Glow) die im `scanning` vertikal wandert.
   - Bei `locked`: Fenster accent + Glow, Linie aus, **„Erkannt"‑Pill** (accent, weiß, Häkchen) oben mittig mit Overshoot‑Scale.
   - `Hint`‑Pill unten („Zahl in den Rahmen halten", dunkel/blur) nur im `scanning`.
   - Außerhalb wird **nicht** abgedunkelt.
5. **Edge‑Tab** rechter Rand, vertikal ~mittig: Glas‑Handle (42 × 64, links abgerundet 16, kleiner „Grip"‑Strich) mit Listen‑Icon (accent‑deep). Tap → Listen‑Panel (Screen 6). *(Kein Zähler‑Badge.)*
6. **Ergebnis‑Karte** unten (`glass-strong`, radius 26, Schatten float, `left/right 12, bottom 22`), slidet bei `locked` ein:
   - Kopf: Live‑Punkt + `UMGERECHNET` (11/700 uppercase accent‑deep) + rechts `1 {from} = {rate} {to}`.
   - Body Grid `1fr auto 1fr`: links `from`‑Code (11/700 ink‑3) + Betrag (24/700 ink‑2); Mitte runder Pfeil (accent‑soft); rechts `to`‑Code + Betrag (32/700 ink). Beträge im Zahlen‑Font.
   - **Add‑Button** (`rc-add`, voll breit, accent, radius 15): Plus‑Icon + **„Zu Liste hinzufügen"**. Tap (nur wenn `locked`) → Screen 4.

### Screen 3 — Währungs‑Picker (Bottom‑Sheet)
- Scrim (dunkel, blur) + Sheet (`surface`, oben radius 28) von unten.
- Grab‑Handle, Titel (`Eingescannte Währung` für „Von" bzw. `Zielwährung` für „Zu"; für Listen‑Währung `Währung der Liste`).
- **Suchfeld** (`surface-warm`, radius 14, Lupe): filtert über **Code und Name** (case‑insensitiv).
- **Liste** (scrollbar, max‑Höhe ~372):
  - **Ohne Suche:** Abschnitt „Angepinnt" → Pins in **fester Reihenfolge**; dann **Trenner** „Alle Währungen"; dann übrige Währungen **alphabetisch** nach Code.
  - **Mit Suche:** flache, alphabetische Trefferliste (keine Gruppierung); 0 Treffer → „Keine Treffer für ‚…'".
  - **Zeile:** Flagge 32 + Code 15/800 (+ Häkchen wenn aktiv) + Name 12 ink‑2 + Symbol (Zahlen‑Font, ink‑3) rechts + **Pin‑Button** ganz rechts (Pin gefüllt wenn gepinnt; deaktiviert/`opacity .25` wenn 4 erreicht und nicht gepinnt).
  - Aktive Währung: Zeile mit `accent-soft` hinterlegt.
- Zeilen‑Tap (links) wählt die Währung (Sheet schließt, `rescan`). Pin‑Tap (rechts) togglet nur den Pin (kein Schließen).

### Screen 4 — „Zu Liste hinzufügen" (Bottom‑Sheet)
- Titel „Zu Liste hinzufügen". **Amount‑Chip** (`surface-warm`): großer umgerechneter Betrag `{conv} {to}` (26/700) + „aus {raw} {from} gescannt" + Flagge der Zielwährung rechts.
- Liste der **Listen mit `currency == to`** (Zeile: Flagge, Name, „{n} Positionen", Summe rechts). Tap → Position hinzufügen, Sheet schließen, **Toast** „Zu ‚{name}' hinzugefügt".
- Gibt es keine passende Liste: Hinweis „Noch keine Liste in **{to}** …".
- Immer unten: gestrichelte Zeile **„Neue {to}‑Liste"** → Screen 5 (Kontext `add`, Währung fix = `to`).

### Screen 5 — Neue Liste (Bottom‑Sheet)
- **In‑Place erstellen:** Aus dem Listen‑Panel heraus öffnet sich dieses Sheet **über** dem Panel (z‑index 65 > Panel 60); das Panel bleibt dahinter offen — man muss es **nicht** verlassen.
- Felder: **Name** (Pflicht, Placeholder „z. B. Hongkong Reise"); **Währung der Liste** – im `add`‑Kontext **fix** (Chip + „fest"), im `panel`‑Kontext wählbar über horizontalen Flaggen‑Scroller (alle Währungen, ausgewählte hervorgehoben); **Budget (optional)** Zahlenfeld mit Währungssymbol als Suffix.
- Primär‑Button: `add` → „Erstellen & hinzufügen" (legt Liste an **und** fügt aktuelle Position hinzu, Toast); `panel` → „Liste erstellen" (öffnet das Detail der neuen Liste). Deaktiviert bei leerem Namen.

### Screen 6 — Listen‑Übersicht (Vollbild‑Panel)
- Slidet von unten (`panel`, grüner Canvas‑Hintergrund). Kopf: Titel „Meine Listen" + Sub „Reise‑Rechnungen je Zielwährung" + **Schließen‑Icon** (X) rechts.
- Body scrollbar: pro Liste eine **Karte** (`surface`, radius 20, card‑shadow): Flagge der Währung (44) + Name (17/800) + Meta „{n} Positionen · {currency}" + **Budget‑Balken** (kompakt, wenn Budget gesetzt) + Summe rechts (22/700) + Chevron. Tap → Detail (Screen 7).
- Keine Listen → Empty‑State (Icon, „Noch keine Listen", Subtext).
- Unten gestrichelt **„Neue Liste"** → Screen 5 (Kontext `panel`).

### Screen 7 — Listen‑Detail (Vollbild‑Panel)
- Kopf: **Zurück‑Icon** links + Listenname + Sub „{n} Positionen · {currency}" + Schließen‑Icon rechts.
- **Summen‑Karte** (`surface`, radius 22): Label „Gesamt" + große Summe (38/700) mit Symbol; bei Budget darunter **Budget‑Balken** mit Meta `Budget {budget}` und rechts `{rem} übrig` (bzw. `+{x} über` in Danger‑Rot bei Überschreitung; Balken‑Verlauf wechselt auf budget‑over).
- Abschnitt „Positionen": je Eintrag (neueste zuerst) eine Zeile (`surface`, radius 15): Flagge der **gescannten** Währung (`from`) + umgerechneter Betrag `{value} {currency}` (18/700) + „aus {raw} {from}" + **Löschen‑Icon** (Papierkorb; Hover/aktiv Danger). Löschen aktualisiert Summe/Budget sofort und persistiert.
- Leere Liste → Hinweis „Noch nichts hinzugefügt …".

- **Verwalten:** Stift‑Icon im Detail‑Kopf → Bottom‑Sheet „Liste bearbeiten" (über dem Panel, z‑index 65): Name + Budget editieren (Währung bleibt fix), **Speichern**; darunter Danger‑Aktion „Liste löschen" mit Inline‑Bestätigung („Wirklich löschen?" · Abbrechen / Löschen) → danach zurück zur Übersicht.

### Screen 8 — Toast
Dunkle Pill mittig unten (`ink`, weiß, radius 999), kleiner accent‑Kreis mit Häkchen + Text. Ein‑/Ausblenden nach §11, Auto‑Hide 2200 ms.

---

## 14. Assets
- **Flaggen:** im Prototyp von `https://flagcdn.com/w80/{cc}.png` (rund geclippt). **Produktiv: lokales Flaggen‑Set bündeln** (z. B. SVG/zusammengesetzte Vektor‑Assets je `cc`), keine Remote‑Requests. Rund maskieren, 1px‑Innenrand `rgba(0,0,0,0.08)` + leichter Schatten.
- **Icons:** einfache Stroke‑Icons (Plus, Liste, X, Zurück, Chevron, Papierkorb, Globe, Swap, Lupe, Pin, Häkchen, Pfeil, Reset) – Material‑Symbols oder eigene 24er‑Strokes (≈1.8–2.4 px), Farben wie im Kontext.
- **Fonts:** Plus Jakarta Sans, Space Grotesk (Google Fonts, OFL) als Ressourcen bündeln.
- **Kamera‑Szene** der Referenz ist nur Mock und entfällt (echtes Kamerabild).

## 15. Hinweise / später zu klären
- ✓ **Umbenennen, Budget bearbeiten, Liste löschen** sind umgesetzt (Stift‑Icon im Listen‑Detail, mit Lösch‑Bestätigung).
- Optional: zuletzt genutzte Währung automatisch nach oben; gepinnte Reihenfolge per Drag ändern.
- Der „Demo zurücksetzen"-Button der Referenz ist nur fürs Mockup (setzt Onboarding/Listen zurück) – in der App nicht nötig.
- Eingabe‑Validierung Budget: nur Zahlen ≥ 0.

## 16. Dateien im Bundle
- `reference/Camera Scanner V2.html` · `reference/app.css` · `reference/components.jsx` · `reference/v2.jsx`

## 17. Screenshots (Soll‑Zustand)
Im Ordner `screenshots/` (Referenz für die pixelgenaue Umsetzung):
- `01-onboarding.png` — Onboarding, bis zu 4 Favoriten wählen
- `02-live-scan.png` — Hauptscreen: Glas‑Menü, Scan‑Rahmen „Erkannt", Ergebnis‑Karte mit „Zu Liste hinzufügen", Edge‑Tab
- `03-picker.png` — Währungs‑Picker (Suche, Angepinnt + Trenner + alphabetisch, Pins)
- `04-add-to-list.png` — „Zu Liste hinzufügen" (nur Listen der Zielwährung + „Neue Liste")
- `05-lists-overview.png` — Listen‑Übersicht mit Budget‑Balken
- `06-list-detail.png` — Listen‑Detail: Summe, Budget‑Rest, Positionen mit Löschen
- `07-edit-list.png` — „Liste bearbeiten" (Name/Budget) + „Liste löschen", über dem Panel

