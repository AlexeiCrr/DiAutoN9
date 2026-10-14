#!/usr/bin/env bash
# Owner-run evidence probe for DiAuto on a Denza N9 (works on any head unit).
# Read-only on the car: getprop, dumpsys, cmd and pm queries over adb, written to one redacted report.
#
# Usage: scripts/n9_probe.sh [-s SERIAL] [-o OUTPUT_DIR] [--no-keys]
#        scripts/n9_probe.sh --self-test
# See docs/N9_PROBE.md for what it captures and the baseline-drive checklist.
set -uo pipefail

# Git Bash rewrites absolute-looking arguments (/sys/class/net) into Windows paths before they
# reach adb, so remote commands would silently fail. Turn that off for this script.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ADB="${ADB:-adb}"
SERIAL=""
OUT_DIR="."
KEYS=1
KEY_SECONDS=25
PROBE_VERSION=1

# The redaction rules use GNU sed (\b, the I flag). Git Bash and Linux ship it; on macOS use gsed.
SED=sed
if ! sed --version >/dev/null 2>&1; then
    if command -v gsed >/dev/null 2>&1; then SED=gsed; else
        printf 'GNU sed is required (on macOS: brew install gnu-sed).\n' >&2; exit 1
    fi
fi

# Masks what is personal or locates the car, and drops lines that name a secret outright.
# Order matters: long digit runs are masked before the VIN pattern so a 17-digit number reads [number].
redact() {
    "$SED" -E \
        -e '/password|passphrase|psk|token|private.?key|ssid[[:space:]]*[=:]/Id' \
        -e 's/([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}/[address]/g' \
        -e 's/([0-9A-Fa-f]{1,4}:)*[0-9A-Fa-f]{0,4}::[0-9A-Fa-f:]*(%[A-Za-z0-9_.-]+)?/[ip]/g' \
        -e 's/\b([0-9]{1,3}\.){3}[0-9]{1,3}\b/[ip]/g' \
        -e 's/\b(lat(itude)?|lon(gitude)?|lng)([=: ]+)-?[0-9]{1,3}\.[0-9]+/\1\4[location]/Ig' \
        -e 's/-?[0-9]{1,3}\.[0-9]{4,}[[:space:]]*,[[:space:]]*-?[0-9]{1,3}\.[0-9]{4,}/[location]/g' \
        -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/[email]/g' \
        -e 's/\+[0-9][0-9 ()-]{7,}[0-9]/[phone]/g' \
        -e 's/\b[0-9]{14,20}\b/[number]/g' \
        -e 's/\b[A-HJ-NPR-Z0-9]{17}\b/[vin]/g' \
        -e 's/^(.{1000}).*/\1/'
}

self_test() {
    local failures=0
    check() { # input, expected (empty expected = line must be dropped)
        local got
        got=$(printf '%s\n' "$1" | redact)
        if [ "$got" = "$2" ]; then
            printf 'PASS  %s\n' "$1"
        else
            printf 'FAIL  %s\n      expected: [%s]\n      got:      [%s]\n' "$1" "$2" "$got"
            failures=$((failures + 1))
        fi
    }
    check 'link addr 4e:b1:c7:94:48:3f' 'link addr [address]'
    check 'inet 192.168.46.1/24 brd 192.168.46.255' 'inet [ip]/24 brd [ip]'
    check 'inet6 fe80::1c2d:3eff:fe4a:1b2c%wlan0 scope link' 'inet6 [ip] scope link'
    check 'hotspot password=secret' ''
    check 'Hotspot Passphrase: secret' ''
    check 'wpa_psk=abcdef0123' ''
    check 'session Token=abc' ''
    check '-----BEGIN PRIVATE KEY-----' ''
    check 'SSID: Home Network' ''
    check 'mBssid=4e:b1:c7:94:48:3f' ''
    check 'fix lat=25.204849 lon=55.270783' 'fix lat=[location] lon=[location]'
    check 'last fix 25.204849, 55.270783 acc=4' 'last fix [location] acc=4'
    check 'vehicle vin LGXCE4CB5N0123456' 'vehicle vin [vin]'
    check 'modem imei 356938035643809' 'modem imei [number]'
    check 'account alex.driver@example.com' 'account [email]'
    check 'last call +971 50 123 4567 ended' 'last call [phone] ended'
    check 'versionCode=10601004 minSdk=28 targetSdk=33' 'versionCode=10601004 minSdk=28 targetSdk=33'
    check 'ro.build.fingerprint=BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys' \
        'ro.build.fingerprint=BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys'
    check '10-14 12:30:45.123  1234  5678 I InputDispatcher: dispatchKey keyCode=87' \
        '10-14 12:30:45.123  1234  5678 I InputDispatcher: dispatchKey keyCode=87'
    check 'Display 0: 2560x1600 60.000004 fps density 240' 'Display 0: 2560x1600 60.000004 fps density 240'
    if [ "$failures" -ne 0 ]; then
        printf '%d redaction check(s) failed\n' "$failures"
        return 1
    fi
    printf 'All redaction checks passed\n'
}

