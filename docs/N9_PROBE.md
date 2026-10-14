# Denza N9 probe and baseline drive

Before any N9-specific behavior is written, the car itself has to say what it is and what it lets an
app do. This page covers the two owner-run steps that produce that evidence: the ADB probe and the
baseline drive. Their results go into `docs/N9_EVIDENCE.md`, and every N9 rule in the app cites them.

Do everything here **parked**.

## What you need

- A computer with Android platform-tools (`adb`) on the `PATH`, or `ADB=/path/to/adb` set.
- On Windows, Git Bash. The script turns off Git Bash's path conversion itself, so device paths such
  as `/sys/class/net` reach the car unchanged.
- ADB access to the head unit, over USB or `adb connect CAR_IP:5555`.

## 1. Run the probe

```bash
scripts/n9_probe.sh -o docs/n9
```

Options: `-s SERIAL` picks a device when more than one is connected; `--no-keys` skips the
steering-wheel step. `scripts/n9_probe.sh --self-test` checks the redaction rules without a car.

The probe only reads. It runs `getprop`, `dumpsys`, `cmd package`, `pm` and `getevent` and writes one
report, `n9-probe-<date>.txt`. Near the end it asks you to press, in order, **next, previous,
play/pause, voice (short) and voice (held for two seconds)** while it records key events.

It captures:

- Identity: an allowlist of build and vendor properties, plus any property whose name mentions the
  vehicle, DiLink, Denza, BYD, the platform or the series.
- Display size, density, modes and refresh rate, plus window modes, insets and the freeform setting.
- Video decoders from the vendor codec lists.
- Network interface names and state, hotspot and Wi-Fi Direct state, and Bluetooth profile owners.
- Vendor navigation packages and versions, the receivers for the Amap and `byd.hud.NAVIGATION`
  broadcasts, the SOME/IP service, and system services for the HUD, instrument and vehicle state.
- Audio input devices, background-running and power settings, and the ADB and developer settings
  (including `ro.adb.secure`).
- Steering-wheel buttons as kernel key events, input and media log lines, and recent broadcast
  action names.

It never captures network addresses, Bluetooth device names, the serial number, or full logs. It also
redacts MAC and IP addresses, coordinates, email addresses, phone numbers, long digit runs (IMEI and
ICCID) and VIN-shaped strings, and drops any line that names a password, passphrase, PSK, token,
private key or SSID.

**Open the report and read it before you commit it.** Search it for your name, phone, home area and
VIN. Then commit it as `docs/n9/probe-<firmware-build>.txt`.

## 2. Export the in-app probe section

Install the build, then open **Settings > Diagnostics** and save a report. Its new **Head unit
probe** section shows the identity values *as the app reads them*, together with the display, the
hardware and software decoders, interface names and the vendor packages the app can see. A vendor
property can be visible to `adb shell getprop` and still read blank to an app. Only a property that
is non-blank here can be used to detect the N9.

## 3. Baseline drive

Use the current build of this fork. Record every answer in `docs/N9_EVIDENCE.md` using the template at
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
6. **Buttons.** What does each wheel button do in Android Auto? (The probe recorded what it sends.)
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

## 4. HUD route tests (debug build, parked)

The debug build installs alongside the normal one as `com.andrerinas.headunitrevived.bydhudtest`.
Each route test sends one fixed sequence: **left arrow at 500 m, right arrow at 800 m, then a cleared
display**, 15 seconds per step. For each route, record which of the three you saw, and where
(windshield or cluster).

```bash
adb shell am start -n com.andrerinas.headunitrevived.bydhudtest/com.andrerinas.openheadunit.hud.HudRouteTestActivity --es route someip
```

The command returns at once. A test screen opens on the car display, shows each step as it is sent,
then shows whether the sequence finished and cleared or failed. Close it with Back before the next
route: Android may just bring an open test screen to the front instead of starting a new run, and a
start during a running test is ignored. Repeat with `--es route amap` and `--es route clusterdebug`.
The SOME/IP route needs **BYD navigation** turned on in the debug build's settings first. Watch
progress with `adb logcat -s DiAuto-HUD-Route`.

Two existing debug actions cover the BYD car-server route, which a shell-privileged route would build
on if no normal-app route works:

```bash
adb shell am broadcast -n com.andrerinas.headunitrevived.bydhudtest/com.andrerinas.openheadunit.hud.HudStatusReceiver -a com.andrerinas.openheadunit.HUD_SEND_TEST
adb shell am broadcast -n com.andrerinas.headunitrevived.bydhudtest/com.andrerinas.openheadunit.hud.HudStatusReceiver
adb logcat -d -s DiAuto-HUD-Send DiAuto-HUD-Status
```

Record the result codes and what the windshield showed. Uninstall the debug build when you have the
evidence: `adb uninstall com.andrerinas.headunitrevived.bydhudtest`.

## 5. Which property identifies the N9

Compare this car's identity properties with what other DiLink 150 cars report:

- Leopard 5: `ro.vehicle.type=DiLink150_7.0UI`, `ro.product.model=DiLink5.1`, chip mt8673
  (https://github.com/sunlixWhyNotAvailable/byd-hud/issues/4).
- Another N9: board `spm8673p1_64`, chip mt8673, 2560x1600 panel
  (https://github.com/shihabal3amri/DiAuto/issues/6).

Pick a property that differs from the Leopard values **and** is non-blank in the in-app probe
section. If none does, stop: N9 detection needs another signal before any N9 rule is written.

## Evidence template for `docs/N9_EVIDENCE.md`

```markdown
# Denza N9 evidence

- Firmware build (fingerprint):
- Probe report: docs/n9/probe-<firmware-build>.txt
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
| car-server (HUD_SEND_TEST) result codes | | | | |

## Still unknown
-
```
