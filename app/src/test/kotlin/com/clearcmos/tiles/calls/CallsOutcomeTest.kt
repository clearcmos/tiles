package com.clearcmos.tiles.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsOutcomeTest {
    private val reply = "AA:BB:CC:00:00:01 calls=on hfp=connected Soundcore Life Q30"

    @Test
    fun `a parsed reply with exit 0 is kept with no error`() {
        assertEquals(reply to null, CallsReply.outcome("calls status", 0, reply))
    }

    @Test
    fun `a parsed reply with a failing exit is kept and the last line becomes the error`() {
        val output = "$reply\ns25-bt: one headset refused"
        assertEquals(output to "s25-bt: one headset refused", CallsReply.outcome("calls on", 1, output))
    }

    @Test
    fun `output that parses to nothing is shown as the error and keeps no reply`() {
        val (kept, error) = CallsReply.outcome("calls status", 1, "s25-bt: phone not reachable over adb")
        assertNull(kept)
        assertEquals("s25-bt: phone not reachable over adb", error)
    }

    @Test
    fun `silent output falls back to naming the command and exit status`() {
        assertEquals(null to "calls off exited 255", CallsReply.outcome("calls off", 255, ""))
        // Exit 0 with nothing parseable is still a failure: no headsets means nothing was toggled.
        assertEquals(null to "calls status exited 0", CallsReply.outcome("calls status", 0, "  "))
    }

    @Test
    fun `a reply goes stale only after two minutes`() {
        val now = 10_000_000L
        assertFalse(CallsReply.isStale(now - 120_000L, now))
        assertTrue(CallsReply.isStale(now - 120_001L, now))
        // Never synced: updatedAt is 0, so the first shade open refreshes.
        assertTrue(CallsReply.isStale(0L, now))
    }
}
