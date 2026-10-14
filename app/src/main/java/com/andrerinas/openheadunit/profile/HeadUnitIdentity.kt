package com.andrerinas.openheadunit.profile

import android.os.Build
import com.andrerinas.openheadunit.utils.SystemProperties

/**
 * What this head unit says it is: the `Build` values plus a short, fixed list of system properties.
 *
 * Read once per process and safe before the unit is unlocked: it touches neither `Settings` nor any
 * credential-encrypted storage, so a process started in Direct Boot sees the same identity as one
 * started later. `Build.MODEL` alone cannot tell BYD head units apart - other DiLink 150 cars report
 * `DiLink5.1` too - which is why the board, hardware and vendor properties travel with it.
 */
data class HeadUnitIdentity(
    val fingerprint: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val board: String,
    val hardware: String,
    val sdkInt: Int,
    /** Values of [PROPERTY_KEYS]; blank when a property is unset or unreadable from the app. */
    val properties: Map<String, String>,
) {
    fun property(key: String): String = properties[key].orEmpty()

    companion object {
        /**
         * Properties worth reading on every start. Short on purpose: a value a vendor build hides from
         * apps reads back blank here even when `adb shell getprop` shows it, so every key added must
         * first appear non-blank in the in-app diagnostics probe.
         */
        val PROPERTY_KEYS = listOf(
            "ro.board.platform",
            "ro.soc.manufacturer",
            "ro.soc.model",
            "ro.vehicle.type",
            "ro.product.device",
            "ro.product.name",
            "ro.build.display.id",
        )

        /** Builds an identity from raw values; [property] may return null or throw for any key. */
        fun from(
            fingerprint: String?,
            manufacturer: String?,
            brand: String?,
            model: String?,
            board: String?,
            hardware: String?,
            sdkInt: Int,
            property: (String) -> String?,
        ): HeadUnitIdentity = HeadUnitIdentity(
            fingerprint = fingerprint.orEmpty().trim(),
            manufacturer = manufacturer.orEmpty().trim(),
            brand = brand.orEmpty().trim(),
            model = model.orEmpty().trim(),
            board = board.orEmpty().trim(),
            hardware = hardware.orEmpty().trim(),
            sdkInt = sdkInt,
            properties = PROPERTY_KEYS.associateWith { key ->
                runCatching { property(key) }.getOrNull().orEmpty().trim()
            },
        )

        @Volatile private var override: HeadUnitIdentity? = null

        private val device: HeadUnitIdentity by lazy {
            from(
                Build.FINGERPRINT, Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.BOARD,
                Build.HARDWARE, Build.VERSION.SDK_INT,
            ) { key -> SystemProperties.get(key) }
        }

        /** This process's identity. Tests pin it with [overrideForTest] instead of reading `Build`. */
        val current: HeadUnitIdentity
            get() = override ?: device

        /** Pins [current] for a test; null returns to the device's own identity. */
        internal fun overrideForTest(identity: HeadUnitIdentity?) {
            override = identity
        }
    }
}
