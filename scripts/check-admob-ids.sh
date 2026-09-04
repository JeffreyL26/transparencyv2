#!/usr/bin/env bash
# check-admob-ids.sh — Prüft vor einem Play-Upload, dass keine Google-Test-AdMob-IDs
# im Release landen (CLAUDE.md §9.2). adProp() in app/build.gradle.kts fällt still
# auf Test-IDs zurück, wenn die Keys in local.properties fehlen oder falsch heißen.
#
# Aufruf aus dem Projekt-Root (Git Bash / WSL / Linux / macOS):
#   bash scripts/check-admob-ids.sh            # inkl. ./gradlew :app:assembleRelease
#   bash scripts/check-admob-ids.sh --no-build # nur local.properties + vorhandene Artefakte
#
# Exit-Code 1, sobald irgendwo die Test-Publisher-ID 3940256099942544 auftaucht
# oder local.properties formal fehlerhaft ist.

set -u

TEST_PUBLISHER="3940256099942544"
EXPECTED_PUBLISHER="ca-app-pub-8240160347656225"
KEYS=(ADMOB_APP_ID ADMOB_NATIVE_UNIT_ID ADMOB_REWARDED_UNIT_ID ADMOB_BANNER_UNIT_ID)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1
PROPS="$ROOT/local.properties"
DO_BUILD=1
[ "${1:-}" = "--no-build" ] && DO_BUILD=0

fail=0
red()   { printf '\033[31m✗ %s\033[0m\n' "$*"; fail=1; }
ok()    { printf '\033[32m✓ %s\033[0m\n' "$*"; }
info()  { printf '  %s\n' "$*"; }
head_() { printf '\n== %s ==\n' "$*"; }

# ---------------------------------------------------------------------------
head_ "1) local.properties"
if [ ! -f "$PROPS" ]; then
    red "local.properties fehlt unter $ROOT"
else
    # BOM?
    if head -c 3 "$PROPS" | od -An -tx1 | tr -d ' \n' | grep -qi '^efbbbf'; then
        red "local.properties beginnt mit einer UTF-8-BOM — Gradle liest den ersten Key dann falsch"
    else
        ok "keine BOM"
    fi
    # CRLF-Hinweis (Properties-Loader toleriert CRLF, aber Werte mit \r am Ende sind ein Risiko)
    if grep -q $'\r' "$PROPS"; then
        info "Hinweis: CRLF-Zeilenenden vorhanden (von java.util.Properties toleriert)"
    fi

    for key in "${KEYS[@]}"; do
        # Zeile, die exakt mit dem Key beginnt und direkt ein '=' folgt (keine Leerzeichen).
        line="$(grep -E "^${key}=" "$PROPS" | tr -d '\r' | head -n1 || true)"
        if [ -z "$line" ]; then
            # Diagnose: gibt es den Key in falscher Schreibweise / mit Leerzeichen?
            loose="$(grep -iE "^[[:space:]]*${key}[[:space:]]*=" "$PROPS" | tr -d '\r' | head -n1 || true)"
            if [ -n "$loose" ]; then
                red "$key: Zeile gefunden, aber nicht exakt '${key}=<wert>' (Groß/Kleinschreibung oder Leerzeichen um '='): '$loose'"
            else
                red "$key fehlt in local.properties → Build fällt still auf Test-ID zurück"
            fi
            continue
        fi
        value="${line#*=}"
        case "$value" in
            *\"*|*\'*) red "$key: Wert enthält Anführungszeichen: $value"; continue ;;
            *" "*)     red "$key: Wert enthält Leerzeichen: '$value'"; continue ;;
            "")        red "$key: Wert ist leer"; continue ;;
        esac
        if [[ "$value" == *"$TEST_PUBLISHER"* ]]; then
            red "$key ist eine Google-Test-ID: $value"; continue
        fi
        sep='/'
        [ "$key" = "ADMOB_APP_ID" ] && sep='~'
        if [[ "$value" =~ ^${EXPECTED_PUBLISHER}${sep}[0-9]{10}$ ]]; then
            ok "$key = $value"
        else
            red "$key entspricht nicht dem Muster ${EXPECTED_PUBLISHER}${sep}<10 Ziffern>: $value"
        fi
    done
