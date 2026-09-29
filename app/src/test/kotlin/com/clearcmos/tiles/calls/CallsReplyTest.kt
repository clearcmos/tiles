package com.clearcmos.tiles.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsReplyTest {
    private val reply =
        """
        AA:BB:CC:00:00:01 calls=on hfp=connected Soundcore Life Q30
        AA:BB:CC:00:00:02 calls=off hfp=disconnected CMF Buds 2 Plus
        """.trimIndent()

    private fun headset(on: Boolean) = Headset("00:00:00:00:00:01", on, "connected", "x")

    @Test
    fun `parses one headset per line with names containing spaces`() {
        val headsets = CallsReply.parse(reply)
        assertEquals(2, headsets.size)
        assertEquals(Headset("AA:BB:CC:00:00:01", true, "connected", "Soundcore Life Q30"), headsets[0])
        assertEquals(Headset("AA:BB:CC:00:00:02", false, "disconnected", "CMF Buds 2 Plus"), headsets[1])
    }

    @Test
    fun `ignores error and notice lines`() {
        val headsets = CallsReply.parse("s25-bt: phone not reachable over adb\n$reply\nbt-helper: refused")
        assertEquals(2, headsets.size)
        assertTrue(CallsReply.parse("s25-bt: phone bluetooth is off").isEmpty())
    }

    @Test
    fun `a tap turns calls off only when every headset has it on`() {
        assertFalse(CallsReply.target(listOf(headset(true), headset(true))))
        assertTrue(CallsReply.target(listOf(headset(true), headset(false))))
        assertTrue(CallsReply.target(listOf(headset(false), headset(false))))
        assertTrue(CallsReply.target(emptyList()))
    }

    @Test
    fun `appearance follows the headsets`() {
        fun of(vararg on: Boolean) = CallsAppearance.of(true, false, false, on.map { headset(it) })
        assertEquals(CallsAppearance.ON, of(true, true))
        assertEquals(CallsAppearance.OFF, of(false, false))
        assertEquals(CallsAppearance.MIXED, of(true, false))
        assertEquals(CallsAppearance.UNKNOWN, of())
    }

    @Test
    fun `setup, in-flight and failure outrank the last reply`() {
        val all = listOf(headset(true))
        assertEquals(CallsAppearance.UNCONFIGURED, CallsAppearance.of(false, true, true, all))
        assertEquals(CallsAppearance.WORKING, CallsAppearance.of(true, true, true, all))
        assertEquals(CallsAppearance.FAILED, CallsAppearance.of(true, false, true, all))
    }
}
