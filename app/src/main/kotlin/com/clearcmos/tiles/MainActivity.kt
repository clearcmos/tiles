package com.clearcmos.tiles

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clearcmos.tiles.calls.Calls
import com.clearcmos.tiles.calls.CallsTileService
import com.clearcmos.tiles.calls.SshClient
import com.clearcmos.tiles.databinding.ActivityMainBinding
import com.clearcmos.tiles.wirelessdebug.WirelessDebugging
import com.clearcmos.tiles.wirelessdebug.WirelessDebuggingTileService
import java.util.concurrent.Executors

/**
 * Setup and status for every tile. Also the target of each tile's long-press, so a tile that
 * reports itself unavailable leads straight to the reason and the fix.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val callsListener = { renderCalls() }
    private val background = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggle.setOnClickListener {
            val target = !WirelessDebugging.isEnabled(contentResolver)
            if (!WirelessDebugging.setEnabled(contentResolver, target)) {
                Toast.makeText(this, R.string.toast_ungranted, Toast.LENGTH_LONG).show()
            }
            renderWirelessDebug()
        }
        binding.copyCommand.setOnClickListener { copy(getString(R.string.grant_command)) }
        binding.addTile.setOnClickListener {
            requestAddTile(WirelessDebuggingTileService::class.java, R.string.tile_label, R.drawable.ic_tile)
        }
        binding.openDeveloperOptions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        binding.grantCommand.text = getString(R.string.grant_command)

        binding.callsHost.setText(Calls.host(this))
        binding.callsUser.setText(Calls.user(this))
        binding.callsSave.setOnClickListener {
            Calls.configure(this, binding.callsHost.text.toString(), binding.callsUser.text.toString())
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        }
        binding.callsToggle.setOnClickListener { Calls.toggle(this) }
        binding.callsRefresh.setOnClickListener { Calls.refresh(this) }
        binding.callsCopyKey.setOnClickListener { copy(binding.callsKey.text.toString()) }
        binding.callsAddTile.setOnClickListener {
            requestAddTile(CallsTileService::class.java, R.string.calls_tile_label, R.drawable.ic_tile_calls)
        }
        // Key generation takes a moment the first time; keep it off the main thread.
        background.execute {
            val key = SshClient(this).ensureKey()
            runOnUiThread { binding.callsKey.text = key }
        }
        applyWindowInsets()
    }

    /** targetSdk 35 and up draws edge to edge, so the content has to inset itself. */
    private fun applyWindowInsets() {
        val content = binding.content
        val base = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(base + bars.left, base + bars.top, base + bars.right, base + bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        Calls.addListener(callsListener)
        renderWirelessDebug()
        renderCalls()
    }

    override fun onPause() {
        Calls.removeListener(callsListener)
        super.onPause()
    }

    override fun onDestroy() {
        background.shutdown()
        super.onDestroy()
    }

    private fun renderWirelessDebug() {
        val granted = WirelessDebugging.canWrite(this)
        val enabled = WirelessDebugging.isEnabled(contentResolver)

        binding.status.setText(if (enabled) R.string.status_on else R.string.status_off)
        binding.toggle.setText(if (enabled) R.string.action_turn_off else R.string.action_turn_on)
        binding.toggle.isEnabled = granted
        binding.permissionStatus.setText(if (granted) R.string.permission_granted else R.string.permission_missing)
        binding.grantGroup.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun renderCalls() {
        val state = Calls.state(this)
        binding.callsStatus.setText(state.appearance.subtitle)
        binding.callsToggle.isEnabled = state.configured && !state.busy
        binding.callsRefresh.isEnabled = state.configured && !state.busy
        val lines =
            state.headsets.map { h ->
                getString(if (h.callsOn) R.string.calls_headset_on else R.string.calls_headset_off, h.name, h.hfp)
            }
        val updated =
            if (state.updatedAt == 0L) {
                getString(R.string.calls_never_synced)
            } else {
                getString(R.string.calls_updated, DateUtils.getRelativeTimeSpanString(state.updatedAt))
            }
        binding.callsDetail.text =
            getString(R.string.calls_detail, (lines + updated + listOfNotNull(state.error)).joinToString("\n"))
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Android 13 added this so a tile can be added with one confirmation instead of a trip
     * through Quick Settings edit mode. The system throttles a component that keeps being
     * declined, so it stays behind an explicit button.
     */
    private fun requestAddTile(
        service: Class<*>,
        @StringRes label: Int,
        @DrawableRes icon: Int,
    ) {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, service),
            getString(label),
            Icon.createWithResource(this, icon),
            mainExecutor,
        ) { result ->
            val message =
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.toast_tile_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.toast_tile_already_added
                    else -> R.string.toast_tile_not_added
                }
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }
    }
}
