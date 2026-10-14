package com.andrerinas.openheadunit.hud

import android.content.Context

/**
 * Debug-only: sends [HudTestSteps] through exactly one HUD route, so the owner can see which routes
 * render on this firmware before any of them is trusted. Reached only from an ADB shell through
 * [HudStatusReceiver]; no production path uses it.
 */
internal object HudRouteProbe {
    const val TAG = "DiAuto-HUD-Route"
    val ROUTES = listOf("someip", "amap", "clusterdebug")

    /** A sender for [route], or null for an unknown route name. */
    fun sender(context: Context, route: String): ((BydGuidance?) -> Unit)? {
        val app = context.applicationContext
        return when (route) {
            "someip" -> {
                BydHudBridge.initialize(app)
                return BydHudBridge::update
            }
            "amap" -> BydClusterOutput(app)::update
            "clusterdebug" -> {
                // No firmware gate here on purpose: this is the parked, owner-triggered check of
                // whether the stock receiver exists and renders on this car at all.
                val session = BydStandaloneSession(
                    send = { packet -> BydStandaloneHudOutput.dispatch(app, packet) },
                    rememberPendingClear = {},
                )
                return { value ->
                    if (value == null) session.clear()
                    else session.update(value.clusterIcon, value.roundaboutExit, value.distanceMeters, value.road)
                }
            }
            else -> null
        }
    }

    /**
     * Runs the shared sequence on the calling thread: each guidance step is re-sent every
     * [HudTestSteps.RESEND_MILLIS] for [HudTestSteps.HOLD_MILLIS], the clear step once. A final
     * clear always follows, even when a send throws.
     */
    fun run(send: (BydGuidance?) -> Unit, sleep: (Long) -> Unit = Thread::sleep, log: (String) -> Unit) {
        try {
            HudTestSteps.SEQUENCE.forEachIndexed { index, step ->
                log("step ${index + 1}/${HudTestSteps.SEQUENCE.size}: ${step.label}")
                var held = 0L
                do {
                    send(step.guidance())
                    sleep(HudTestSteps.RESEND_MILLIS)
                    held += HudTestSteps.RESEND_MILLIS
                } while (step != HudTestSteps.Step.CLEAR && held < HudTestSteps.HOLD_MILLIS)
            }
        } finally {
            runCatching { send(null) }
        }
    }
}
