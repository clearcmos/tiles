package com.clearcmos.tiles.calls

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.edit
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** What the app knows about the Calls switch, as of the last reply from the workstation. */
data class CallsState(
    val configured: Boolean,
    val busy: Boolean,
    val headsets: List<Headset>,
    val error: String?,
    val updatedAt: Long,
) {
    val appearance: CallsAppearance
        get() = CallsAppearance.of(configured, busy, error != null, headsets)
}

/**
 * The per-device "Calls" switch in Bluetooth settings is the HFP connection policy, which is
 * behind BLUETOOTH_PRIVILEGED: no installable app can hold it, the adb shell UID does. So the
 * work happens on the workstation, which reaches this phone over wireless adb: the tile asks
 * it over SSH with `calls on|off|status`, and it answers with one line per headset.
 *
 * Requests run one at a time on a single thread and are dropped while one is in flight. The
 * reply is persisted, so the tile shows the last known state without asking again every time
 * the shade opens.
 */
object Calls {
    private const val TAG = "TilesCalls"
    private const val PREFS = "calls"
    private const val KEY_HOST = "host"
    private const val KEY_USER = "user"
    private const val KEY_REPLY = "reply"
    private const val KEY_ERROR = "error"
    private const val KEY_UPDATED = "updated_at"
    private const val CONNECT_TIMEOUT_MS = 5_000

    // Covers the workstation reconnecting adb, which scans for a port when the pin is lost.
    private const val COMMAND_TIMEOUT_MS = 30_000

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile private var busy = false

    fun state(context: Context): CallsState {
        val prefs = prefs(context)
        return CallsState(
            configured = host(context).isNotBlank() && user(context).isNotBlank(),
            busy = busy,
            headsets = CallsReply.parse(prefs.getString(KEY_REPLY, "").orEmpty()),
            error = prefs.getString(KEY_ERROR, null),
            updatedAt = prefs.getLong(KEY_UPDATED, 0L),
        )
    }

    fun host(context: Context): String = prefs(context).getString(KEY_HOST, "").orEmpty()

    fun user(context: Context): String = prefs(context).getString(KEY_USER, "").orEmpty()

    fun configure(
        context: Context,
        host: String,
        user: String,
    ) {
        prefs(context).edit {
            putString(KEY_HOST, host.trim())
            putString(KEY_USER, user.trim())
            remove(KEY_ERROR)
        }
        changed(context)
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Flip every headset to the opposite of what the tile shows as a whole. */
    fun toggle(context: Context) {
        val on = CallsReply.target(state(context).headsets)
        request(context, if (on) "calls on" else "calls off")
    }

    fun refresh(context: Context) = request(context, "calls status")

    fun refreshIfStale(context: Context) {
        val state = state(context)
        if (state.configured && !state.busy && CallsReply.isStale(state.updatedAt, System.currentTimeMillis())) {
            refresh(context)
        }
    }

    private fun request(
        context: Context,
        command: String,
    ) {
        val app = context.applicationContext
        val host = host(app)
        val user = user(app)
        if (busy || host.isBlank() || user.isBlank()) return
        busy = true
        changed(app)
        executor.execute {
            val (reply, error) = runCommand(app, host, user, command)
            prefs(app).edit {
                if (reply != null) putString(KEY_REPLY, reply)
                if (error != null) putString(KEY_ERROR, error) else remove(KEY_ERROR)
                putLong(KEY_UPDATED, System.currentTimeMillis())
            }
            busy = false
            changed(app)
        }
    }

    /** Returns the raw reply when it parsed, and an error message when anything went wrong. */
    private fun runCommand(
        context: Context,
        host: String,
        user: String,
        command: String,
    ): Pair<String?, String?> =
        try {
            val result = SshClient(context).run(host, user, command, CONNECT_TIMEOUT_MS, COMMAND_TIMEOUT_MS)
            CallsReply.outcome(command, result.exitStatus, result.output)
        } catch (e: SshException) {
            null to e.message
        } catch (e: Exception) {
            Log.w(TAG, "$command failed", e)
            null to e.toString()
        }

    /** Tells whatever is showing the state, and asks SystemUI to rebind the tile if it is not listening. */
    private fun changed(context: Context) {
        main.post {
            listeners.forEach { it() }
            TileService.requestListeningState(context, ComponentName(context, CallsTileService::class.java))
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
