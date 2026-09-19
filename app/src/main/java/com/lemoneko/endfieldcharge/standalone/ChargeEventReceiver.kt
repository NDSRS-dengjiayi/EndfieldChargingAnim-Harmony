package com.lemoneko.endfieldcharge.standalone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lemoneko.endfieldcharge.core.HudPlayMode
import com.lemoneko.endfieldcharge.core.settings.HudSettings
import com.lemoneko.endfieldcharge.settings.SettingsRepository

/**
 * Manifest-registered receiver for charger plug/unplug.
 *
 * `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` are protected system broadcasts and remain
 * deliverable to manifest receivers (and are exempt from the implicit-broadcast restriction). The
 * app must still be launched once after install and survive EMUI's background management: the user
 * has to allow auto-start and exempt the app from battery optimisation.
 */
class ChargeEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!StandalonePrefs.isEnabled(context)) return
        val settings = runCatching { SettingsRepository.current() }
            .getOrDefault(HudSettings.Default)

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (settings.wakeOnPlug) StandaloneScreen.wake(context)
                StandaloneLauncher.launch(context, HudPlayMode.CHARGE)
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                // Delivery during deep doze (screen off) is best effort and cannot be guaranteed
                // without system privileges.
                if (settings.playOnUnplug) {
                    StandaloneLauncher.launch(context, HudPlayMode.UNPLUG)
                }
            }
        }
    }
}
