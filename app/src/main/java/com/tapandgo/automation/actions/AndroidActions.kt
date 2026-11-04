package com.tapandgo.automation.actions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.tapandgo.R
import com.tapandgo.automation.engine.ActionHandler
import com.tapandgo.automation.engine.ActionRegistry
import com.tapandgo.automation.engine.ActionResult
import com.tapandgo.core.ndef.UriPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Allowlisted device actions implemented with public Android APIs only.
 * Restricted operations return [ActionResult.Unsupported] with the reason and
 * the legitimate alternative; nothing reports success it did not achieve.
 */
@Singleton
class AndroidActions @Inject constructor(@ApplicationContext private val context: Context) {

    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    @Volatile private var torchOn = false

    init {
        runCatching {
            context.getSystemService(CameraManager::class.java).registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
            }, null)
        }
    }

    val registry: ActionRegistry by lazy {
        ActionRegistry(
            mapOf(
                "flashlight" to ActionHandler(::flashlight),
                "volume.set" to ActionHandler(::volume),
                "ringer.mode" to ActionHandler(::ringer),
                "brightness.set" to ActionHandler(::brightness),
                "brightness.auto" to ActionHandler(::autoBrightness),
                "rotation.auto" to ActionHandler(::autoRotation),
                "screen.timeout" to ActionHandler(::screenTimeout),
                "wifi.set" to ActionHandler(::wifi),
                "bluetooth.set" to ActionHandler(::bluetooth),
                "alarm.set" to ActionHandler(::alarm),
                "timer.set" to ActionHandler(::timer),
                "tts.speak" to ActionHandler(::speak),
                "notification.show" to ActionHandler(::notify),
                "url.open" to ActionHandler(::openUrl),
                "app.launch" to ActionHandler(::launchApp),
                "settings.open" to ActionHandler(::openSettings),
                "share.text" to ActionHandler(::shareText),
            ),
        )
    }

    private fun start(intent: Intent): ActionResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Success()
    } catch (_: ActivityNotFoundException) {
        ActionResult.Failure("no app can handle this")
    } catch (e: SecurityException) {
        ActionResult.Failure("blocked by Android: ${e.javaClass.simpleName}")
    }

    private fun canWriteSettings() = Settings.System.canWrite(context)
    private val writeSettingsHint = "needs \"Modify system settings\" permission (Settings › Apps › Tap & Go)"

    private suspend fun flashlight(p: Map<String, String>): ActionResult {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return ActionResult.Unsupported("this device has no flash")
        val target = ActionParams.switchTarget(p["state"], torchOn) ?: return ActionResult.Failure("state must be on, off or toggle")
        return try {
            cm.setTorchMode(id, target)
            ActionResult.Success(if (target) "on" else "off")
        } catch (e: Exception) {
            ActionResult.Failure("camera in use or unavailable (${e.javaClass.simpleName})")
        }
    }

    private suspend fun volume(p: Map<String, String>): ActionResult {
        val stream = when (ActionParams.stream(p["stream"]) ?: return ActionResult.Failure("unknown stream")) {
            ActionParams.Stream.MEDIA -> AudioManager.STREAM_MUSIC
            ActionParams.Stream.RING -> AudioManager.STREAM_RING
            ActionParams.Stream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            ActionParams.Stream.ALARM -> AudioManager.STREAM_ALARM
            ActionParams.Stream.SYSTEM -> AudioManager.STREAM_SYSTEM
        }
        val pct = ActionParams.percent(p["percent"]) ?: return ActionResult.Failure("percent must be 0–100")
        return try {
            audio.setStreamVolume(stream, ActionParams.scale(pct, audio.getStreamMaxVolume(stream)), 0)
            ActionResult.Success("$pct%")
        } catch (_: SecurityException) {
            ActionResult.Unsupported("changing this stream during Do Not Disturb needs Do Not Disturb access")
        }
    }

    private suspend fun ringer(p: Map<String, String>): ActionResult {
        val mode = ActionParams.ringer(p["mode"]) ?: return ActionResult.Failure("mode must be normal, vibrate or silent")
        if (mode != ActionParams.Ringer.NORMAL && !notifications.isNotificationPolicyAccessGranted) {
            return ActionResult.Unsupported("needs Do Not Disturb access (Settings › Notifications › Do Not Disturb access)")
        }
        return try {
            audio.ringerMode = when (mode) {
                ActionParams.Ringer.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                ActionParams.Ringer.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                ActionParams.Ringer.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            ActionResult.Success(mode.name.lowercase())
        } catch (_: SecurityException) {
            ActionResult.Unsupported("needs Do Not Disturb access")
        }
    }

    private suspend fun brightness(p: Map<String, String>): ActionResult {
        if (!canWriteSettings()) return ActionResult.Unsupported(writeSettingsHint)
        val pct = ActionParams.percent(p["percent"]) ?: return ActionResult.Failure("percent must be 0–100")
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ActionParams.scale(pct, 255))
        return ActionResult.Success("$pct%")
    }

    private suspend fun autoBrightness(p: Map<String, String>): ActionResult {
        if (!canWriteSettings()) return ActionResult.Unsupported(writeSettingsHint)
        val on = ActionParams.onOff(p["state"]) ?: return ActionResult.Failure("state must be on or off")
        Settings.System.putInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        return ActionResult.Success(if (on) "on" else "off")
    }

    private suspend fun autoRotation(p: Map<String, String>): ActionResult {
        if (!canWriteSettings()) return ActionResult.Unsupported(writeSettingsHint)
        val on = ActionParams.onOff(p["state"]) ?: return ActionResult.Failure("state must be on or off")
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        return ActionResult.Success(if (on) "on" else "off")
    }

    private suspend fun screenTimeout(p: Map<String, String>): ActionResult {
        if (!canWriteSettings()) return ActionResult.Unsupported(writeSettingsHint)
        val s = ActionParams.seconds(p["seconds"], 30 * 60) ?: return ActionResult.Failure("seconds must be 1–1800")
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, s * 1000)
        return ActionResult.Success("${s}s")
    }

    @Suppress("DEPRECATION")
    private suspend fun wifi(p: Map<String, String>): ActionResult {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        val target = ActionParams.switchTarget(p["state"], wm.isWifiEnabled) ?: return ActionResult.Failure("state must be on, off or toggle")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ActionResult.Unsupported("Android 10+ does not let apps switch Wi-Fi; use settings.open with panel=wifi, or root")
        }
        return if (wm.setWifiEnabled(target)) ActionResult.Success(if (target) "on" else "off") else ActionResult.Failure("Wi-Fi state change refused")
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun bluetooth(p: Map<String, String>): ActionResult {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return ActionResult.Unsupported("no Bluetooth on this device")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActionResult.Unsupported("Android 13+ does not let apps switch Bluetooth; use settings.open with panel=bluetooth, or root")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.Unsupported("needs the Nearby devices permission")
        }
        val target = ActionParams.switchTarget(p["state"], adapter.isEnabled) ?: return ActionResult.Failure("state must be on, off or toggle")
        val ok = runCatching { if (target) adapter.enable() else adapter.disable() }.getOrDefault(false)
        return if (ok) ActionResult.Success(if (target) "on" else "off") else ActionResult.Failure("Bluetooth state change refused")
    }

    private suspend fun alarm(p: Map<String, String>): ActionResult {
        val (h, m) = ActionParams.hourMinute(p["time"]) ?: return ActionResult.Failure("time must be HH:MM")
        return start(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, p["label"].orEmpty().take(100)),
        )
    }

    private suspend fun timer(p: Map<String, String>): ActionResult {
        val s = ActionParams.seconds(p["seconds"], 24 * 3600) ?: return ActionResult.Failure("seconds must be 1–86400")
        return start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_MESSAGE, p["label"].orEmpty().take(100)))
    }

    private suspend fun speak(p: Map<String, String>): ActionResult {
        val text = p["text"]?.takeIf { it.isNotBlank() }?.take(TextToSpeech.getMaxSpeechInputLength()) ?: return ActionResult.Failure("text is empty")
        var tts: TextToSpeech? = null
        return try {
            val ready = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { c -> tts = TextToSpeech(context) { status -> if (c.isActive) c.resume(status == TextToSpeech.SUCCESS) } }
            } ?: false
            val engine = tts
            if (!ready || engine == null) return ActionResult.Unsupported("no text-to-speech engine available")
            engine.language = Locale.getDefault()
            val done = withTimeoutOrNull(60_000) {
                suspendCancellableCoroutine { c ->
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) { if (c.isActive) c.resume(true) }
                        @Deprecated("Deprecated in Java") override fun onError(id: String?) { if (c.isActive) c.resume(false) }
                    })
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                }
            } ?: false
            if (done) ActionResult.Success() else ActionResult.Failure("speech failed or timed out")
        } finally {
            tts?.shutdown()
        }
    }

    private suspend fun notify(p: Map<String, String>): ActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.Unsupported("notification permission not granted")
        }
        val title = p["title"]?.take(100).orEmpty().ifBlank { context.getString(R.string.app_name) }
        val text = p["text"]?.take(1000).orEmpty()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_automation), NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()
        notifications.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
        return ActionResult.Success()
    }

    private suspend fun openUrl(p: Map<String, String>): ActionResult {
        val url = p["url"].orEmpty()
        if (UriPolicy.validate(url) != null) return ActionResult.Failure("URL blocked by the URI policy")
        return start(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    private suspend fun launchApp(p: Map<String, String>): ActionResult {
        val pkg = p["package"].orEmpty()
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return ActionResult.Failure("app not installed or has no launcher screen")
        return start(intent)
    }

    private suspend fun openSettings(p: Map<String, String>): ActionResult {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val action = when (p["panel"]?.lowercase()) {
            "wifi" -> if (q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            "internet" -> if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
            "volume" -> if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
            "nfc" -> if (q) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "hotspot", "network" -> Settings.ACTION_WIRELESS_SETTINGS
            "write_settings" -> Settings.ACTION_MANAGE_WRITE_SETTINGS
            "dnd_access" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            else -> return ActionResult.Failure("unknown settings panel")
        }
        val intent = Intent(action)
        if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) intent.data = "package:${context.packageName}".toUri()
        return start(intent)
    }

    private suspend fun shareText(p: Map<String, String>): ActionResult {
        val text = p["text"]?.takeIf { it.isNotBlank() } ?: return ActionResult.Failure("text is empty")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.take(10_000))
        return start(Intent.createChooser(send, null))
    }

    companion object {
        const val CHANNEL = "automation"
    }
}
