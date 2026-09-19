package com.lemoneko.endfieldcharge.standalone

import android.content.Context

/**
 * App-local switch for the no-root standalone mode.
 *
 * Deliberately separate from [com.lemoneko.endfieldcharge.core.settings.HudSettings.enabled]: that
 * flag means "replace the ROM animation via the Xposed hook", whereas this one means "play the HUD
 * from an ordinary app process". The animation parameters themselves are still read from the shared
 * settings store.
 */
object StandalonePrefs {

    private const val FILE = "standalone"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
