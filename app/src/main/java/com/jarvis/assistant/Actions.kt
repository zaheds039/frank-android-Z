package com.jarvis.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat

/** Every action checks the matching toggle in settings. Returns a sentence to speak if it fails, or null on success. */
class Actions(private val ctx: Context, private val p: Prefs) {

    fun run(name: String, a: String, b: String): String? = when (name) {
        "call" -> call(a)
        "sms" -> sms(a, b)
        "open_app" -> openApp(a)
        "web" -> if (!p.allowWeb) "Web search is turned off in my settings." else { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(a))), "Search $a"); null }
        "youtube" -> if (!p.allowWeb) "Web access is turned off in my settings." else { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(a))), "YouTube $a"); null }
        "timer" -> timer(a)
        "alarm" -> alarm(a)
        "flashlight" -> flash(a)
        "remember" -> { p.memory = (p.memory + "\n" + a).trim(); null }
        "forget_all" -> { p.memory = ""; null }
        "home", "back", "recents", "notifications", "quick_settings", "lock", "screenshot", "click", "scroll", "type" -> control(name, a)
        "volume" -> volume(a)
        "settings" -> openSettings(a)
        else -> null
    }

    private fun control(name: String, a: String): String? {
        if (!p.allowControl) return "Phone control is turned off in my settings."
        val s = JarvisAccessibilityService.instance ?: return "Please turn on my accessibility access in settings first."
        val api28 = Build.VERSION.SDK_INT >= 28
        val ok = when (name) {
            "home" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            "lock" -> api28 && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "screenshot" -> api28 && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            "click" -> s.clickText(a)
            "scroll" -> s.scroll(a.trim().lowercase() != "up")
            "type" -> s.typeText(a)
            else -> false
        }
        return if (ok) null else "I couldn't do that on the screen."
    }

    private fun volume(a: String): String? {
        if (!p.allowControl) return "Phone control is turned off in my settings."
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val m = AudioManager.STREAM_MUSIC
        when (a.trim().lowercase()) {
            "up" -> am.adjustStreamVolume(m, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> am.adjustStreamVolume(m, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.setStreamVolume(m, 0, AudioManager.FLAG_SHOW_UI)
            "max" -> am.setStreamVolume(m, am.getStreamMaxVolume(m), AudioManager.FLAG_SHOW_UI)
        }
        return null
    }

    private fun openSettings(a: String): String? {
        if (!p.allowControl) return "Phone control is turned off in my settings."
        val act = when (a.trim().lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        launch(Intent(act), "Open settings")
        return null
    }

    private fun granted(perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun lookup(name: String): String? {
        if (name.count { it.isDigit() } >= 5) return name.filter { it.isDigit() || it == '+' }
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null
        ) ?: return null
        return c.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun call(who: String): String? {
        if (!p.allowCalls) return "Calling is turned off in my settings."
        if (!granted(Manifest.permission.CALL_PHONE)) return "I need phone permission to make calls. You can grant it in my settings."
        val num = lookup(who) ?: return "I couldn't find $who in your contacts."
        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")), "Call $who")
        return null
    }

    private fun sms(who: String, text: String): String? {
        if (!p.allowSms) return "Messaging is turned off in my settings."
        val num = lookup(who) ?: return "I couldn't find $who in your contacts."
        launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", text), "Message $who")
        return null
    }

    private fun openApp(label: String): String? {
        if (!p.allowApps) return "Opening apps is turned off in my settings."
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ri = pm.queryIntentActivities(main, 0).firstOrNull { it.loadLabel(pm).toString().contains(label, true) }
            ?: return "I couldn't find an app called $label."
        val i = pm.getLaunchIntentForPackage(ri.activityInfo.packageName) ?: return "I can't open $label."
        launch(i, "Open $label")
        return null
    }

    private fun timer(sec: String): String? {
        if (!p.allowAlarms) return "Timers are turned off in my settings."
        val s = sec.filter { it.isDigit() }.toIntOrNull() ?: return "I didn't catch the timer length."
        launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Start timer")
        return null
    }

    private fun alarm(hhmm: String): String? {
        if (!p.allowAlarms) return "Alarms are turned off in my settings."
        val parts = hhmm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return "I didn't catch the alarm time."
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Set alarm")
        return null
    }

    private fun flash(state: String): String? {
        if (!p.allowFlash) return "The flashlight is turned off in my settings."
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList.first(), state.trim().lowercase() == "on"); null
        } catch (e: Exception) { "I couldn't control the flashlight." }
    }

    /**
     * Android blocks apps from opening screens from the background. If the user granted "Display over other apps",
     * we launch directly; otherwise we show a notification they can tap.
     */
    private fun launch(i: Intent, label: String) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(ctx)) { try { ctx.startActivity(i) } catch (_: Exception) {}; return }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("actions", "Jarvis actions", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, label.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(label.hashCode(), NotificationCompat.Builder(ctx, "actions")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Jarvis")
            .setContentText("Tap to: $label").setContentIntent(pi).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
}
