package com.lemoneko.endfieldcharge.standalone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

/**
 * Restarts the monitor service after reboot when standalone mode is enabled.
 *
 * Only responds to post-unlock boot actions: [StandalonePrefs] lives in credential-encrypted
 * storage, which is unreadable during direct boot (LOCKED_BOOT_COMPLETED before first unlock).
 * BOOT_COMPLETED is sent after the user unlocks the device and is on the FGS background-start
 * allowlist. Huawei also emits its own quick-boot action.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "boot action=$action")
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        // Huawei QUICKBOOT_POWERON may arrive before unlock; CE-shared prefs are unreadable then.
        // BOOT_COMPLETED is only sent after the first unlock, so it will still start the service.
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) {
            Log.i(TAG, "user locked, deferring to BOOT_COMPLETED")
            return
        }
        if (StandalonePrefs.isEnabled(context)) {
            Log.i(TAG, "standalone enabled -> starting monitor service")
            StandaloneMonitorService.start(context.applicationContext)
        }
    }

    private companion object {
        const val TAG = "EndfieldCharge/Boot"
    }
}