usage() {
    sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        -s) SERIAL="${2:?-s needs a device serial}"; shift 2 ;;
        -o) OUT_DIR="${2:?-o needs a directory}"; shift 2 ;;
        --no-keys) KEYS=0; shift ;;
        --self-test) self_test; exit $? ;;
        -h|--help) usage; exit 0 ;;
        *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
done

adb_cmd() {
    if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}

# One remote command string per call, quoted, so the device shell sees it exactly as written.
adb_shell() {
    adb_cmd shell "$1" 2>&1 | tr -d '\r'
}

section() { # title, remote command
    local out status
    printf '\n=== %s ===\n' "$1"
    out=$(adb_shell "$2")
    status=$?
    # A remote grep that matches nothing exits 1, so empty output is "nothing found", not a failure.
    if [ -z "$out" ] && [ $status -ne 0 ]; then
        printf '(no output, exit %s)\n' "$status"
    elif [ -z "$out" ]; then
        printf '(no output)\n'
    else
        printf '%s\n' "$out" | redact
    fi
}

progress() { printf '%s\n' "$*" >&2; }

if ! command -v "$ADB" >/dev/null 2>&1; then
    printf 'adb not found. Install Android platform-tools or set ADB=/path/to/adb.\n' >&2
    exit 1
fi
if [ "$(adb_cmd get-state 2>/dev/null | tr -d '\r')" != "device" ]; then
    printf 'No device ready. Connect over USB or run: %s connect CAR_IP:5555\n' "$ADB" >&2
    exit 1
fi
[ -t 0 ] || KEYS=0

mkdir -p "$OUT_DIR"
REPORT="$OUT_DIR/n9-probe-$(date +%Y%m%d-%H%M%S).txt"

IDENTITY_KEYS="ro.build.fingerprint ro.build.display.id ro.build.version.release ro.build.version.sdk \
ro.build.version.incremental ro.build.date ro.product.manufacturer ro.product.brand ro.product.model \
ro.product.name ro.product.device ro.product.board ro.board.platform ro.hardware ro.soc.manufacturer \
ro.soc.model ro.vehicle.type ro.adb.secure ro.secure ro.debuggable"

VENDOR_PACKAGES="com.ts.car.someip.service com.byd.amapservice com.example.amapservice com.byd.clusterdebug \
com.byd.car.server com.byd.autovoice com.autonavi.amapauto"

