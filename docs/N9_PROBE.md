# Denza N9 probe and baseline drive

Before any N9-specific behavior is written, the car itself has to say what it is and what it lets an
app do. This page covers the owner-run steps that produce that evidence: the probe, the HUD route
tests and the baseline drive. Their results go into `docs/N9_EVIDENCE.md`, and every N9 rule in the
app cites them.

None of it needs a computer in the car. The probe is a screen in the debug build: you install the APK
from a USB stick like any other app, run the tests on the car screen, and save one report back to the
stick. (If you ever get ADB to the car, section 6 adds a deeper probe.)

Do everything here **parked**.

## 1. Install the debug build

1. On your computer, open the fork's **Actions** tab on GitHub, open the latest successful run for the
   `feat/denza-n9-support` branch, and download the **diauto-review-apk** artifact under
   **Artifacts** (you need to be signed in). It is a zip; unzip it to get the `.apk`.
2. Copy the `.apk` to a USB stick (FAT32 or exFAT).
3. In the car, install it from the stick the way you install other APKs.

It installs next to any other DiAuto as **DiAuto HUD Test**, and adds a second icon, **DiAuto N9
Probe**. Each CI run signs the APK with a different debug key, so a newer build only installs after
you uninstall the old one, which also clears its settings.

## 2. Run the probe

Open **DiAuto N9 Probe**. Work down the screen:

1. **Key test.** Tap **Start 30-second key test**, then press, in order, **next, previous,
   play/pause, voice (short) and voice (held for two seconds)**. Each event the app receives appears
   on screen. A press that shows nothing does not reach apps at all. Music pauses during the test,
   because the probe holds media focus the way DiAuto does during Android Auto playback.
2. **Microphone test.** Tap **Start microphone test**, allow the microphone, and speak for two
   seconds. Only whether the microphone opened and how loud the peak was are kept, never the audio.
3. **HUD route tests.** See section 3. Record each result with the buttons under its route.
4. **Save the report.** **Save report to... (USB stick)** opens the file picker; pick the USB stick.
   **Save report to Downloads/DiAuto** saves on the car instead.

The report captures, as the app sees them:

- Identity: build values and the vendor properties DiAuto would use to recognise the N9, plus any
  readable property whose name mentions the vehicle, DiLink, Denza, BYD, the platform or the series.
- The display, the window size and system-bar insets, and the video decoders.
- Network interface names, vendor navigation packages and versions, the receivers for the Amap and
  `byd.hud.NAVIGATION` broadcasts, the `clusterdebug` receiver, the SOME/IP service, the BYD
  car-server provider, and system services for the HUD, instrument and vehicle state (when the car
  lets an app list them).
- The freeform, ADB and developer settings, battery-optimisation and background restrictions, and
  the input devices (names left out for external ones).
- The key, microphone and HUD test results.

It never reads the serial number, VIN, IMEI, ICCID, MAC, Wi-Fi or Bluetooth properties. It drops any
line naming a password, passphrase, PSK, token, private key or SSID, and replaces your saved hotspot
name and password wherever they appear. It masks MAC and IP addresses, coordinates, email addresses,
phone numbers, long digit runs and VIN-shaped strings.

**Open the report and read it before you share or commit it.** Search it for your name, phone, home
area and VIN. Then send it, or commit it as `docs/n9/probe-<firmware-build>.txt`.

The app cannot see everything ADB can: kernel key events, logs, Bluetooth profile owners and
properties hidden from apps are missing. That is acceptable for a first pass: only a property the app
can read can be used to detect the N9 anyway.

## 3. HUD route tests

Each route test sends one fixed sequence: **left arrow at 500 m, right arrow at 800 m, then a cleared
display**, 15 seconds per step. In the probe, tap **HUD test: someip**, **amap** or
**clusterdebug**. A test screen shows each step as it is sent, then whether the sequence finished and
cleared or failed. Watch the windshield and the cluster. When it has finished, press Back and tap
**saw all 3**, **partly** or **nothing** for that route. If it was partly, note in
`docs/N9_EVIDENCE.md` which steps showed and where (windshield or cluster).

The **someip** route needs **BYD navigation** turned on first in the **DiAuto HUD Test** app's
settings. Starting a test while one is running is ignored.

## 4. Baseline drive

Use the debug build from section 1: it is the whole app, so **DiAuto HUD Test** is the DiAuto you
drive with. Record every answer in `docs/N9_EVIDENCE.md` using the template at
the end of this page.

1. **Settings first.** Set **Resolution to 1080p** and **Codec to H.264**. Upstream 0.3.11 asks an
   N9 for 1440p over H.264, and the phone drops the link about three seconds after video starts.
   Grant Nearby devices, then enter the car hotspot details as described in `docs/CONNECTION_SETUP.md`.
2. **Starts.** Over five car starts, time car start to the projection appearing, and note any start
   that needed a touch.
