package com.andrerinas.openheadunit.hud

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.andrerinas.openheadunit.utils.Settings

/** ADB-started route test as an activity, so no held broadcast delays the routes' own broadcasts. */
class HudRouteTestActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status = TextView(this).apply { textSize = 32f }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(status)
            addView(TextView(this@HudRouteTestActivity).apply {
                text = "Parked test. Watch for left 500 m, right 800 m, then a cleared display; " +
                    "${HudTestSteps.HOLD_MILLIS / 1_000} seconds per step."
                textSize = 22f
            })
        })
        // A recreated activity shows the run already in progress instead of starting a second one.
        if (state == null) start(intent.getStringExtra("route").orEmpty())
    }

    override fun onStart() { super.onStart(); visible = this; render() }
    override fun onStop() { if (visible === this) visible = null; super.onStop() }

    private fun render() {
        status.text = message
        if (running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun start(route: String) {
        if (running) { refuse("A route test is already running; wait for it to finish"); return }
        if (route == "someip" && !Settings(this).bydNavigationEnabled) {
            refuse("Turn on BYD navigation in DiAuto settings first; " +
                "the SOME/IP route clears while it is off")
            return
        }
        val send = HudRouteProbe.sender(this, route)
        if (send == null) {
            refuse("Unknown route '$route'; use one of ${HudRouteProbe.ROUTES}")
            return
        }
        running = true
        show("Route test started: $route")
        Log.i(HudRouteProbe.TAG, "$route sequence started")
        Thread({
            BydNavigationOutputs.syntheticHold = true
            BydNavigationOutputs.setDiagnosticHold(true)
            var outcome = "$route sequence failed"
            try {
                HudRouteProbe.run(send) { step ->
                    Log.i(HudRouteProbe.TAG, "$route $step")
                    handler.post { show("$route $step") }
                }
                outcome = "$route sequence finished and cleared"
                Log.i(HudRouteProbe.TAG, outcome)
            } catch (e: Exception) {
                outcome = "$route sequence failed: ${e.javaClass.simpleName}: ${e.message}"
                Log.w(HudRouteProbe.TAG, outcome)
            } finally {
                BydNavigationOutputs.syntheticHold = false
                BydNavigationOutputs.setDiagnosticHold(false)
                val result = outcome
                handler.post { running = false; show(result) }
            }
        }, "hud-route-test").start()
    }

    companion object {
        private val handler = Handler(Looper.getMainLooper())

        // Main thread only. A run outlives a recreated activity, so whichever instance is started
        // renders it; onStop drops that reference, so no stopped activity is retained.
        private var running = false
        private var message = "HUD route test ready"
        private var visible: HudRouteTestActivity? = null

        private fun refuse(text: String) { Log.w(HudRouteProbe.TAG, text); show(text) }

        private fun show(text: String) { message = text; visible?.render() }
    }
}
