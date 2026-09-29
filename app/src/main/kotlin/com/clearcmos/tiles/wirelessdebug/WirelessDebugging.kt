package com.clearcmos.tiles.wirelessdebug

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * The Wireless debugging toggle.
 *
 * Android 11 moved network adb behind [SETTING]; the older `adb_enabled` global is USB
 * debugging only and is untouched here. Writing a global setting needs
 * WRITE_SECURE_SETTINGS, which the platform declares with the `development` protection
 * flag, so it can be granted to an ordinary app once over adb.
 */
object WirelessDebugging {
    const val SETTING: String = "adb_wifi_enabled"

    fun isEnabled(resolver: ContentResolver): Boolean = Settings.Global.getInt(resolver, SETTING, 0) == 1

    /** Returns false when the write was rejected, which in practice means the grant is missing. */
    fun setEnabled(
        resolver: ContentResolver,
        enabled: Boolean,
    ): Boolean =
        try {
            Settings.Global.putInt(resolver, SETTING, if (enabled) 1 else 0)
        } catch (_: SecurityException) {
            false
        }

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Watched so the tile follows changes made from the Settings app or from a host over adb. */
    fun uri(): Uri = Settings.Global.getUriFor(SETTING)
}
