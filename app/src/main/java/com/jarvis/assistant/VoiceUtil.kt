package com.jarvis.assistant

import android.speech.tts.TextToSpeech
import java.util.Locale

/** Applies the chosen voice type (auto, female, male, neutral, robot, deep, kid) or a specific installed voice. */
object VoiceUtil {
    private fun style(s: String): Pair<Float, Float> = when (s) {
        "female" -> 1.18f to 1.0f
        "male" -> 0.82f to 1.0f
        "robot" -> 0.6f to 0.9f
        "deep" -> 0.7f to 0.95f
        "kid" -> 1.5f to 1.05f
        else -> 1.0f to 1.0f
    }

    fun apply(t: TextToSpeech, p: Prefs) {
        val loc = Locale.forLanguageTag(p.language)
        t.language = loc
        val vs = try { t.voices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        val named = vs.firstOrNull { it.name == p.voiceName }
        val want = when (p.voiceStyle) { "female" -> "female"; "male" -> "male"; else -> null }
        val guess = if (named == null && want != null) vs.firstOrNull {
            it.locale.language == loc.language && it.name.contains(want, true) && !(want == "male" && it.name.contains("female", true))
        } else null
        (named ?: guess)?.let { t.voice = it }
        val (pitch, rate) = style(p.voiceStyle)
        val shift = if (named != null || guess != null) 1f else pitch
        t.setPitch((shift * p.pitch).coerceIn(0.3f, 2f))
        t.setSpeechRate((rate * p.rate).coerceIn(0.3f, 2f))
    }
}
