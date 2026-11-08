package com.jarvis.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** All settings live here, encrypted on the device. Nothing is sent anywhere except your messages to the Claude API. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx, "jarvis_prefs",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private inner class Str(val k: String, val d: String) : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(k, d) ?: d
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(k, value).apply() }
    }
    private inner class Bool(val k: String, val d: Boolean) : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp.edit().putBoolean(k, value).apply() }
    }
    private inner class Flt(val k: String, val d: Float) : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float = sp.getFloat(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { sp.edit().putFloat(k, value).apply() }
    }

    var apiKey by Str("api_key", "")
    var model by Str("model", "claude-sonnet-5-5")
    var wakeWord by Str("wake", "jarvis")
    var language by Str("lang", "en-IN")
    var persona by Str("persona", "a warm, witty, human-sounding personal voice assistant")
    var voiceStyle by Str("voice_style", "auto")
    var voiceName by Str("voice_name", "")
    var assistantName by Str("name", "Jarvis")
    var agentsJson by Str("agents", "")
    var activeAgent by Str("active_agent", "")
    var allowControl by Bool("a_control", true)
    var memory by Str("memory", "")
    var rate by Flt("rate", 1.0f)
    var pitch by Flt("pitch", 1.0f)
    var allowCalls by Bool("a_calls", true)
    var allowSms by Bool("a_sms", true)
    var allowApps by Bool("a_apps", true)
    var allowWeb by Bool("a_web", true)
    var allowAlarms by Bool("a_alarms", true)
    var allowFlash by Bool("a_flash", true)
}
