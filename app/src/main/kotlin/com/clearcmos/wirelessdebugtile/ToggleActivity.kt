package com.clearcmos.wirelessdebugtile

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * The home-screen surface: flip, say what happened, disappear. A launcher shortcut pointing
 * here can be dragged onto a home screen, which is a one-tap icon without the layout and
 * broadcast plumbing an AppWidgetProvider would need.
 */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = !WirelessDebugging.isEnabled(contentResolver)
        val message =
            when {
                !WirelessDebugging.setEnabled(contentResolver, target) -> R.string.toast_ungranted
                target -> R.string.toast_on
                else -> R.string.toast_off
            }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
