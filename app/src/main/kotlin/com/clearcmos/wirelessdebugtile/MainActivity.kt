package com.clearcmos.wirelessdebugtile

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clearcmos.wirelessdebugtile.databinding.ActivityMainBinding

/**
 * Setup and status. Also the target of the tile's long-press, so a tile that reports itself
 * unavailable leads straight to the reason and the command that fixes it.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggle.setOnClickListener {
            val target = !WirelessDebugging.isEnabled(contentResolver)
            if (!WirelessDebugging.setEnabled(contentResolver, target)) {
                Toast.makeText(this, R.string.toast_ungranted, Toast.LENGTH_LONG).show()
            }
            render()
        }
        binding.copyCommand.setOnClickListener {
            val command = getString(R.string.grant_command)
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), command))
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
        }
        binding.addTile.setOnClickListener { requestAddTile() }
        binding.openDeveloperOptions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        binding.grantCommand.text = getString(R.string.grant_command)
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
        render()
    }

    private fun render() {
        val granted = WirelessDebugging.canWrite(this)
        val enabled = WirelessDebugging.isEnabled(contentResolver)

        binding.status.setText(if (enabled) R.string.status_on else R.string.status_off)
        binding.toggle.setText(if (enabled) R.string.action_turn_off else R.string.action_turn_on)
        binding.toggle.isEnabled = granted
        binding.permissionStatus.setText(if (granted) R.string.permission_granted else R.string.permission_missing)
        binding.grantGroup.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    /**
     * Android 13 added this so the tile can be added with one confirmation instead of a
     * trip through Quick Settings edit mode. The system throttles a component that keeps
     * being declined, so it stays behind an explicit button.
     */
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, WirelessDebuggingTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
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
