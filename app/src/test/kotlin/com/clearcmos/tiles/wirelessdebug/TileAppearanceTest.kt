package com.clearcmos.tiles.wirelessdebug

import org.junit.Assert.assertEquals
import org.junit.Test

class TileAppearanceTest {
    @Test
    fun `granted and enabled reads as active`() {
        assertEquals(TileAppearance.ON, TileAppearance.of(enabled = true, canWrite = true))
    }

    @Test
    fun `granted and disabled reads as inactive`() {
        assertEquals(TileAppearance.OFF, TileAppearance.of(enabled = false, canWrite = true))
    }

    @Test
    fun `a missing grant outranks the setting in both directions`() {
        // Without the grant the tile cannot act, so it must not claim a state it cannot leave.
        assertEquals(TileAppearance.UNGRANTED, TileAppearance.of(enabled = true, canWrite = false))
        assertEquals(TileAppearance.UNGRANTED, TileAppearance.of(enabled = false, canWrite = false))
    }

    @Test
    fun `every appearance maps to a distinct tile state`() {
        val states = TileAppearance.entries.map { it.tileState }
        assertEquals(states.size, states.distinct().size)
    }

    @Test
    fun `the setting key is the one Android 11 introduced for network adb`() {
        // adb_enabled is USB debugging; writing it here would toggle the wrong thing.
        assertEquals("adb_wifi_enabled", WirelessDebugging.SETTING)
    }
}