{
    printf 'DiAuto head-unit probe v%s\n' "$PROBE_VERSION"
    printf 'Captured %s\n' "$(date '+%Y-%m-%d %H:%M %Z')"
    printf 'Redacted: addresses, IPs, coordinates, emails, phone numbers, long digit runs, VIN-shaped strings.\n'
    printf 'Review this file before committing it.\n'

    progress 'Identity…'
    section 'Identity (allowlisted properties)' \
        "for k in $IDENTITY_KEYS; do echo \"\$k=\$(getprop \$k)\"; done"
    section 'Identity candidates (pattern match)' \
        "getprop | grep -iE 'vehicle|dilink|denza|byd|carmodel|car_model|car\.model|series|platform|project' | grep -viE 'serial|vin|imei|iccid|mac|ssid|bt\.|wifi' | head -150"

    progress 'Displays and windows…'
    section 'Display size and density' 'wm size; wm density'
    section 'Display modes' \
        "dumpsys display | grep -E 'mDisplayId|DisplayDeviceInfo|mDefaultModeId|mActiveModeId|supportedModes|refreshRate|renderFrameRate|density|mBaseDisplayInfo' | head -80"
    section 'Window modes and insets' \
        "settings get global enable_freeform_support; settings get global force_resizable_activities; dumpsys window displays | grep -iE 'mDisplayId|freeform|windowing|mStable|mUnrestricted|insets' | head -60"

    progress 'Media codecs…'
    section 'Video decoders (codec XML)' \
        "ls /vendor/etc /odm/etc /system/etc 2>/dev/null | grep -i media_codecs; cat /vendor/etc/media_codecs*.xml /odm/etc/media_codecs*.xml 2>/dev/null | grep -iE 'MediaCodec name=' | grep -iE 'avc|hevc|vp9|av1' | head -60"

    progress 'Network…'
    section 'Network interfaces (names and state only)' \
        "for i in \$(ls /sys/class/net); do echo \"\$i operstate=\$(cat /sys/class/net/\$i/operstate 2>/dev/null)\"; done"
    section 'Hotspot and Wi-Fi Direct' \
        "dumpsys wifi | grep -iE 'SoftApState|ApInterface|apBand|mBand|channel|frequency|p2p' | head -60; dumpsys tethering | grep -iE 'state|interface|upstream' | head -40"

    progress 'Bluetooth…'
    section 'Bluetooth adapter and profiles (no device names)' \
        "dumpsys bluetooth_manager | grep -iE 'enabled|state:|profile|a2dp|headset|hfp|avrcp|mapclient|pbapclient|connected' | grep -viE 'name|alias' | head -80"

    progress 'Vendor navigation services…'
    section 'Vendor packages' \
        "pm list packages | grep -iE 'byd|amap|autonavi|gaode|someip|ts\.car|clusterdebug|hud|navi|voice|carserver|dicar' | sort"
    section 'Vendor package versions' \
        "for p in $VENDOR_PACKAGES; do d=\$(dumpsys package \$p); v=\$(printf '%s\n' \"\$d\" | grep -m1 -oE 'versionCode=[0-9]+'); f=\$(printf '%s\n' \"\$d\" | grep -m1 -oE 'pkgFlags=\[[^]]*\]'); echo \"\$p: \${v:-not installed} \$f\"; done"
    section 'HUD and cluster receivers and services' \
        "echo '-- AUTONAVI_STANDARD_BROADCAST_SEND receivers'; cmd package query-receivers --brief -a AUTONAVI_STANDARD_BROADCAST_SEND; echo '-- byd.hud.NAVIGATION receivers'; cmd package query-receivers --brief -a byd.hud.NAVIGATION; echo '-- SOME/IP service'; cmd package query-services --brief -a com.ts.car.someip.SomeIpServerService"
    section 'System services (HUD, instrument, vehicle state)' \
        "service list | grep -iE 'someip|hud|instrument|vehicle|car|byd|speed|gear' | head -80"
    section 'Car server provider' \
        "dumpsys activity providers | grep -iE 'byd|car\.server' | head -20"

    progress 'Audio input…'
    section 'Audio input devices' \
        "dumpsys audio | grep -iE 'AUDIO_DEVICE_IN|input|mic|record' | head -40"

    progress 'Background running and ADB…'
    section 'Background and power settings' \
        "dumpsys deviceidle whitelist | head -60; for t in global system secure; do settings list \$t | grep -iE 'background|kill|clean|freeze|autostart|quick|boost' | sed \"s/^/\$t: /\"; done"
    section 'ADB and developer options' \
        "echo adb_enabled=\$(settings get global adb_enabled); echo adb_wifi_enabled=\$(settings get global adb_wifi_enabled); echo development_settings_enabled=\$(settings get global development_settings_enabled); echo service.adb.tcp.port=\$(getprop service.adb.tcp.port); echo persist.adb.tcp.port=\$(getprop persist.adb.tcp.port); echo ro.adb.secure=\$(getprop ro.adb.secure)"

    section 'Input devices' \
        "dumpsys input | grep -iE 'Device [0-9]+:|Sources:|KeyLayoutFile|KeyCharacterMapFile|Identifier' | head -80"
} > "$REPORT"

if [ "$KEYS" -eq 1 ]; then
    printf '\nSteering-wheel buttons: after you press Enter you have %s seconds.\n' "$KEY_SECONDS" >&2
    printf 'Press, in order, with a pause between: next, previous, play/pause, voice (short), voice (hold 2 s).\n' >&2
    read -r -p 'Press Enter to start…' _
    # Read only the log lines written from now on, by the car's own clock; never clear its buffers.
    SINCE=$(adb_shell "date '+%m-%d %H:%M:%S.000'")
    if [ -n "$SINCE" ]; then LOG_WINDOW="-T '$SINCE'"; else LOG_WINDOW='-t 2000'; fi
    {
        printf '\n=== Steering-wheel buttons (kernel key events) ===\n'
        printf 'Order pressed: next, previous, play/pause, voice short, voice long\n'
        adb_shell "timeout $KEY_SECONDS getevent -lt" | grep -E 'EV_KEY|add device|name:' | redact
        section 'Steering-wheel buttons (input and media log tags)' \
            "logcat -d $LOG_WINDOW -s InputDispatcher:V InputReader:V KeyEvent:V MediaSessionService:V AudioService:V *:S | grep -iE 'key|media|voice|button' | head -120"
        section 'Recent broadcast actions (names only)' \
            "dumpsys activity broadcasts history | grep -oE 'act=[A-Za-z0-9._]+' | sort | uniq -c | sort -rn | head -50"
    } >> "$REPORT"
else
    printf '\n=== Steering-wheel buttons ===\nskipped (--no-keys or no terminal)\n' >> "$REPORT"
fi

progress ''
progress "Report written to $REPORT"
progress 'Before committing it, open the file and search for your name, phone, home area and VIN.'
