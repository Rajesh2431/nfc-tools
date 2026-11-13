package com.tapandgo.automation

import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.Condition
import com.tapandgo.automation.engine.Workflow

/**
 * Ready-made workflows the user adds as editable copies. Templates that use a
 * smart-home system name the connection they expect ([connection]); the user
 * either creates a connection with that name or edits the blocks.
 */
data class WorkflowTemplate(
    val id: String,
    val description: String,
    val workflow: Workflow,
    val connection: String? = null,
    val category: Category,
) {
    enum class Category { HOME, ROUTINE, TRAVEL, DEVICE, SMART_HOME }
}

object WorkflowTemplates {
    private fun a(id: String, vararg p: Pair<String, String>) = Block.Action(id, mapOf(*p))

    val all: List<WorkflowTemplate> = listOf(
        WorkflowTemplate(
            "arrive_home", "Lights on, normal ringer, media volume 40%, welcome notification.",
            Workflow("Arrive home", listOf(
                a("ha.service", "connection" to "Home", "service" to "light.turn_on", "entity" to "light.hallway"),
                a("ringer.mode", "mode" to "normal"),
                a("volume.set", "stream" to "media", "percent" to "40"),
                a("notification.show", "title" to "Welcome home", "text" to "Arrived at \${TIME}"),
            )),
            connection = "Home", category = WorkflowTemplate.Category.SMART_HOME,
        ),
        WorkflowTemplate(
            "leave_home", "Everything off through Home Assistant and a reminder if the battery is low.",
            Workflow("Leave home", listOf(
                a("ha.service", "connection" to "Home", "service" to "light.turn_off", "entity" to "all"),
                Block.If(
                    Condition.Compare("\${BATTERY_LEVEL}", Condition.Op.LESS, "30"),
                    then = listOf(a("notification.show", "title" to "Charge your phone", "text" to "Battery at \${BATTERY_LEVEL}%")),
                ),
            )),
            connection = "Home", category = WorkflowTemplate.Category.SMART_HOME,
        ),
        WorkflowTemplate(
            "bedtime", "Vibrate, dim screen, 07:00 alarm, Hue goodnight scene off, spoken good night.",
            Workflow("Bedtime", listOf(
                a("ringer.mode", "mode" to "vibrate"),
                a("brightness.set", "percent" to "10"),
                a("alarm.set", "time" to "07:00", "label" to "Wake up"),
                a("hue.light", "connection" to "Hue", "light" to "1", "state" to "off"),
                a("tts.speak", "text" to "Good night"),
            )),
            connection = "Hue", category = WorkflowTemplate.Category.ROUTINE,
        ),
        WorkflowTemplate(
            "car_mode", "Media volume up, open Maps, spoken reminder. Put the tag on your car mount.",
            Workflow("Car mode", listOf(
                a("volume.set", "stream" to "media", "percent" to "80"),
                a("app.launch", "package" to "com.google.android.apps.maps"),
                a("tts.speak", "text" to "Drive safe"),
            )),
            category = WorkflowTemplate.Category.TRAVEL,
        ),
        WorkflowTemplate(
            "meeting", "Silent mode for one hour with a timer and a notification.",
            Workflow("Meeting", listOf(
                a("ringer.mode", "mode" to "silent"),
                a("timer.set", "seconds" to "3600", "label" to "Meeting ends"),
                a("notification.show", "title" to "Meeting mode", "text" to "Silent until \${TIME} + 1 h"),
            )),
            category = WorkflowTemplate.Category.ROUTINE,
        ),
        WorkflowTemplate(
            "gym", "Workout timer, media volume 70%, open your music app.",
            Workflow("Gym", listOf(
                a("volume.set", "stream" to "media", "percent" to "70"),
                a("timer.set", "seconds" to "2700", "label" to "Workout"),
                a("app.launch", "package" to "com.spotify.music"),
            )),
            category = WorkflowTemplate.Category.ROUTINE,
        ),
        WorkflowTemplate(
            "battery_saver", "If the battery is under 20%: dim the screen and warn; otherwise report the level.",
            Workflow("Battery check", listOf(
                Block.If(
                    Condition.Compare("\${BATTERY_LEVEL}", Condition.Op.LESS, "20"),
                    then = listOf(a("brightness.set", "percent" to "20"), a("notification.show", "title" to "Low battery", "text" to "\${BATTERY_LEVEL}% left")),
                    otherwise = listOf(a("notification.show", "title" to "Battery OK", "text" to "\${BATTERY_LEVEL}% · \${BATTERY_STATUS}")),
                ),
            )),
            category = WorkflowTemplate.Category.DEVICE,
        ),
        WorkflowTemplate(
            "garage_door", "Publish to your garage-door MQTT topic (Zigbee2MQTT, ESPHome, Tasmota).",
            Workflow("Garage door", listOf(
                a("mqtt.publish", "connection" to "Broker", "topic" to "home/garage/door/set", "payload" to "TOGGLE", "qos" to "1", "retain" to "off"),
                a("notification.show", "title" to "Garage", "text" to "Command sent at \${TIME}"),
            )),
            connection = "Broker", category = WorkflowTemplate.Category.SMART_HOME,
        ),
        WorkflowTemplate(
            "desk_lamp", "Toggle a Shelly, Tasmota or WLED device named “Desk”.",
            Workflow("Desk lamp", listOf(a("device.switch", "connection" to "Desk", "state" to "toggle"))),
            connection = "Desk", category = WorkflowTemplate.Category.SMART_HOME,
        ),
        WorkflowTemplate(
            "temperature_alert", "Read a Home Assistant temperature sensor and warn above 28 °C.",
            Workflow("Temperature alert", listOf(
                Block.Action("ha.state", mapOf("connection" to "Home", "entity" to "sensor.living_room_temperature"), outputVariable = "TEMP"),
                Block.If(
                    Condition.Compare("\${TEMP}", Condition.Op.GREATER, "28"),
                    then = listOf(a("ha.service", "connection" to "Home", "service" to "fan.turn_on", "entity" to "fan.living_room"), a("notification.show", "title" to "Hot", "text" to "\${TEMP} °C — fan on")),
                ),
            )),
            connection = "Home", category = WorkflowTemplate.Category.SMART_HOME,
        ),
    )
}
