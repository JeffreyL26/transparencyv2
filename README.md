# FX Lens — Kamera-Währungsscanner mit Reise-Budget-Listen (Android)

Native Android-Umsetzung (Kotlin, Jetpack Compose) des Design-Handoffs in
[`design_handoff/`](design_handoff/README.md) — ein 1:1-Nachbau der HTML/React-Referenz
inklusive aller Maße, Farben, Typografie und Animationen.

Die App rechnet Preise **live über die offene Kamera** in eine andere Währung um:
Nur die Zahl im zentrierten Scan-Rahmen (230×116 dp) wird erkannt und umgerechnet.
Umgerechnete Preise lassen sich per „+“ in **Listen** mit fester Zielwährung und
optionalem **Budget** sammeln (laufende Reise-Rechnung).

## Features

- **Onboarding**: bis zu 4 Favoriten-Währungen pinnen (persistent, DataStore)
- **Live-Scan**: CameraX-Preview + ML Kit Text Recognition v2 (on-device), nur im Scan-ROI;
  `scanning → locked` nach ~1 s stabiler Erkennung, Tap auf den Rahmen scannt erneut
- **Glas-Menü**: Von/Zu-Währungschips, federnder Swap (beide tauschen bei gleichem Paar),
  Live-Kurszeile mit Puls-Indikator
- **Währungs-Picker**: Suche über Code und Name, Pins (max 4) oben in fester Reihenfolge,
  übrige Währungen alphabetisch
- **Listen**: nur Listen der Zielwährung beim Hinzufügen; Anlegen, Umbenennen,
  Budget bearbeiten, Löschen mit Inline-Bestätigung; Budget-Balken inkl. Überschreitung
- **Live-Kurse**: [open.er-api.com](https://www.exchangerate-api.com/docs/free) (EUR-Basis,
  ~166 Währungen — Onboarding, Picker und Listen führen die komplette Liste), Datei-Cache,
  Offline-Fallback auf den letzten Stand bzw. die Demo-Kurse aus dem Handoff
- **Persistenz**: Room (Listen + Positionen), DataStore (Pins, Onboarding-Flag);
  Demo-Seed „USA Roadtrip“ / „Hongkong Reise“ beim ersten Start

## Tech-Stack

| Bereich | Wahl |
|---|---|
| UI | Jetpack Compose (eigenes Designsystem, bewusst ohne Material-Styling) |
| Kamera | CameraX `LifecycleCameraController` + `MlKitAnalyzer` (`COORDINATE_SYSTEM_VIEW_REFERENCED`) |
| Texterkennung | ML Kit Text Recognition v2, on-device |
| Persistenz | Room + DataStore |
| Kurse | open.er-api.com, EUR-normalisiert, 15-min-Refresh |
| Fonts | Plus Jakarta Sans + Space Grotesk (gebündelt, OFL — siehe `app/src/main/assets/licenses/`) |
| Flaggen | 156 lokale PNGs (`app/src/main/assets/flags/`, von flagcdn.com), rund maskiert |
| Min-SDK | 26 · Target/Compile-SDK 36 · Portrait-lock |
| App-Icon | Scanner-Badge aus `branding/icon-v1.svg` — adaptiv (dunkler Verlauf + Vordergrund) und als Legacy-Mipmaps gerendert |

## Build

```
# Windows (JDK 17+ erforderlich, z. B. das JBR von Android Studio)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

`local.properties` mit `sdk.dir` auf das Android SDK zeigen lassen (legt Android Studio
automatisch an).

> **Hinweis (maschinenspezifisch):** Scheitert der Gradle-Daemon mit
> `Unable to establish loopback connection` / `Invalid argument: connect`, sind
> Windows-AF_UNIX-Sockets im Temp-Pfad gestört (beobachtet mit 8.3-Kurznamen im
> Profilpfad + Fortinet-VPN-Filtertreibern). Workaround: `mkdir C:\tmp` und
> `set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/tmp` (wirkt auf Client-,
> Daemon- und Worker-JVMs; für Android Studio als Benutzer-Umgebungsvariable setzen).

## Bewusste Abweichungen von der Referenz

- **Backdrop-Blur** (CSS `backdrop-filter` der Glas-Flächen) ist über einem
  Kamera-Stream auf Android nicht praktikabel; die Glas-Flächen verwenden die
  spezifizierten halbtransparenten Glass-Farben (α 0.85/0.93) ohne Echtzeit-Blur.
- **Status-Bar**: echte System-Status-Bar (Edge-to-Edge) mit Top-Scrim statt des
  gemockten Overlays; helle/dunkle Icons je nach Schicht.
- **Nachkommastellen** folgen ICU je Währung (JPY 0 wie spezifiziert; bei 166 Währungen
  zusätzlich KRW/VND 0, BHD/KWD/TND 3), sonst 2.
- Der „Demo zurücksetzen“-Button der Referenz entfällt (laut Handoff §15 nur fürs Mockup).

## Struktur

```
design_handoff/        Original-Handoff (Spezifikation, Referenz-Prototyp, Screenshots)
app/src/main/java/com/transparency/fxlens/
  data/                CurrencyMeta, RatesRepository (er-api + Cache), Prefs, Room, ListsRepository
  domain/              Modelle, convert()/rate()/Formatierung (de-DE)
  scan/                CameraX-Preview + ML-Kit-ROI-Analyse (§12)
  ui/theme/            Design-Tokens (§10), Typografie, Motion (§11)
  ui/components/       Flagge, LiveDot, BudgetBar, Sheets, Buttons, Icons, Toast
  ui/screens/          Onboarding, Glas-Menü, Scan-Overlay, Picker, Listen-Sheets, Listen-Panel
```
