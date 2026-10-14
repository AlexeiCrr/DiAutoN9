package com.andrerinas.openheadunit.hud

import com.andrerinas.openheadunit.hud.HudTestSteps.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudTestStepsTest {

    @Test
    fun `the sequence is left 500 m, right 800 m, then clear`() {
        assertEquals(listOf(Step.LEFT_500, Step.RIGHT_800, Step.CLEAR), HudTestSteps.SEQUENCE)

        val left = Step.LEFT_500.guidance()!!
        val right = Step.RIGHT_800.guidance()!!
        assertEquals(500, left.distanceMeters)
        assertEquals(800, right.distanceMeters)
        assertNull(Step.CLEAR.guidance())
    }

    @Test
    fun `left and right map to opposite arrows on every route's encoding`() {
        val left = Step.LEFT_500.guidance()!!
        val right = Step.RIGHT_800.guidance()!!

        assertNotEquals(left.clusterIcon, right.clusterIcon)
        assertNotEquals(left.hudArrow, right.hudArrow)
        // The windshield receiver's turn codes: 1 is left and 2 is right, as its packet tests pin.
        assertEquals(1, BydFactoryTurnCode.map(left.clusterIcon, left.roundaboutExit))
        assertEquals(2, BydFactoryTurnCode.map(right.clusterIcon, right.roundaboutExit))
    }

    @Test
    fun `each step encodes to a windshield packet`() {
        for (step in listOf(Step.LEFT_500, Step.RIGHT_800)) {
            val value = step.guidance()!!
            assertNotNull(BydStandalonePackets.guidance(value.clusterIcon, value.roundaboutExit, value.distanceMeters))
        }
    }

    @Test
    fun `each call builds fresh guidance so a held step never goes stale`() {
        val first = Step.LEFT_500.guidance()!!
        val second = Step.LEFT_500.guidance()!!

        assertNotSame(first, second)
        assertTrue(second.updatedNs >= first.updatedNs)
    }

    @Test
    fun `a held step is re-sent often enough to stay fresh`() {
        assertTrue(HudTestSteps.RESEND_MILLIS < HudTestSteps.HOLD_MILLIS)
        assertEquals(0L, HudTestSteps.HOLD_MILLIS % HudTestSteps.RESEND_MILLIS)
    }
}
