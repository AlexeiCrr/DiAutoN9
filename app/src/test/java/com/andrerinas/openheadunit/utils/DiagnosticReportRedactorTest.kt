package com.andrerinas.openheadunit.utils

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportRedactorTest {
    @Test fun removesCredentialsAndRetainsUsefulFailureDetails() {
        assertNull(DiagnosticReportRedactor.redact("SSID=car, password=secret", "car", "secret"))
        assertNull(DiagnosticReportRedactor.redact("payload=010203", "", ""))
        assertEquals("NativeAA: No usable BSSID; join failure status=-3", DiagnosticReportRedactor.redact(
            "NativeAA: No usable BSSID; join failure status=-3", "", ""))
        assertEquals("Network [saved value] at [address] [ip]", DiagnosticReportRedactor.redact(
            "Network My Car at 4e:b1:c7:94:48:3f 192.168.46.1", "My Car", "secret"))
    }

    @Test fun stillMasksAddressesAndDropsPassphrases() {
        assertEquals("inet6 [ip] scope link", DiagnosticReportRedactor.redact(
            "inet6 fe80::1c2d:3eff:fe4a:1b2c%wlan0 scope link", "", ""))
        assertNull(DiagnosticReportRedactor.redact("hotspot passphrase=hunter22", "", ""))
    }

    @Test fun masksWhereTheCarIsAndWhoDrivesIt() {
        assertEquals("fix lat=[location] lon=[location]", DiagnosticReportRedactor.redact(
            "fix lat=25.204849 lon=55.270783", "", ""))
        assertEquals("last fix [location] acc=4", DiagnosticReportRedactor.redact(
            "last fix 25.204849, 55.270783 acc=4", "", ""))
        assertEquals("account [email]", DiagnosticReportRedactor.redact("account alex.driver@example.com", "", ""))
        assertEquals("last call [phone] ended", DiagnosticReportRedactor.redact("last call +971 50 123 4567 ended", "", ""))
    }

    @Test fun keepsHeadUnitFactsIntact() {
        // Version codes, package names and words that merely contain "lat" must survive.
        for (line in listOf(
            "Vendor package com.byd.clusterdebug: versionCode=10601004 system=true",
            "Property ro.board.platform=mt8673",
            "Display modes: 2560x1600@60.0",
            "Decoder hardware video/hevc c2.mtk.hevc.decoder",
        )) assertEquals(line, DiagnosticReportRedactor.redact(line, "", ""))
    }
}
