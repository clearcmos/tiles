package com.clearcmos.tiles.calls

import android.service.quicksettings.Tile
import androidx.annotation.StringRes
import com.clearcmos.tiles.R

/** One headset as the workstation reported it. */
data class Headset(
    val mac: String,
    val callsOn: Boolean,
    val hfp: String,
    val name: String,
)

/**
 * Parses what `calls on|off|status` prints on the workstation, one line per headset:
 * `<mac> calls=on|off hfp=<state> <name>`. Anything else in the output (an error line, a
 * notice) is ignored, so the caller decides success from the exit status and this list.
 */
object CallsReply {
    private val LINE =
        Regex("""^([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}) calls=(on|off) hfp=(\S+) ?(.*)$""")

    fun parse(output: String): List<Headset> =
        output
            .lineSequence()
            .mapNotNull { line ->
                LINE.matchEntire(line.trim())?.destructured?.let { (mac, calls, hfp, name) ->
                    Headset(mac.uppercase(), calls == "on", hfp, name.ifBlank { mac })
                }
            }.toList()

    /** How old the last reply may be before the tile asks again when the shade opens. */
    private const val STALE_MS = 2 * 60_000L

    fun isStale(
        updatedAt: Long,
        now: Long,
    ): Boolean = now - updatedAt > STALE_MS

    /**
     * Splits a finished command into the raw reply to keep and the error to show. A reply that
     * parsed is kept even when the command failed, so the last known state survives; the error
     * is then the script's final line.
     */
    fun outcome(
        command: String,
        exitStatus: Int,
        output: String,
    ): Pair<String?, String?> {
        val headsets = parse(output)
        return when {
            exitStatus == 0 && headsets.isNotEmpty() -> output to null
            headsets.isNotEmpty() -> output to output.lines().last()
            else -> null to output.ifBlank { "$command exited $exitStatus" }
        }
    }

    /** A tap turns Calls on unless every headset already has it on, so a mixed state converges on on. */
    fun target(headsets: List<Headset>): Boolean = headsets.isEmpty() || !headsets.all { it.callsOn }
}

/** What the tile shows. Kept free of Android objects apart from constants, so it is unit testable. */
enum class CallsAppearance(
    val tileState: Int,
    @param:StringRes val subtitle: Int,
) {
    ON(Tile.STATE_ACTIVE, R.string.calls_subtitle_on),
    OFF(Tile.STATE_INACTIVE, R.string.calls_subtitle_off),
    MIXED(Tile.STATE_INACTIVE, R.string.calls_subtitle_mixed),

    /** Never synced. A tap still works: it turns Calls on. */
    UNKNOWN(Tile.STATE_INACTIVE, R.string.calls_subtitle_unknown),

    /** The last request failed. Inactive rather than unavailable, so a tap retries. */
    FAILED(Tile.STATE_INACTIVE, R.string.calls_subtitle_failed),

    /** No workstation configured. A tap opens the app. */
    UNCONFIGURED(Tile.STATE_INACTIVE, R.string.calls_subtitle_unconfigured),

    /** A request is in flight. Unavailable, which also stops SystemUI delivering a second tap. */
    WORKING(Tile.STATE_UNAVAILABLE, R.string.calls_subtitle_working),
    ;

    companion object {
        fun of(
            configured: Boolean,
            busy: Boolean,
            failed: Boolean,
            headsets: List<Headset>,
        ): CallsAppearance =
            when {
                !configured -> UNCONFIGURED
                busy -> WORKING
                failed -> FAILED
                headsets.isEmpty() -> UNKNOWN
                headsets.all { it.callsOn } -> ON
                headsets.none { it.callsOn } -> OFF
                else -> MIXED
            }
    }
}
