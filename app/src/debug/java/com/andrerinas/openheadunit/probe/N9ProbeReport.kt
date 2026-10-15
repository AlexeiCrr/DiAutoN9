package com.andrerinas.openheadunit.probe

/**
 * The pure half of the in-app N9 probe: which raw lines are kept, how a key event reads, and how the
 * report is assembled. Mirrors the filters of `scripts/n9_probe.sh` so both probes keep the same
 * evidence for owners with and without ADB.
 */
internal object N9ProbeReport {
    /** Property lines worth comparing across DiLink 150 cars. */
    private val candidateProperty =
        Regex("(?i)vehicle|dilink|denza|byd|carmodel|car_model|car\\.model|series|platform|project")

    /** Never kept, whatever else the line says: identifiers and secrets. */
    private val excludedProperty = Regex("(?i)serial|vin|imei|iccid|mac|ssid|bt\\.|wifi|pwd|pass|secret")

    private val systemService = Regex("(?i)someip|hud|instrument|vehicle|car|byd|speed|gear")

    private val vendorPackage =
        Regex("(?i)byd|amap|autonavi|gaode|someip|ts\\.car|clusterdebug|hud|navi|voice|carserver|dicar")

    private val digitRun = Regex("\\b[0-9]{14,20}\\b")
    private val vin = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b")

    /** At most this many lines per raw-output section, so one noisy source cannot swamp the report. */
    const val MAX_LINES = 150

    /**
     * The shell probe's masks that the diagnostics redactor lacks: IMEI- and ICCID-length digit runs,
     * then VIN-shaped strings. Digit runs go first, so a 17-digit number reads `[number]`.
     */
    fun maskIdentifiers(line: String): String = line.replace(digitRun, "[number]").replace(vin, "[vin]")

    fun candidateProperties(getprop: List<String>): List<String> =
        getprop.filter { candidateProperty.containsMatchIn(it) && !excludedProperty.containsMatchIn(it) }
            .take(MAX_LINES)

    fun systemServices(serviceList: List<String>): List<String> =
        serviceList.filter { systemService.containsMatchIn(it) }.take(MAX_LINES)

    fun isVendorPackage(packageName: String): Boolean = vendorPackage.containsMatchIn(packageName)

    /** One received key event; device names are left out because they can name a paired phone. */
    fun keyLine(
        via: String,
        down: Boolean,
        keyName: String,
        keyCode: Int,
        scanCode: Int,
        repeat: Int,
        longPress: Boolean,
        inputSource: Int,
        deviceId: Int,
    ): String = "$via ${if (down) "down" else "up"} $keyName ($keyCode) scan=$scanCode repeat=$repeat " +
        "longPress=$longPress source=0x${Integer.toHexString(inputSource)} device=$deviceId"

    /**
     * Header, then one titled block per section. Every line goes through [redact]; a dropped line is
     * gone, and a section left empty says "(none)" so absence is explicit rather than silent.
     */
    fun render(header: List<String>, sections: Map<String, List<String>>, redact: (String) -> String?): String =
        buildString {
            header.forEach { line -> redact(line)?.let { appendLine(it) } }
            sections.forEach { (title, lines) ->
                appendLine()
                appendLine("=== $title ===")
                val kept = lines.mapNotNull(redact)
                if (kept.isEmpty()) appendLine("(none)") else kept.forEach { appendLine(it) }
            }
        }
}
