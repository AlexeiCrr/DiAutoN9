package com.andrerinas.openheadunit.utils

internal object DiagnosticReportRedactor {
    private val secret = Regex("(?i)\\b(password|passphrase|psk|token|private.?key|payload|hex)\\b|\\bssid\\s*[=:]")
    private val mac = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val ip = Regex("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
    private val ipv6 = Regex("(?i)(?:[0-9a-f]{1,4}:)*[0-9a-f]{0,4}::[0-9a-f:]*(?:%[a-z0-9_.-]+)?|(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}")
    // Where the car is and who drives it: a shared report must not carry either.
    private val coordinateField = Regex("(?i)\\b(lat(?:itude)?|lon(?:gitude)?|lng)([=: ]+)-?[0-9]{1,3}\\.[0-9]+")
    private val coordinatePair = Regex("-?[0-9]{1,3}\\.[0-9]{4,}\\s*,\\s*-?[0-9]{1,3}\\.[0-9]{4,}")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("\\+[0-9][0-9 ()-]{7,}[0-9]")
    fun redact(line: String, ssid: String, password: String): String? {
        if (secret.containsMatchIn(line)) return null
        var value = line
        for (saved in listOf(ssid, password).filter { it.isNotEmpty() }) value = value.replace(saved, "[saved value]")
        return value.replace(mac, "[address]").replace(ip, "[ip]").replace(ipv6, "[ip]")
            .replace(coordinateField, "$1$2[location]").replace(coordinatePair, "[location]")
            .replace(email, "[email]").replace(phone, "[phone]").take(1000)
    }
}
