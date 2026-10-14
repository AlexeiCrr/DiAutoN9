package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.profile.HeadUnitIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadUnitProbeTest {

    private val fingerprint = "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys"

    private val identity = HeadUnitIdentity.from(fingerprint, "BYD", "BYD", "DiLink5.1", "spm8673p1_64", "mt8673", 33) {
        if (it == "ro.board.platform") "mt8673" else null
    }

    private val n9Panel = HeadUnitProbe.Display(2560, 1600, 240, 60f, listOf("2560x1600@60.0"))

    private fun render(
        display: HeadUnitProbe.Display? = n9Panel,
        decoders: List<HeadUnitProbe.Decoder> = emptyList(),
        interfaces: List<String> = emptyList(),
        packages: List<HeadUnitProbe.VendorPackage> = emptyList(),
        who: HeadUnitIdentity = identity,
    ) = HeadUnitProbe.render(who, display, decoders, interfaces, packages)

    @Test
    fun `a hardware hevc decoder is listed as hardware`() {
        val lines = render(decoders = listOf(HeadUnitProbe.Decoder("c2.mtk.hevc.decoder", listOf("video/hevc"), true)))

        assertTrue(lines.contains("Decoder hardware video/hevc c2.mtk.hevc.decoder"))
    }

    @Test
    fun `a software decoder is labelled software`() {
        val lines = render(decoders = listOf(HeadUnitProbe.Decoder("c2.android.avc.decoder", listOf("video/avc"), false)))

        assertTrue(lines.contains("Decoder software video/avc c2.android.avc.decoder"))
    }

    @Test
    fun `display metrics carry size, aspect, density and refresh`() {
        val lines = render()

        assertTrue(lines.contains("Display: 2560x1600 aspect=1.60 density=240dpi refresh=60.0Hz"))
        assertTrue(lines.contains("Display modes: 2560x1600@60.0"))
    }

    @Test
    fun `a portrait-reported panel reads the same aspect`() {
        val lines = render(display = HeadUnitProbe.Display(1600, 2560, 240, 60f, emptyList()))

        assertTrue(lines.contains("Display: 1600x2560 aspect=1.60 density=240dpi refresh=60.0Hz"))
    }

    @Test
    fun `identity values and properties are listed as the app reads them`() {
        val lines = render()

        assertTrue(lines.contains("Fingerprint: $fingerprint"))
        assertTrue(lines.contains("Property ro.board.platform=mt8673"))
        assertTrue(lines.contains("Property ro.vehicle.type=unknown"))
        assertTrue(lines.first().contains("model=DiLink5.1 board=spm8673p1_64 hardware=mt8673 api=33"))
    }

    @Test
    fun `missing values render as unknown without throwing`() {
        val blank = HeadUnitIdentity.from(null, null, null, null, null, null, 0) { error("hidden") }
        val lines = render(display = null, who = blank)

        assertTrue(lines.contains("Fingerprint: unknown"))
        assertTrue(lines.first().contains("model=unknown"))
        assertTrue(lines.contains("Display: unknown"))
        assertTrue(lines.contains("Video decoders: none listed"))
        assertTrue(lines.contains("Interfaces: none listed"))
    }

    @Test
    fun `vendor packages show their version or that they are not visible`() {
        val lines = render(
            packages = listOf(
                HeadUnitProbe.VendorPackage("com.ts.car.someip.service", 3L, true),
                HeadUnitProbe.VendorPackage("com.byd.amapservice", null, false),
            ),
        )

        assertTrue(lines.contains("Vendor package com.ts.car.someip.service: versionCode=3 system=true"))
        assertTrue(lines.contains("Vendor package com.byd.amapservice: not visible"))
    }

    @Test
    fun `every probe line survives redaction unchanged`() {
        // The section is exported through the redactor; a probe fact it masked or dropped would be lost.
        val lines = render(
            decoders = listOf(HeadUnitProbe.Decoder("c2.mtk.hevc.decoder", listOf("video/hevc"), true)),
            interfaces = listOf("ap0(up)", "eth0", "p2p0", "wlan0(up)"),
            packages = listOf(HeadUnitProbe.VendorPackage("com.byd.clusterdebug", 10601004L, true)),
        )

        for (line in lines) assertEquals(line, DiagnosticReportRedactor.redact(line, "", ""))
    }
}
