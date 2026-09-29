package com.clearcmos.tiles.wirelessdebug

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import com.clearcmos.tiles.MainActivity

/**
 * The one-tap surface. Turning wireless debugging off drops any adb connection, including
 * the one that granted this app its permission, so the tile is also the way back on.
 */
class WirelessDebuggingTileService : TileService() {
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = render()
        }

    override fun onStartListening() {
        super.onStartListening()
        contentResolver.registerContentObserver(WirelessDebugging.uri(), false, observer)
        render()
    }

    override fun onStopListening() {
        contentResolver.unregisterContentObserver(observer)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val target = !WirelessDebugging.isEnabled(contentResolver)
        if (!WirelessDebugging.setEnabled(contentResolver, target)) {
            openApp()
            return
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val appearance =
            TileAppearance.of(
                enabled = WirelessDebugging.isEnabled(contentResolver),
                canWrite = WirelessDebugging.canWrite(this),
            )
        tile.state = appearance.tileState
        tile.subtitle = getString(appearance.subtitle)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }
}
