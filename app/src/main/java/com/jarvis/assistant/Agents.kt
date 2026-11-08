package com.jarvis.assistant

import org.json.JSONArray
import org.json.JSONObject

/** provider: "claude", "openai" (any OpenAI-compatible API: ChatGPT, Groq, OpenRouter, Ollama, DeepSeek...), or "gemini". */
data class Agent(val id: String, val name: String, val provider: String, val key: String, val model: String, val baseUrl: String)

fun loadAgents(p: Prefs): List<Agent> {
    val out = mutableListOf<Agent>()
    try {
        val a = JSONArray(p.agentsJson.ifBlank { "[]" })
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Agent(o.getString("id"), o.getString("name"), o.getString("provider"), o.optString("key"), o.optString("model"), o.optString("baseUrl")))
        }
    } catch (_: Exception) {}
    if (out.isEmpty()) out.add(Agent("claude-default", "Claude", "claude", p.apiKey, p.model, ""))
    return out
}

fun saveAgents(p: Prefs, l: List<Agent>) {
    p.agentsJson = JSONArray(l.map {
        JSONObject().put("id", it.id).put("name", it.name).put("provider", it.provider).put("key", it.key).put("model", it.model).put("baseUrl", it.baseUrl)
    }).toString()
}

fun activeAgent(p: Prefs): Agent {
    val l = loadAgents(p)
    return l.firstOrNull { it.id == p.activeAgent } ?: l.first()
}

/** Quick presets. Model names change over time, so they are editable in the app. */
fun newAgent(kind: String): Agent {
    val id = System.currentTimeMillis().toString()
    return when (kind) {
        "ChatGPT" -> Agent(id, "ChatGPT", "openai", "", "gpt-4o-mini", "https://api.openai.com/v1")
        "Gemini" -> Agent(id, "Gemini", "gemini", "", "gemini-2.0-flash", "")
        "Groq" -> Agent(id, "Groq", "openai", "", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1")
        "OpenRouter" -> Agent(id, "OpenRouter", "openai", "", "openai/gpt-4o-mini", "https://openrouter.ai/api/v1")
        "Ollama" -> Agent(id, "Ollama", "openai", "", "llama3.2", "http://192.168.1.10:11434/v1")
        else -> Agent(id, "Claude", "claude", "", "claude-sonnet-5-5", "")
    }
}