fi

# ---------------------------------------------------------------------------
head_ "2) Release-Build"
if [ "$DO_BUILD" -eq 1 ]; then
    if [ "$fail" -ne 0 ]; then
        info "local.properties fehlerhaft — Build wird trotzdem versucht (der Gradle-Guard sollte abbrechen)."
    fi
    GRADLEW="./gradlew"
    [ -f "./gradlew.bat" ] && [ "${OS:-}" = "Windows_NT" ] && GRADLEW="./gradlew.bat"
    if "$GRADLEW" :app:assembleRelease; then
        ok "assembleRelease erfolgreich"
    else
        red "assembleRelease fehlgeschlagen (erwartet, wenn der Release-Guard in app/build.gradle.kts Test-IDs erkannt hat)"
    fi
else
    info "Build übersprungen (--no-build); es werden vorhandene Artefakte geprüft."
fi

# ---------------------------------------------------------------------------
head_ "3) Generierte BuildConfig"
bc_files="$(find app/build/generated -type f -name 'BuildConfig.java' -path '*release*' 2>/dev/null)"
if [ -z "$bc_files" ]; then
    red "keine Release-BuildConfig.java unter app/build/generated gefunden"
else
    while IFS= read -r f; do
        info "$f"
        grep -oE 'ca-app-pub-[0-9]+[/~][0-9]+' "$f" | sort -u | sed 's/^/    /'
        if grep -q "$TEST_PUBLISHER" "$f"; then
            red "Test-ID in BuildConfig: $f"
        else
            ok "keine Test-ID in $f"
        fi
    done <<< "$bc_files"
fi

# ---------------------------------------------------------------------------
head_ "4) Release-APK (unzip + strings + grep)"
apks="$(find app/build/outputs -type f -name '*release*.apk' 2>/dev/null)"
if [ -z "$apks" ]; then
    red "kein Release-APK unter app/build/outputs gefunden"
else
    while IFS= read -r apk; do
        info "$apk"
        tmp="$(mktemp -d)"
        if ! unzip -o -q "$apk" -d "$tmp"; then
            red "unzip von $apk fehlgeschlagen"; rm -rf "$tmp"; continue
        fi
        # strings über alle Dateien (DEX + binäres Manifest); Fallback auf grep -a, falls strings fehlt.
        if command -v strings >/dev/null 2>&1; then
            found="$(find "$tmp" -type f -print0 | xargs -0 strings -n 8 2>/dev/null | grep -oE 'ca-app-pub-[0-9]*[/~][0-9]*' | sort -u)"
        else
            found="$(grep -raoE 'ca-app-pub-[0-9]*[/~][0-9]*' "$tmp" | sed 's/^[^:]*://' | sort -u)"
        fi
        # Das binäre Manifest kodiert Strings als UTF-16; zusätzlich dort suchen.
        if [ -f "$tmp/AndroidManifest.xml" ]; then
            m16="$(tr -d '\000' < "$tmp/AndroidManifest.xml" | grep -aoE 'ca-app-pub-[0-9]*[/~][0-9]*' | sort -u)"
            found="$(printf '%s\n%s\n' "$found" "$m16" | sed '/^$/d' | sort -u)"
        fi
        rm -rf "$tmp"
        if [ -z "$found" ]; then
            info "keine AdMob-IDs im APK gefunden (?)"
        else
            printf '%s\n' "$found" | sed 's/^/    /'
        fi
        if printf '%s\n' "$found" | grep -q "$TEST_PUBLISHER"; then
            red "Test-ID im Release-APK: $apk"
        else
            ok "keine Test-ID in $apk"
        fi
    done <<< "$apks"
fi

# ---------------------------------------------------------------------------
echo
if [ "$fail" -ne 0 ]; then
    printf '\033[31mERGEBNIS: FEHLER — nicht hochladen. Keys erwartet: %s\033[0m\n' "${KEYS[*]}"
    exit 1
fi
printf '\033[32mERGEBNIS: OK — keine Test-IDs gefunden.\033[0m\n'
exit 0
