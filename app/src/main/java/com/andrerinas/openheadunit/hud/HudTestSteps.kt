package com.andrerinas.openheadunit.hud

/**
 * The one fixed sequence every HUD route test sends: left at 500 m, right at 800 m, then clear.
 *
 * Shared by the debug per-route action and the in-car HUD test so both ask the driver to confirm the
 * same three things - direction, distance and a clean clear - not merely that "an arrow appeared".
 */
internal object HudTestSteps {
    /** How long each step stays up: long enough to read from the driver's seat and log a result. */
    const val HOLD_MILLIS = 15_000L

    /** How often a held step is re-sent; routes that expire stale guidance need a steady refresh. */
    const val RESEND_MILLIS = 1_000L

    // Android Auto NavigationManeuver types, mapped by BydNavigationMapper like live guidance.
    private const val TURN_NORMAL_LEFT = 7
    private const val TURN_NORMAL_RIGHT = 8

    enum class Step(val label: String, private val maneuverType: Int?, val distanceMeters: Int) {
        LEFT_500("left arrow, 500 m", TURN_NORMAL_LEFT, 500),
        RIGHT_800("right arrow, 800 m", TURN_NORMAL_RIGHT, 800),
        CLEAR("cleared display", null, 0);

        /** Fresh guidance for this step, or null for [CLEAR]. Built at send time so it is never stale. */
        fun guidance(): BydGuidance? {
            val type = maneuverType ?: return null
            val codes = checkNotNull(BydNavigationMapper.codes(type)) { "Unmapped test maneuver $type" }
            return BydGuidance(codes.cluster, codes.gaode, codes.arrow, codes.exit, distanceMeters, "")
        }
    }

    val SEQUENCE: List<Step> = Step.values().toList()
}
