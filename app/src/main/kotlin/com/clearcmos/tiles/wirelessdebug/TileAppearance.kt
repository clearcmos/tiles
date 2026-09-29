package com.clearcmos.tiles.wirelessdebug

import android.service.quicksettings.Tile
import androidx.annotation.StringRes
import com.clearcmos.tiles.R

/**
 * What the tile shows, derived from the only two facts that matter. Kept free of Android
 * objects so the mapping is testable off-device.
 */
enum class TileAppearance(
    val tileState: Int,
    @param:StringRes val subtitle: Int,
) {
    /** Granted and on. */
    ON(Tile.STATE_ACTIVE, R.string.subtitle_on),

    /** Granted and off. */
    OFF(Tile.STATE_INACTIVE, R.string.subtitle_off),

    /**
     * Ungranted. Greyed out rather than hidden, because a tile that silently does nothing
     * is worse than one that says why. Tapping it opens the app with the grant command.
     */
    UNGRANTED(Tile.STATE_UNAVAILABLE, R.string.subtitle_ungranted),
    ;

    companion object {
        fun of(
            enabled: Boolean,
            canWrite: Boolean,
        ): TileAppearance =
            when {
                !canWrite -> UNGRANTED
                enabled -> ON
                else -> OFF
            }
    }
}
