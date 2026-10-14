package com.andrerinas.openheadunit.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HudRouteProbeTest {

    private val holds = (HudTestSteps.HOLD_MILLIS / HudTestSteps.RESEND_MILLIS).toInt()

    @Test
    fun `each guidance step is held and re-sent, then the display is cleared`() {
        val sent = mutableListOf<BydGuidance?>()
        var slept = 0L

        HudRouteProbe.run(send = { sent += it }, sleep = { slept += it }, log = {})

        assertEquals(holds, sent.count { it?.distanceMeters == 500 })
        assertEquals(holds, sent.count { it?.distanceMeters == 800 })
        assertTrue(sent.indexOfLast { it?.distanceMeters == 500 } < sent.indexOfFirst { it?.distanceMeters == 800 })
        assertNull(sent.last())
        assertEquals((2 * holds + 1) * HudTestSteps.RESEND_MILLIS, slept)
    }

    @Test
    fun `a send that throws still ends with a clear`() {
        val sent = mutableListOf<BydGuidance?>()
        val thrown = assertThrows(IllegalStateException::class.java) {
            HudRouteProbe.run(send = { sent += it; if (it != null) error("transport down") }, sleep = {}, log = {})
        }
        assertEquals("transport down", thrown.message)
        assertNull(sent.last())
    }

    @Test
    fun `steps are logged in order with what the owner should see`() {
        val logged = mutableListOf<String>()

        HudRouteProbe.run(send = {}, sleep = {}, log = { logged += it })

        assertEquals(
            listOf("step 1/3: left arrow, 500 m", "step 2/3: right arrow, 800 m", "step 3/3: cleared display"),
            logged,
        )
    }
}
