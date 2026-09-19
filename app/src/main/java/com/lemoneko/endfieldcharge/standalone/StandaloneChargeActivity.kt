package com.lemoneko.endfieldcharge.standalone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.lemoneko.endfieldcharge.core.BatterySnapshot
import com.lemoneko.endfieldcharge.core.HudMetrics
import com.lemoneko.endfieldcharge.core.HudPlayMode
import com.lemoneko.endfieldcharge.core.settings.HudSettings
import com.lemoneko.endfieldcharge.core.timeline.HudTimeline
import com.lemoneko.endfieldcharge.settings.SettingsRepository
import com.lemoneko.endfieldcharge.ui.EndfieldHudView

/**
 * No-root host for the charging HUD.
 *
 * A translucent, show-when-locked activity that plays the exact same [EndfieldHudView] /
 * [HudTimeline] as the SystemUI hook, but in an ordinary app process. It shows directly over the
 * keyguard without unlocking the device, then finishes itself when the timeline ends (tap also
 * dismisses). It cannot suppress the ROM's own charging animation - that requires hooking
 * SystemUI - so the OEM popup may still appear alongside.
 */
class StandaloneChargeActivity : Activity() {

    private var animator: ValueAnimator? = null
    private lateinit var hud: EndfieldHudView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        // NB: hideSystemBars() needs the DecorView, which only exists after setContentView().
        // Calling window.insetsController before that crashes with an internal NPE.

        val mode = runCatching {
            HudPlayMode.valueOf(intent?.getStringExtra(EXTRA_MODE) ?: HudPlayMode.CHARGE.name)
        }.getOrDefault(HudPlayMode.CHARGE)
        val settings = runCatching { SettingsRepository.current() }
            .getOrDefault(HudSettings.Default)

        val root = FrameLayout(this).apply {
            // Transparent by theme; a very slight scrim keeps the pill legible over a bright
            // lockscreen wallpaper without pretending to be a system surface.
            setBackgroundColor(Color.argb(40, 0, 0, 0))
            setOnClickListener { finish() }
        }

        hud = EndfieldHudView(this).apply {
            widthRatio = settings.widthRatio
            language = settings.language
            setSnapshot(currentSnapshot())
        }
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = HudMetrics.topMarginPx(this@StandaloneChargeActivity, settings.topGapDp) },
        )
        setContentView(root)
        hideSystemBars()
        StandaloneNotifier.cancel(this)

        val timeline = HudTimeline(settings.animationOptions())
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = timeline.durationMillis.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val cue = (animation.animatedValue as Float).toDouble()
                hud.render(
                    when (mode) {
                        HudPlayMode.CHARGE -> timeline.evaluateCharge(cue)
                        HudPlayMode.UNPLUG -> timeline.evaluateUnplug(cue)
                    },
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
        }.also { it.start() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Reads the sticky [Intent.ACTION_BATTERY_CHANGED] intent (level, plug state, status). Energy
     * figures live in sysfs and are unreadable for an unprivileged app, so they stay -1 and the
     * HUD renders "--" instead.
     */
    private fun currentSnapshot(): BatterySnapshot {
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) return BatterySnapshot.from(sticky)

        val battery = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return BatterySnapshot(
            level = runCatching {
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }.getOrDefault(-1),
            plugged = 0,
            status = 0,
            voltageUv = -1L,
            chargeFullUah = -1L,
            chargeCounterRaw = -1L,
        )
    }

    companion object {
        /** A [HudPlayMode] name: CHARGE (plug in) or UNPLUG. */
        const val EXTRA_MODE = "mode"
    }
}
