package com.clearcmos.tiles.calls

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import com.clearcmos.tiles.MainActivity

/**
 * Turns the Bluetooth "Calls" switch on or off on both headsets at once. The work runs on the
 * workstation over SSH (see [Calls]), so the tile shows the last reply and a working state
 * while a request is in flight.
 */
class CallsTileService : TileService() {
    private val listener = { render() }

    override fun onStartListening() {
        super.onStartListening()
        Calls.addListener(listener)
        render()
        // Picks up a change made in Settings since the last reply, at most every couple of minutes.
        Calls.refreshIfStale(this)
    }

    override fun onStopListening() {
        Calls.removeListener(listener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!Calls.state(this).configured) {
            openApp()
            return
        }
        Calls.toggle(this)
    }

    private fun render() {
        val tile = qsTile ?: return
        val appearance = Calls.state(this).appearance
        tile.state = appearance.tileState
        tile.subtitle = getString(appearance.subtitle)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }
}
