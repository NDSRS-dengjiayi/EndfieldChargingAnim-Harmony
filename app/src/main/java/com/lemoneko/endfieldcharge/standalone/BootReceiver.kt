package com.lemoneko.endfieldcharge.standalone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the monitor service after reboot when standalone mode is enabled.
 *
 * BOOT_COMPLETED is on the FGS background-start allowlist. Huawei also emits its own quick-boot
 * actions, which are registered in the manifest filter.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("EndfieldCharge/Boot", "boot action=${intent.action}")
        if (StandalonePrefs.isEnabled(context)) {
            StandaloneMonitorService.start(context.applicationContext)
        }
    }
}
