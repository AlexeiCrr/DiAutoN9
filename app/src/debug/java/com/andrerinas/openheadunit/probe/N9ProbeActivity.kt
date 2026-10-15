package com.andrerinas.openheadunit.probe

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import com.andrerinas.openheadunit.hud.HudRouteProbe
import com.andrerinas.openheadunit.hud.HudRouteTestActivity
import com.andrerinas.openheadunit.utils.DiagnosticExportStore
import com.andrerinas.openheadunit.utils.DiagnosticReportRedactor
import com.andrerinas.openheadunit.utils.HeadUnitProbe
import com.andrerinas.openheadunit.utils.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import android.provider.Settings as SystemSettings

/**
 * Debug-only probe for owners without ADB: run it in the parked car from the launcher, then save one
 * redacted report to Downloads/DiAuto or a USB stick. Only reads, except the HUD route tests the
 * owner starts and the short audio-focus hold during the key test.
 */
class N9ProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var keyLog: TextView
    private val keyLines = mutableListOf<String>()
    private val routeResults = linkedMapOf<String, String>()
    @Volatile private var micResult = listOf("not run")
    private var keyTest: KeyTest? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        fun text(value: String, size: Float = 20f) = TextView(this).apply { text = value; textSize = size }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 20f
            setOnClickListener { action() }
        }
        status = text("Parked use only. Run the tests below, then save the report.", 24f)
        keyLog = text("Key test not run.")
        column.addView(status)
        column.addView(button("Save report to Downloads/DiAuto") { saveReport(null) })
        column.addView(button("Save report to... (USB stick)") { chooseDestination() })
        column.addView(text("\nSteering-wheel buttons: start, then press next, previous, play/pause, " +
            "voice (short) and voice (hold 2 s). Music pauses for 30 s while the test holds media focus."))
        column.addView(button("Start 30-second key test") { startKeyTest() })
        column.addView(keyLog)
        column.addView(text("\nMicrophone: start, then speak for 2 seconds. Nothing is recorded or kept."))
        column.addView(button("Start microphone test") { startMicTest() })
        column.addView(text("\nHUD routes: each test shows a left arrow at 500 m, a right arrow at 800 m, " +
            "then clears (15 s each). For someip, first turn on BYD navigation in the DiAuto HUD Test " +
            "app's settings. After each test, press Back and record what the windshield showed."))
        for (route in HudRouteProbe.ROUTES) {
            column.addView(button("HUD test: $route") {
                startActivity(Intent(this, HudRouteTestActivity::class.java).putExtra("route", route))
            })
            column.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                for (answer in listOf("saw all 3", "partly", "nothing")) {
                    addView(button(answer) {
                        routeResults[route] = answer
                        show("$route: $answer")
                    })
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyTest != null) record("window", event)
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopKeyTest()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQUEST_SAVE && resultCode == RESULT_OK && uri != null) saveReport(uri)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_MIC && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startMicTest()
        else if (requestCode == REQUEST_MIC) show("Microphone permission denied")
    }

    private fun show(text: String) = handler.post { status.text = text }

    private fun fileName() =
        "n9-probe-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    private fun chooseDestination() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain").putExtra(Intent.EXTRA_TITLE, fileName()),
                REQUEST_SAVE,
            )
        } catch (_: ActivityNotFoundException) {
            show("This car has no file picker: save to Downloads, then copy the file with a file manager")
        }
    }

    private fun saveReport(destination: Uri?) {
        show("Collecting...")
        val name = fileName()
        // Screen state is read on the main thread, while this screen is showing.
        val window = windowLines()
        val routes = HudRouteProbe.ROUTES.map { "$it: ${routeResults[it] ?: "not recorded"}" }
        Thread({
            try {
                val report = buildReport(window, routes)
                if (destination != null) {
                    DiagnosticExportStore.write(contentResolver, destination, report)
                    show("Report saved to the chosen location")
                } else if (Build.VERSION.SDK_INT >= 29) {
                    DiagnosticExportStore.saveToDownloads(contentResolver, name, report)
                    show("Report saved to Downloads/DiAuto/$name")
                } else {
                    show("Saving to Downloads needs Android 10; use 'Save report to...'")
                }
            } catch (error: Exception) {
                show("Save failed: ${error.javaClass.simpleName}: ${error.message}")
            }
        }, "n9-probe-save").start()
    }

    private fun buildReport(window: List<String>, routes: List<String>): String {
        val settings = Settings(this)
        @Suppress("DEPRECATION")
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val header = listOf(
            "DiAuto in-app N9 probe v$PROBE_VERSION, app $version",
            "Captured " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            "Redacted: addresses, IPs, coordinates, emails, phone numbers, long digit runs, VIN-shaped strings; " +
                "identifier properties are not read.",
            "Review this file before sharing or committing it.",
        )
        val sections = linkedMapOf<String, List<String>>()
        fun section(title: String, read: () -> List<String>) {
            sections[title] = runCatching { read() }.getOrElse { listOf("unavailable: ${it.javaClass.simpleName}") }
        }
        section("Head unit") { HeadUnitProbe.collect(this) }
        sections["Window and display"] = window
        section("Candidate properties (as the app reads them)") {
            N9ProbeReport.candidateProperties(exec("getprop"))
        }
        section("Vendor packages") { vendorPackages() }
        section("HUD and cluster receivers and services") { vendorEndpoints() }
        section("System services") { N9ProbeReport.systemServices(exec("service", "list")) }
        section("Settings") { settingLines() }
        section("Power and background") { powerLines() }
        section("Input devices") { inputDevices() }
        sections["Microphone test"] = micResult
        sections["Steering-wheel key test"] = synchronized(keyLines) { keyLines.toList() }.ifEmpty { listOf("not run") }
        sections["HUD route tests (what the windshield showed)"] = routes
        return N9ProbeReport.render(header, sections) { line ->
            DiagnosticReportRedactor.redact(line, settings.hotspotSsid, settings.hotspotPassword)
                ?.let(N9ProbeReport::maskIdentifiers)
        }
    }

    /** Runs a read-only command the app is allowed to run; its error text is evidence too. */
    private fun exec(vararg command: String): List<String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        return try {
            process.inputStream.bufferedReader().readLines()
        } finally {
            process.destroy()
        }
    }

    private fun windowLines(): List<String> = buildList {
        add("Window view: ${window.decorView.width}x${window.decorView.height}")
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val max = windowManager.maximumWindowMetrics.bounds
            val bars = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
            add("Window bounds: ${bounds.width()}x${bounds.height()}; maximum: ${max.width()}x${max.height()}")
            add("System bar insets: top=${bars.top} bottom=${bars.bottom} left=${bars.left} right=${bars.right}")
        }
    }

    private fun vendorPackages(): List<String> {
        @Suppress("DEPRECATION")
        val installed = packageManager.getInstalledPackages(0)
        return installed.filter { N9ProbeReport.isVendorPackage(it.packageName) }
            .sortedBy { it.packageName }
            .map {
                val system = ((it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
                "${it.packageName} versionCode=${PackageInfoCompat.getLongVersionCode(it)} system=$system"
            }
    }

    @Suppress("DEPRECATION")
    private fun vendorEndpoints(): List<String> {
        fun receivers(intent: Intent) = packageManager.queryBroadcastReceivers(intent, 0)
            .map { "${it.activityInfo.packageName}/${it.activityInfo.name} exported=${it.activityInfo.exported}" }
        fun line(label: String, found: List<String>) = "$label: " + found.ifEmpty { listOf("none") }.joinToString("; ")
        val clusterDebug = ComponentName("com.byd.clusterdebug", "com.byd.clusterdebug.BroadcastReceiverCAN")
        val someIp = ComponentName("com.ts.car.someip.service", "com.ts.car.someip.service.manager.SomeIpServerService")
        val provider = packageManager.resolveContentProvider("com.byd.car.server.provider.CarServiceProvider", 0)
        return listOf(
            line("AUTONAVI_STANDARD_BROADCAST_SEND receivers", receivers(Intent("AUTONAVI_STANDARD_BROADCAST_SEND"))),
            line("byd.hud.NAVIGATION receivers", receivers(Intent("byd.hud.NAVIGATION"))),
            line("clusterdebug receiver", receivers(Intent().setComponent(clusterDebug))),
            line("SOME/IP service", packageManager.queryIntentServices(Intent().setComponent(someIp), 0)
                .map { "${it.serviceInfo.packageName}/${it.serviceInfo.name} exported=${it.serviceInfo.exported}" }),
            line("Car server provider", listOfNotNull(provider?.let { "${it.packageName} exported=${it.exported}" })),
        )
    }

    private fun settingLines(): List<String> = listOf(
        "enable_freeform_support", "force_resizable_activities", "adb_enabled", "adb_wifi_enabled",
        "development_settings_enabled",
    ).map { key -> "$key=${runCatching { SystemSettings.Global.getString(contentResolver, key) }.getOrNull()}" }

    private fun powerLines(): List<String> = buildList {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val activity = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (Build.VERSION.SDK_INT >= 23) add("Ignoring battery optimizations: ${power.isIgnoringBatteryOptimizations(packageName)}")
        if (Build.VERSION.SDK_INT >= 28) add("Background restricted: ${activity.isBackgroundRestricted}")
        if (Build.VERSION.SDK_INT >= 19) add("Low-RAM device: ${activity.isLowRamDevice}")
    }

    /** External devices (Bluetooth remotes, phones) are listed without their names. */
    private fun inputDevices(): List<String> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        InputDevice.getDevice(id)?.let { device ->
            val external = Build.VERSION.SDK_INT >= 29 && device.isExternal
            val name = if (external) "(external, name withheld)" else device.name
            "id=$id sources=0x${Integer.toHexString(device.sources)} keyboard=${device.keyboardType} " +
                "external=$external name=$name"
        }
    }

    private fun record(via: String, event: KeyEvent) {
        val line = N9ProbeReport.keyLine(
            via, event.action == KeyEvent.ACTION_DOWN, KeyEvent.keyCodeToString(event.keyCode), event.keyCode,
            event.scanCode, event.repeatCount, event.isLongPress, event.source, event.deviceId,
        )
        synchronized(keyLines) { if (keyLines.size < N9ProbeReport.MAX_LINES) keyLines += line }
        handler.post { keyLog.text = synchronized(keyLines) { keyLines.takeLast(12).joinToString("\n") } }
    }

    private fun startKeyTest() {
        if (keyTest != null) return
        if (Build.VERSION.SDK_INT >= 21) {
            synchronized(keyLines) { keyLines.clear() }
            keyTest = KeyTest(this) { event -> record("media session", event) }.also { it.start() }
            show("Key test running for 30 s: press each button now")
            handler.postDelayed({ stopKeyTest(); show("Key test finished") }, KEY_TEST_MILLIS)
        } else {
            show("The key test needs Android 5")
        }
    }

    private fun stopKeyTest() {
        if (Build.VERSION.SDK_INT >= 21) keyTest?.stop()
        keyTest = null
    }

    @SuppressLint("MissingPermission")
    private fun startMicTest() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        show("Microphone test: speak now")
        Thread({
            val rate = 16_000
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val result = mutableListOf<String>()
            val recorder = runCatching {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, rate))
            }.getOrElse { result += "could not open: ${it.javaClass.simpleName}"; null }
            try {
                if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buffer = ShortArray(rate / 10)
                    var peak = 0
                    var samples = 0
                    val until = System.currentTimeMillis() + MIC_TEST_MILLIS
                    while (System.currentTimeMillis() < until) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read <= 0) break
                        samples += read
                        for (i in 0 until read) peak = maxOf(peak, abs(buffer[i].toInt()))
                    }
                    result += "source=VOICE_RECOGNITION recordingState=${recorder.recordingState} " +
                        "samples=$samples peak=$peak/32767"
                } else if (recorder != null) {
                    result += "could not initialise (state=${recorder.state})"
                }
            } catch (error: Exception) {
                result += "failed: ${error.javaClass.simpleName}"
            } finally {
                runCatching { recorder?.stop() }
                recorder?.release()
            }
            micResult = result
            show("Microphone test: ${result.joinToString("; ")}")
        }, "n9-probe-mic").start()
    }

    /**
     * Holds media focus, plays silence and owns an active media session, so media buttons route here
     * the way they would to DiAuto during Android Auto playback.
     */
    @RequiresApi(21)
    private class KeyTest(context: Context, private val onMediaKey: (KeyEvent) -> Unit) {
        private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val session = MediaSession(context, "DiAutoN9Probe")
        private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        private var focus: AudioFocusRequest? = null
        @Volatile private var playing = false

        fun start() {
            session.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)?.let(onMediaKey)
                    return true
                }
            })
            session.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            session.isActive = true
            if (Build.VERSION.SDK_INT >= 26) {
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes).build()
                focus?.let { audio.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            playing = true
            Thread({ playSilence() }, "n9-probe-silence").start()
        }

        fun stop() {
            playing = false
            session.isActive = false
            session.release()
            if (Build.VERSION.SDK_INT >= 26) {
                focus?.let { audio.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(null)
            }
        }

        private fun playSilence() {
            val rate = 44_100
            val size = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            @Suppress("DEPRECATION")
            val track = runCatching {
                AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM)
            }.getOrNull() ?: return
            try {
                track.play()
                val silence = ShortArray(size / 2)
                while (playing) track.write(silence, 0, silence.size)
            } catch (_: Exception) {
                // Best effort: without audio the session may simply not receive media keys.
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    private companion object {
        const val PROBE_VERSION = 1
        const val REQUEST_SAVE = 1
        const val REQUEST_MIC = 2
        const val KEY_TEST_MILLIS = 30_000L
        const val MIC_TEST_MILLIS = 2_000L
    }
}
