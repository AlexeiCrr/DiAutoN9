package com.andrerinas.openheadunit.profile

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadUnitIdentityTest {

    private val n9Properties = mapOf(
        "ro.board.platform" to "mt8673",
        "ro.vehicle.type" to "DiLink150_6.0UI",
    )

    private fun n9(property: (String) -> String? = { n9Properties[it] }) = HeadUnitIdentity.from(
        fingerprint = "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260901.101010:user/release-keys",
        manufacturer = "BYD",
        brand = "BYD",
        model = "DiLink5.1",
        board = "spm8673p1_64",
        hardware = "mt8673",
        sdkInt = 33,
        property = property,
    )

    @After
    fun restoreDeviceIdentity() = HeadUnitIdentity.overrideForTest(null)

    @Test
    fun `builds the identity from build values and the fixed property list`() {
        val identity = n9()

        assertEquals("DiLink5.1", identity.model)
        assertEquals("spm8673p1_64", identity.board)
        assertEquals("mt8673", identity.hardware)
        assertEquals(33, identity.sdkInt)
        assertEquals("mt8673", identity.property("ro.board.platform"))
        assertEquals("DiLink150_6.0UI", identity.property("ro.vehicle.type"))
        assertEquals(HeadUnitIdentity.PROPERTY_KEYS.toSet(), identity.properties.keys)
    }

    @Test
    fun `an unset property reads blank rather than null`() {
        assertEquals("", n9().property("ro.soc.model"))
        assertEquals("", n9().property("ro.not.in.the.list"))
    }

    @Test
    fun `a property reader that throws leaves every property blank instead of failing`() {
        val identity = n9 { error("vendor property hidden from apps") }

        assertTrue(identity.properties.values.all { it.isEmpty() })
        assertEquals("DiLink5.1", identity.model)
    }

    @Test
    fun `missing build values and padded properties are normalised`() {
        val identity = HeadUnitIdentity.from(null, null, null, null, null, null, 0) { "  mt8673 " }

        assertEquals("", identity.fingerprint)
        assertEquals("", identity.model)
        assertEquals("mt8673", identity.property("ro.board.platform"))
    }

    @Test
    fun `only the fixed property list is ever read`() {
        val asked = mutableListOf<String>()
        n9 { asked += it; null }

        assertEquals(HeadUnitIdentity.PROPERTY_KEYS, asked)
    }

    @Test
    fun `an override pins the current identity`() {
        val identity = n9()
        HeadUnitIdentity.overrideForTest(identity)

        assertSame(identity, HeadUnitIdentity.current)
    }
}
