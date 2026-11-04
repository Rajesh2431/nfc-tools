package com.tapandgo.feature.automation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.tapandgo.R

/** UI description of one allowlisted action and its parameters. */
data class ActionSpec(val id: String, val title: Int, val icon: ImageVector, val params: List<ParamSpec>, val note: Int? = null)

data class ParamSpec(val key: String, val label: Int, val options: List<String>? = null, val hint: String? = null)

object ActionCatalog {
    private val STATE = listOf("on", "off", "toggle")
    private val ONOFF = listOf("on", "off")

    val all: List<ActionSpec> = listOf(
        ActionSpec("flashlight", R.string.act_flashlight, Icons.Filled.FlashlightOn, listOf(ParamSpec("state", R.string.p_state, STATE))),
        ActionSpec("volume.set", R.string.act_volume, Icons.Filled.VolumeUp, listOf(
            ParamSpec("stream", R.string.p_stream, listOf("media", "ring", "notification", "alarm", "system")),
            ParamSpec("percent", R.string.p_percent, hint = "0–100"),
        )),
        ActionSpec("ringer.mode", R.string.act_ringer, Icons.Filled.Vibration, listOf(ParamSpec("mode", R.string.p_mode, listOf("normal", "vibrate", "silent"))), R.string.note_dnd),
        ActionSpec("brightness.set", R.string.act_brightness, Icons.Filled.BrightnessMedium, listOf(ParamSpec("percent", R.string.p_percent, hint = "0–100")), R.string.note_write_settings),
        ActionSpec("brightness.auto", R.string.act_brightness_auto, Icons.Filled.BrightnessAuto, listOf(ParamSpec("state", R.string.p_state, ONOFF)), R.string.note_write_settings),
        ActionSpec("rotation.auto", R.string.act_rotation, Icons.Filled.ScreenLockRotation, listOf(ParamSpec("state", R.string.p_state, ONOFF)), R.string.note_write_settings),
        ActionSpec("screen.timeout", R.string.act_timeout, Icons.Filled.Timelapse, listOf(ParamSpec("seconds", R.string.p_seconds, hint = "1–1800")), R.string.note_write_settings),
        ActionSpec("wifi.set", R.string.act_wifi, Icons.Filled.Wifi, listOf(ParamSpec("state", R.string.p_state, STATE)), R.string.note_wifi),
        ActionSpec("bluetooth.set", R.string.act_bluetooth, Icons.Filled.Bluetooth, listOf(ParamSpec("state", R.string.p_state, STATE)), R.string.note_bluetooth),
        ActionSpec("alarm.set", R.string.act_alarm, Icons.Filled.Alarm, listOf(ParamSpec("time", R.string.p_time, hint = "07:30"), ParamSpec("label", R.string.p_label))),
        ActionSpec("timer.set", R.string.act_timer, Icons.Filled.Timer, listOf(ParamSpec("seconds", R.string.p_seconds, hint = "300"), ParamSpec("label", R.string.p_label))),
        ActionSpec("tts.speak", R.string.act_tts, Icons.Filled.RecordVoiceOver, listOf(ParamSpec("text", R.string.p_text, hint = "Battery at \${BATTERY_LEVEL}%"))),
        ActionSpec("notification.show", R.string.act_notification, Icons.Filled.Notifications, listOf(ParamSpec("title", R.string.p_title), ParamSpec("text", R.string.p_text))),
        ActionSpec("url.open", R.string.act_url, Icons.Filled.Link, listOf(ParamSpec("url", R.string.p_url, hint = "https://"))),
        ActionSpec("app.launch", R.string.act_app, Icons.Filled.Apps, listOf(ParamSpec("package", R.string.p_package, hint = "com.example.app"))),
        ActionSpec("settings.open", R.string.act_settings, Icons.Filled.Settings, listOf(
            ParamSpec("panel", R.string.p_panel, listOf("wifi", "internet", "bluetooth", "nfc", "volume", "sound", "display", "location", "hotspot", "write_settings", "dnd_access")),
        )),
        ActionSpec("share.text", R.string.act_share, Icons.Filled.Share, listOf(ParamSpec("text", R.string.p_text))),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
