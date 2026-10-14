package com.andrerinas.openheadunit.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Point
import android.media.MediaCodecList
import android.os.Build
import android.view.WindowManager
import androidx.core.content.pm.PackageInfoCompat
import com.andrerinas.openheadunit.decoder.DecoderCapabilityReport
import com.andrerinas.openheadunit.profile.HeadUnitIdentity
import java.net.NetworkInterface
import java.util.Locale

/**
 * The head-unit facts behind per-car decisions, as lines for the diagnostics export.
 *
 * The ADB probe (`scripts/n9_probe.sh`) sees what an app cannot: other packages' receivers, kernel
 * key events, dumpsys. This section adds what only the app sees: identity values as the app reads
 * them (a vendor property can be blank here yet visible to the shell), its decoder list, its network
 * interface names and the vendor packages it is allowed to see. Rendering is pure; [collect] is the
 * only part that touches the device.
 */
object HeadUnitProbe {
    data class Display(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val refreshHz: Float,
        val modes: List<String>,
    )

    data class Decoder(val name: String, val mimeTypes: List<String>, val hardware: Boolean)

    /** [versionCode] is null when the package is not installed or not visible to the app. */
    data class VendorPackage(val name: String, val versionCode: Long?, val system: Boolean)

    /** Vendor packages the manifest's `<queries>` lets the app see. */
    val VENDOR_PACKAGES = listOf("com.ts.car.someip.service", "com.byd.amapservice", "com.byd.clusterdebug")

    fun render(
        identity: HeadUnitIdentity,
        display: Display?,
        decoders: List<Decoder>,
        interfaces: List<String>,
        packages: List<VendorPackage>,
    ): List<String> = buildList {
        fun shown(value: String) = value.ifBlank { "unknown" }
        add(
            "Identity: manufacturer=${shown(identity.manufacturer)} brand=${shown(identity.brand)} " +
                "model=${shown(identity.model)} board=${shown(identity.board)} " +
                "hardware=${shown(identity.hardware)} api=${identity.sdkInt}"
        )
        add("Fingerprint: ${shown(identity.fingerprint)}")
        identity.properties.forEach { (key, value) -> add("Property $key=${shown(value)}") }
        if (display == null) {
            add("Display: unknown")
        } else {
            add(
                String.format(
                    Locale.US, "Display: %dx%d aspect=%.2f density=%ddpi refresh=%.1fHz",
                    display.widthPx, display.heightPx, aspect(display.widthPx, display.heightPx),
                    display.densityDpi, display.refreshHz,
                )
            )
            if (display.modes.isNotEmpty()) add("Display modes: ${display.modes.joinToString(", ")}")
        }
        if (decoders.isEmpty()) add("Video decoders: none listed")
        decoders.forEach { decoder ->
            val kind = if (decoder.hardware) "hardware" else "software"
            add("Decoder $kind ${decoder.mimeTypes.joinToString("+")} ${decoder.name}")
        }
        add("Interfaces: ${interfaces.ifEmpty { listOf("none listed") }.joinToString(", ")}")
        packages.forEach { pkg ->
            val state = pkg.versionCode?.let { "versionCode=$it system=${pkg.system}" } ?: "not visible"
            add("Vendor package ${pkg.name}: $state")
        }
    }

    /** Long side over short side, so a portrait-reported panel reads the same as a landscape one. */
    private fun aspect(width: Int, height: Int): Float {
        val short = minOf(width, height)
        return if (short <= 0) 0f else maxOf(width, height).toFloat() / short
    }

    /** Reads the device. Every part fails soft: the probe must never break the report it feeds. */
    fun collect(context: Context): List<String> = render(
        HeadUnitIdentity.current,
        runCatching { readDisplay(context) }.getOrNull(),
        runCatching { readDecoders() }.getOrDefault(emptyList()),
        runCatching { readInterfaces() }.getOrDefault(emptyList()),
        VENDOR_PACKAGES.map { name ->
            runCatching { readPackage(context, name) }.getOrDefault(VendorPackage(name, null, false))
        },
    )

    @Suppress("DEPRECATION")
    private fun readDisplay(context: Context): Display {
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val metrics = context.resources.displayMetrics
        val size = Point(metrics.widthPixels, metrics.heightPixels)
        if (Build.VERSION.SDK_INT >= 17) display.getRealSize(size)
        val modes = if (Build.VERSION.SDK_INT >= 23) {
            display.supportedModes.map {
                String.format(Locale.US, "%dx%d@%.1f", it.physicalWidth, it.physicalHeight, it.refreshRate)
            }
        } else {
            emptyList()
        }
        return Display(size.x, size.y, metrics.densityDpi, display.refreshRate, modes)
    }

    private fun readDecoders(): List<Decoder> {
        if (Build.VERSION.SDK_INT < 21) return emptyList()
        return MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder }
            .mapNotNull { info ->
                val video = info.supportedTypes.filter { it.startsWith("video/", ignoreCase = true) }
                if (video.isEmpty()) null
                else Decoder(info.name, video, !DecoderCapabilityReport.isSoftwareName(info.name))
            }
    }

    /** Names and up/down only; addresses never leave the device in this section. */
    private fun readInterfaces(): List<String> =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .map { if (it.isUp) "${it.name}(up)" else it.name }
            .sorted()

    /** Throws when the package is absent or hidden; [collect] maps that to "not visible". */
    private fun readPackage(context: Context, name: String): VendorPackage {
        val info = context.packageManager.getPackageInfo(name, 0)
        val system = ((info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
        return VendorPackage(name, PackageInfoCompat.getLongVersionCode(info), system)
    }
}
