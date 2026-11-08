package com.jarvis.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

object Brain {
    /** Blocking call: run on a background thread. Works with Claude, OpenAI-compatible APIs and Gemini. */
    fun ask(p: Prefs, history: List<Pair<String, String>>, agent: Agent): String {
        if (agent.key.isBlank() && agent.provider != "openai") return "Please add an API key for ${agent.name} in settings."
        val system = """
You are ${p.assistantName}, ${p.persona}. Speak like a friendly human: natural, short (1 to 3 sentences), no markdown, no lists, no emojis. Reply in the language the user speaks.
You can control the phone. To do something, put actions at the very END of your reply in exactly this format, after a short natural sentence saying what you're doing:
[[ACTION:call|name or number]] [[ACTION:sms|name or number|message]] [[ACTION:open_app|app name]] [[ACTION:web|query]] [[ACTION:youtube|query]]
[[ACTION:timer|seconds]] [[ACTION:alarm|HH:mm]] [[ACTION:flashlight|on or off]] [[ACTION:volume|up, down, mute or max]]
[[ACTION:settings|wifi, bluetooth, display, sound, battery or location]]
[[ACTION:home]] [[ACTION:back]] [[ACTION:recents]] [[ACTION:notifications]] [[ACTION:quick_settings]] [[ACTION:lock]] [[ACTION:screenshot]]
[[ACTION:click|visible text on screen]] [[ACTION:scroll|up or down]] [[ACTION:type|text to type in the focused field]]
[[ACTION:read_screen]] [[ACTION:remember|fact about the user]] [[ACTION:forget_all]]
Use read_screen when the user asks you to read or summarize what is on the screen; the screen text will be given to you in the next message. If the phone is locked, the system waits for the user to unlock it.\nOnly use an action when the user clearly asks. Never claim to do anything else on the device.
Current date and time: ${Date()}.
Saved memory about the user:
${p.memory.ifBlank { "(nothing yet)" }}
""".trim()

        val h = history.takeLast(12).dropWhile { it.first != "user" }
        return try {
            when (agent.provider) {
                "openai" -> openai(agent, system, h)
                "gemini" -> gemini(agent, system, h)
                else -> claude(agent, system, h)
            }
        } catch (e: Exception) {
            "Sorry, I couldn't reach ${agent.name}. Please check your internet."
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 40000; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            return Pair(code, s)
        } finally { c.disconnect() }
    }

    private fun bad(code: Int, name: String): String? = when {
        code in 200..299 -> null
        code == 401 || code == 403 -> "The API key for $name was rejected. Please check it in settings."
        code == 429 -> "$name is rate limited right now. Please try again in a moment."
        else -> "Sorry, $name returned an error, code $code."
    }

    private fun claude(a: Agent, system: String, h: List<Pair<String, String>>): String {
        val msgs = JSONArray(); h.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
        val body = JSONObject().put("model", a.model).put("max_tokens", 400).put("system", system).put("messages", msgs)
        val (code, txt) = post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to a.key.trim(), "anthropic-version" to "2023-06-01"), body)
        bad(code, a.name)?.let { return it }
        return JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun openai(a: Agent, system: String, h: List<Pair<String, String>>): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        h.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
        val body = JSONObject().put("model", a.model).put("max_tokens", 400).put("messages", msgs)
        val headers = if (a.key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${a.key.trim()}")
        val (code, txt) = post(a.baseUrl.trimEnd('/') + "/chat/completions", headers, body)
        bad(code, a.name)?.let { return it }
        return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun gemini(a: Agent, system: String, h: List<Pair<String, String>>): String {
        val contents = JSONArray()
        h.forEach { (r, t) ->
            contents.put(JSONObject().put("role", if (r == "user") "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", t))))
        }
        val body = JSONObject().put("contents", contents)
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val (code, txt) = post("https://generativelanguage.googleapis.com/v1beta/models/${a.model}:generateContent", mapOf("x-goog-api-key" to a.key.trim()), body)
        bad(code, a.name)?.let { return it }
        return JSONObject(txt).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
