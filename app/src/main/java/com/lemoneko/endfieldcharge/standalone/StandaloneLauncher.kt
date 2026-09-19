package com.lemoneko.endfieldcharge.standalone

import android.content.Context
import android.content.Intent
import com.lemoneko.endfieldcharge.core.HudPlayMode

/**
 * Brings up the standalone HUD, trying a direct activity start first and falling back to a
 * full-screen-intent notification when the system forbids background activity starts.
 */
object StandaloneLauncher {

    fun launch(context: Context, mode: HudPlayMode) {
        val intent = Intent(context, StandaloneChargeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(StandaloneChargeActivity.EXTRA_MODE, mode.name)
        }
        val startedDirectly = runCatching { context.startActivity(intent) }.isSuccess
        if (startedDirectly) {
            StandaloneNotifier.cancel(context)
        } else {
            StandaloneNotifier.show(context, mode)
        }
    }
}