3. **Late hotspot.** Open DiAuto with the car hotspot off, then turn the hotspot on. Does Android Auto
   appear without reopening the app?
4. **Mid-drive drop.** Toggle the phone's Wi-Fi off and on during a drive. Does Android Auto come back
   on its own?
5. **Picture.** On the 16:10 panel, are there black bars, a stretched picture or a dead strip? Tap
   all four corners and the bottom edge. Do the taps land where you touched?
6. **Buttons.** What does each wheel button do in Android Auto? (The key test recorded what it sends.)
7. **Voice.** What do a short press and a long press of the voice button do? Does Google Assistant
   hear you through the car microphone? Does "navigate to …" work?
8. **Call.** Make a call during navigation. Is the audio in the car, and can both sides hear each
   other?
9. **Media.** Does music pause, skip twice, or fight between Android Auto and the car's Bluetooth?
10. **HUD and cluster.** If **BYD navigation** shows in settings, turn it on, navigate with Google Maps
    in Android Auto, and note what the windshield and the cluster show.
11. **Factory navigation.** With no factory route set, does the windshield show anything from factory
    navigation? Does factory guidance come back after a restart without you opening it?
12. **Before the first N9-profile build.** Run **Settings > Reset settings** (or clear DiAuto's app
    data). Then re-enter the hotspot details. Otherwise the settings this build saved count as your own
    choices, and the N9 defaults never take effect.

## 5. Which property identifies the N9

Compare this car's identity properties with what other DiLink 150 cars report:

- Leopard 5: `ro.vehicle.type=DiLink150_7.0UI`, `ro.product.model=DiLink5.1`, chip mt8673
  (https://github.com/sunlixWhyNotAvailable/byd-hud/issues/4).
- Another N9: board `spm8673p1_64`, chip mt8673, 2560x1600 panel
  (https://github.com/shihabal3amri/DiAuto/issues/6).

Pick a property that differs from the Leopard values **and** is non-blank in the probe report (it
lists only what the app can read). If none does, stop: N9 detection needs another signal before any
N9 rule is written.

## 6. Optional: the ADB probe

Only if you get ADB access to the car (over USB, or `adb connect CAR_IP:5555`) from a computer with
Android platform-tools. On Windows, use Git Bash; the script turns off its path conversion itself.

```bash
scripts/n9_probe.sh -o docs/n9
```

Options: `-s SERIAL` picks a device when more than one is connected; `--no-keys` skips the
steering-wheel step. `scripts/n9_probe.sh --self-test` checks the redaction rules without a car.

It only reads, and adds what the app cannot see: every property, kernel key events, input and media
log lines, `dumpsys` window, Bluetooth and hotspot state, and recent broadcast action names. It masks
the same kinds of values as the in-app probe. Read its report before committing it.

Over ADB, a HUD route test can also start without the probe screen:

```bash
adb shell am start -n com.andrerinas.headunitrevived.bydhudtest/com.andrerinas.openheadunit.hud.HudRouteTestActivity --es route someip
```

Use `--es route amap` or `--es route clusterdebug` for the others. The BYD car-server route, which a
shell-privileged route would build on if no normal-app route works, is ADB-only:

```bash
adb shell am broadcast -n com.andrerinas.headunitrevived.bydhudtest/com.andrerinas.openheadunit.hud.HudStatusReceiver -a com.andrerinas.openheadunit.HUD_SEND_TEST
adb shell am broadcast -n com.andrerinas.headunitrevived.bydhudtest/com.andrerinas.openheadunit.hud.HudStatusReceiver
adb logcat -d -s DiAuto-HUD-Send DiAuto-HUD-Status
```

Record the result codes and what the windshield showed.

## Evidence template for `docs/N9_EVIDENCE.md`

```markdown
# Denza N9 evidence

- Firmware build (fingerprint):
- Probe report: docs/n9/probe-<firmware-build>.txt (in-app probe v1; ADB probe: yes/no)
- N9 identity property and value (non-blank in the app?):

## Baseline drive (build, date)
| Check | Result |
|---|---|
| Five starts: time to projection, touches needed | |
| Late hotspot recovers without reopening | |
| Mid-drive Wi-Fi drop recovers | |
| Picture: bars, stretch or dead strip | |
| Taps at four corners and bottom edge | |
| Wheel buttons in Android Auto | |
| Voice short / long press; Assistant hears car mic | |
| Call during navigation | |
| Media audio pauses or fights | |
| HUD and cluster with BYD navigation on | |
| Factory navigation shows without being opened | |
| Settings reset before first N9 build | |

## HUD route tests
| Route | Left 500 m | Right 800 m | Cleared | Where |
|---|---|---|---|---|
| someip | | | | |
| amap | | | | |
| clusterdebug | | | | |
| car-server (HUD_SEND_TEST, ADB only) result codes | | | | |

## Still unknown
-
```
