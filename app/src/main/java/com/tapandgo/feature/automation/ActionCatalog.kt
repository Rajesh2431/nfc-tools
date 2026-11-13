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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.SignalCellularAlt
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
        ActionSpec("phone.dial", R.string.act_dial, Icons.Filled.Call, listOf(ParamSpec("number", R.string.p_number, hint = "+1 555 0100")), R.string.note_dial),
        ActionSpec("sms.compose", R.string.act_sms, Icons.Filled.Sms, listOf(ParamSpec("number", R.string.p_number), ParamSpec("text", R.string.p_text)), R.string.note_sms),
        ActionSpec("email.compose", R.string.act_email, Icons.Filled.Email, listOf(ParamSpec("to", R.string.p_to), ParamSpec("subject", R.string.p_title), ParamSpec("text", R.string.p_text))),
        ActionSpec("map.open", R.string.act_map, Icons.Filled.Map, listOf(ParamSpec("lat", R.string.p_lat), ParamSpec("lng", R.string.p_lng), ParamSpec("query", R.string.p_query))),
        ActionSpec("app.info", R.string.act_app_info, Icons.Filled.Info, listOf(ParamSpec("package", R.string.p_package, hint = "com.example.app"))),
        ActionSpec("calendar.event", R.string.act_calendar, Icons.Filled.Event, listOf(ParamSpec("title", R.string.p_title), ParamSpec("in_minutes", R.string.p_in_minutes, hint = "60"))),
        ActionSpec("http.request", R.string.act_http, Icons.Filled.Http, listOf(
            ParamSpec("url", R.string.p_url, hint = "https://"),
            ParamSpec("method", R.string.p_method, listOf("POST", "GET")),
            ParamSpec("content_type", R.string.p_content_type, listOf("json", "text")),
            ParamSpec("body", R.string.p_body, hint = "{\"battery\": \"\${BATTERY_LEVEL}\"}"),
        ), R.string.note_http),
        ActionSpec("ha.service", R.string.act_ha_service, Icons.Filled.Home, listOf(
            ParamSpec("connection", R.string.p_connection),
            ParamSpec("service", R.string.p_service, hint = "light.turn_on"),
            ParamSpec("entity", R.string.p_entity, hint = "light.living_room"),
            ParamSpec("data", R.string.p_data, hint = "{\"brightness_pct\": 60}"),
        ), R.string.note_smarthome),
        ActionSpec("ha.state", R.string.act_ha_state, Icons.Filled.Sensors, listOf(
            ParamSpec("connection", R.string.p_connection),
            ParamSpec("entity", R.string.p_entity, hint = "sensor.outdoor_temperature"),
        ), R.string.note_ha_state),
        ActionSpec("mqtt.publish", R.string.act_mqtt, Icons.Filled.Hub, listOf(
            ParamSpec("connection", R.string.p_connection),
            ParamSpec("topic", R.string.p_topic, hint = "home/garage/door"),
            ParamSpec("payload", R.string.p_payload, hint = "open"),
            ParamSpec("qos", R.string.p_qos, listOf("0", "1")),
            ParamSpec("retain", R.string.p_retain, listOf("off", "on")),
        ), R.string.note_smarthome),
        ActionSpec("hue.light", R.string.act_hue_light, Icons.Filled.Lightbulb, listOf(
            ParamSpec("connection", R.string.p_connection),
            ParamSpec("light", R.string.p_light, hint = "1"),
            ParamSpec("state", R.string.p_state, listOf("on", "off", "keep")),
            ParamSpec("brightness", R.string.p_percent, hint = "0–100"),
        ), R.string.note_smarthome),
        ActionSpec("hue.scene", R.string.act_hue_scene, Icons.Filled.Palette, listOf(
            ParamSpec("connection", R.string.p_connection),
            ParamSpec("scene", R.string.p_scene),
            ParamSpec("group", R.string.p_group, hint = "0"),
        ), R.string.note_smarthome),
        ActionSpec("device.switch", R.string.act_device, Icons.Filled.Power, listOf(
            ParamSpec("connection", R.string.p_connection),
            ParamSpec("state", R.string.p_state, listOf("on", "off", "toggle")),
            ParamSpec("channel", R.string.p_channel, hint = "0"),
            ParamSpec("brightness", R.string.p_percent, hint = "WLED / Tasmota dimmer"),
        ), R.string.note_smarthome),
        ActionSpec("root.airplane", R.string.act_root_airplane, Icons.Filled.AirplanemodeActive, listOf(ParamSpec("state", R.string.p_state, ONOFF)), R.string.note_root),
        ActionSpec("root.mobile_data", R.string.act_root_data, Icons.Filled.SignalCellularAlt, listOf(ParamSpec("state", R.string.p_state, ONOFF)), R.string.note_root),
        ActionSpec("root.wifi", R.string.act_root_wifi, Icons.Filled.Wifi, listOf(ParamSpec("state", R.string.p_state, ONOFF)), R.string.note_root),
        ActionSpec("root.bluetooth", R.string.act_root_bt, Icons.Filled.Bluetooth, listOf(ParamSpec("state", R.string.p_state, ONOFF)), R.string.note_root),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
