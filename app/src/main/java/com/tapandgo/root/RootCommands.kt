package com.tapandgo.root

import android.os.Build

/**
 * Allowlisted root operations. Each builds its shell script only from
 * constants and validated numbers/enums, so no user text ever reaches the
 * shell through these commands (the free-form console is separate, typed by
 * the user and confirmed every time).
 */
sealed class RootCommand(val category: Category, val destructive: Boolean = false, val minSdk: Int = 26) {
    enum class Category { CONNECTIVITY, SYSTEM, DISPLAY, POWER, DIAGNOSTICS }

    abstract fun script(): String

    data class Airplane(val on: Boolean) : RootCommand(Category.CONNECTIVITY, minSdk = 30) {
        override fun script() = "cmd connectivity airplane-mode ${if (on) "enable" else "disable"}"
    }
    data class MobileData(val on: Boolean) : RootCommand(Category.CONNECTIVITY) {
        override fun script() = "svc data ${if (on) "enable" else "disable"}"
    }
    data class Wifi(val on: Boolean) : RootCommand(Category.CONNECTIVITY) {
        override fun script() = "svc wifi ${if (on) "enable" else "disable"}"
    }
    data class Bluetooth(val on: Boolean) : RootCommand(Category.CONNECTIVITY) {
        override fun script() = "svc bluetooth ${if (on) "enable" else "disable"}"
    }
    /** Airplane mode on, short pause, off: re-establishes all radios. */
    data object NetworkReset : RootCommand(Category.CONNECTIVITY, minSdk = 30) {
        override fun script() = "cmd connectivity airplane-mode enable && sleep 3 && cmd connectivity airplane-mode disable"
    }

    enum class RebootTarget(val arg: String) { SYSTEM(""), RECOVERY("recovery"), BOOTLOADER("bootloader") }
    data class Reboot(val target: RebootTarget) : RootCommand(Category.POWER, destructive = true) {
        override fun script() = if (target == RebootTarget.SYSTEM) "svc power reboot" else "svc power reboot ${target.arg}"
    }
    data object PowerOff : RootCommand(Category.POWER, destructive = true) {
        override fun script() = "svc power shutdown"
    }
    data object RestartSystemUi : RootCommand(Category.SYSTEM, destructive = true) {
        override fun script() = "pkill -f com.android.systemui"
    }

    /** dpi in 120..640, or null to reset. */
    data class Density(val dpi: Int?) : RootCommand(Category.DISPLAY) {
        init { require(dpi == null || dpi in 120..640) }
        override fun script() = "wm density ${dpi ?: "reset"}"
    }
    /** width/height in 320..4096, or null to reset. */
    data class Resolution(val size: Pair<Int, Int>?) : RootCommand(Category.DISPLAY) {
        init { require(size == null || (size.first in 320..4096 && size.second in 320..4096)) }
        override fun script() = "wm size ${size?.let { "${it.first}x${it.second}" } ?: "reset"}"
    }
    data class AnimationScale(val scale: Scale) : RootCommand(Category.DISPLAY) {
        enum class Scale(val v: String) { OFF("0"), HALF("0.5"), NORMAL("1"), SLOW("1.5"), SLOWER("2") }
        override fun script() = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .joinToString(" && ") { "settings put global $it ${scale.v}" }
    }
    data class Brightness(val level: Int) : RootCommand(Category.DISPLAY) {
        init { require(level in 0..255) }
        override fun script() = "settings put system screen_brightness_mode 0 && settings put system screen_brightness $level"
    }
    data class BatterySaver(val on: Boolean) : RootCommand(Category.SYSTEM) {
        override fun script() = "settings put global low_power ${if (on) 1 else 0}"
    }
    data object ForceDeepSleep : RootCommand(Category.SYSTEM) {
        override fun script() = "dumpsys deviceidle force-idle"
    }
    data object ExitDeepSleep : RootCommand(Category.SYSTEM) {
        override fun script() = "dumpsys deviceidle unforce"
    }
    data class DeveloperOptions(val on: Boolean) : RootCommand(Category.SYSTEM) {
        override fun script() = "settings put global development_settings_enabled ${if (on) 1 else 0}"
    }
    data class FixedPerformance(val on: Boolean) : RootCommand(Category.SYSTEM, minSdk = 31) {
        override fun script() = "cmd power set-fixed-performance-mode-enabled $on"
    }

    // Read-only diagnostics that need root on most devices.
    data object Processes : RootCommand(Category.DIAGNOSTICS) { override fun script() = "ps -A -o PID,USER,RSS,NAME" }
    data object RecentLogs : RootCommand(Category.DIAGNOSTICS) { override fun script() = "logcat -d -t 300" }
    data object Thermal : RootCommand(Category.DIAGNOSTICS) {
        override fun script() = "for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat \$z/type 2>/dev/null): $(cat \$z/temp 2>/dev/null)\"; done"
    }
    data object SelinuxStatus : RootCommand(Category.DIAGNOSTICS) { override fun script() = "getenforce" }
    data object Gpu : RootCommand(Category.DIAGNOSTICS) { override fun script() = "dumpsys SurfaceFlinger | grep -i -m 3 'GLES'" }

    fun supportedOnThisSdk(sdk: Int = Build.VERSION.SDK_INT) = sdk >= minSdk

    /** Short stable name for history/logs (no parameters that could be personal). */
    val label: String get() = this::class.simpleName ?: "command"
}

/** Presets. Each step is checked (SDK) before running and reported individually. */
enum class RootProfile(val steps: List<RootCommand>) {
    GAMING(listOf(RootCommand.AnimationScale(RootCommand.AnimationScale.Scale.HALF), RootCommand.BatterySaver(false), RootCommand.FixedPerformance(true))),
    BATTERY_SAVER(listOf(RootCommand.BatterySaver(true), RootCommand.AnimationScale(RootCommand.AnimationScale.Scale.OFF), RootCommand.FixedPerformance(false))),
    DEEP_SLEEP(listOf(RootCommand.BatterySaver(true), RootCommand.ForceDeepSleep)),
    PRIVACY(listOf(RootCommand.Wifi(false), RootCommand.Bluetooth(false), RootCommand.MobileData(false))),
    DEVELOPER(listOf(RootCommand.DeveloperOptions(true), RootCommand.AnimationScale(RootCommand.AnimationScale.Scale.HALF))),
    NETWORK_RESET(listOf(RootCommand.NetworkReset)),
    PERFORMANCE(listOf(RootCommand.ExitDeepSleep, RootCommand.BatterySaver(false), RootCommand.AnimationScale(RootCommand.AnimationScale.Scale.HALF), RootCommand.FixedPerformance(true))),
}

/** Free-form console input checks: bounded, single script, no NUL. Content is the user's own and confirmed. */
object CustomCommandPolicy {
    const val MAX_LENGTH = 2000
    fun validate(script: String): Boolean = script.isNotBlank() && script.length <= MAX_LENGTH && '\u0000' !in script
}
