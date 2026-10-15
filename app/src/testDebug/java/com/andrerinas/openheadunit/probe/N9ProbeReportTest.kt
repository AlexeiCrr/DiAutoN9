package com.andrerinas.openheadunit.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class N9ProbeReportTest {

    @Test
    fun `candidate properties keep vehicle identity and drop identifiers`() {
        val kept = N9ProbeReport.candidateProperties(
            listOf(
                "[ro.vehicle.type]: [DiLink150_6.0UI]",
                "[ro.board.platform]: [mt8673]",
                "[ro.serialno]: [ABC123]",
                "[persist.byd.vin]: [LGXCE4CB5N0123456]",
                "[persist.byd.wifi.ssid]: [Home]",
                "[ro.build.version.sdk]: [33]",
            ),
        )

        assertEquals(listOf("[ro.vehicle.type]: [DiLink150_6.0UI]", "[ro.board.platform]: [mt8673]"), kept)
    }

    @Test
    fun `candidate properties are capped`() {
        val many = (1..400).map { "[ro.vehicle.p$it]: [x]" }

        assertEquals(N9ProbeReport.MAX_LINES, N9ProbeReport.candidateProperties(many).size)
    }

    @Test
    fun `identifier masks hide digit runs and VIN-shaped values but keep version codes`() {
        assertEquals(
            "[persist.byd.carinfo]: [[vin]] imei=[number] versionCode=10601004",
            N9ProbeReport.maskIdentifiers(
                "[persist.byd.carinfo]: [LGXCE4CB5N0123456] imei=861234567890123 versionCode=10601004",
            ),
        )
        assertEquals("id=[number]", N9ProbeReport.maskIdentifiers("id=12345678901234567"))
    }

    @Test
    fun `system services keep only HUD, instrument and vehicle services`() {
        val kept = N9ProbeReport.systemServices(
            listOf("12 someip: [ts.car.someip.sdk.ISomeIpServerInterface]", "13 window: [android.view.IWindowManager]"),
        )

        assertEquals(listOf("12 someip: [ts.car.someip.sdk.ISomeIpServerInterface]"), kept)
    }

    @Test
    fun `vendor packages are recognised by name`() {
        assertTrue(N9ProbeReport.isVendorPackage("com.ts.car.someip.service"))
        assertTrue(N9ProbeReport.isVendorPackage("com.byd.amapservice"))
        assertFalse(N9ProbeReport.isVendorPackage("com.google.android.gms"))
    }

    @Test
    fun `a key line carries codes and flags but no device name`() {
        val line = N9ProbeReport.keyLine("window", true, "KEYCODE_MEDIA_NEXT", 87, 163, 0, false, 0x101, 4)

        assertEquals(
            "window down KEYCODE_MEDIA_NEXT (87) scan=163 repeat=0 longPress=false source=0x101 device=4",
            line,
        )
    }

    @Test
    fun `render redacts every line, drops removed lines and marks empty sections`() {
        val report = N9ProbeReport.render(
            header = listOf("DiAuto N9 probe", "secret header"),
            sections = linkedMapOf(
                "Kept" to listOf("visible", "secret line"),
                "Empty" to emptyList(),
                "All removed" to listOf("secret only"),
            ),
        ) { line -> if (line.contains("secret")) null else line.uppercase() }

        val expected = listOf(
            "DIAUTO N9 PROBE",
            "",
            "=== Kept ===",
            "VISIBLE",
            "",
            "=== Empty ===",
            "(none)",
            "",
            "=== All removed ===",
            "(none)",
        )
        assertEquals(expected, report.trimEnd().lines())
    }
}
