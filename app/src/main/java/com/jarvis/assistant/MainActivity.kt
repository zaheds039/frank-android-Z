package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.speech.tts.TextToSpeech
import java.util.Locale

private val Bg = Color(0xFF0B0B10)
private val CardC = Color(0xFF16161F)
private val Chip = Color(0xFF22222E)
private val Lime = Color(0xFFC8FF3D)
private val Purple = Color(0xFF8B5CF6)
private val Pink = Color(0xFFFF4D9D)
private val Muted = Color(0xFF8A8AA0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Color.Black, background = Bg, surface = Bg, onSurface = Color.White, onBackground = Color.White)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Home(prefs) }
            }
        }
    }
}

private fun has(c: Context, perm: String) = ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED
private fun startSvc(c: Context) = ContextCompat.startForegroundService(c, Intent(c, JarvisService::class.java))
private fun stopSvc(c: Context) { c.startService(Intent(c, JarvisService::class.java).setAction(JarvisService.STOP)) }

@Composable
fun Orb(on: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val s by t.animateFloat(1f, if (on) 1.12f else 1.03f, infiniteRepeatable(tween(if (on) 900 else 2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Box(
        Modifier.size(230.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(230.dp).scale(s).background(Brush.radialGradient(listOf(Purple.copy(alpha = if (on) 0.55f else 0.18f), Color.Transparent)), CircleShape))
        Box(Modifier.size(140.dp).scale(s).background(Brush.linearGradient(if (on) listOf(Lime, Purple) else listOf(Color(0xFF3A3A4A), Color(0xFF22222E))), CircleShape))
        Text(if (on) "🎙️" else "😴", fontSize = 44.sp)
    }
}

@Composable
fun Bento(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardC, RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        content()
    }
}

@Composable
fun Field(label: String, value: String, secret: Boolean = false, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, minLines = lines,
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Lime, unfocusedBorderColor = Color(0xFF2A2A38), focusedLabelColor = Lime, unfocusedLabelColor = Muted, cursorColor = Lime)
    )
}

@Composable
fun Power(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = on, onClick = { onChange(!on) }, label = { Text(label) }, shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(containerColor = Chip, labelColor = Color.White, selectedContainerColor = Lime, selectedLabelColor = Color.Black)
    )
}

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = CircleShape, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Chip, contentColor = Color.White)) { Text(text) }
}

@Composable
fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text, color = Color.White, modifier = Modifier.widthIn(max = 290.dp)
                .background(if (mine) Color(0xFF2A2A38) else Purple, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentCard(a: Agent, active: Boolean, onChange: (Agent) -> Unit, onUse: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (active) Color(0xFF1F2A12) else Chip, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Field("Agent name (say \"ask ${a.name}...\")", a.name) { onChange(a.copy(name = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("claude" to "Claude", "openai" to "OpenAI-style", "gemini" to "Gemini").forEach { (k, n) ->
                Power(n, a.provider == k) { onChange(a.copy(provider = k)) }
            }
        }
        Field("API key", a.key, secret = true) { onChange(a.copy(key = it)) }
        Field("Model", a.model) { onChange(a.copy(model = it)) }
        if (a.provider == "openai") Field("Base URL", a.baseUrl) { onChange(a.copy(baseUrl = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onUse, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = if (active) Lime else CardC, contentColor = if (active) Color.Black else Color.White)) {
                Text(if (active) "✓ active" else "Use this")
            }
            TextButton(onClick = onDelete) { Text("Delete", color = Pink) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Home(p: Prefs) {
    val ctx = LocalContext.current
    val running = JarvisService.running

    var name by remember { mutableStateOf(p.assistantName) }
    var agents by remember { mutableStateOf(loadAgents(p)) }
    var active by remember { mutableStateOf(activeAgent(p).id) }
    var wake by remember { mutableStateOf(p.wakeWord) }
    var lang by remember { mutableStateOf(p.language) }
    var persona by remember { mutableStateOf(p.persona) }
    var memory by remember { mutableStateOf(p.memory) }
    var rate by remember { mutableStateOf(p.rate) }
    var pitch by remember { mutableStateOf(p.pitch) }
    var calls by remember { mutableStateOf(p.allowCalls) }
    var sms by remember { mutableStateOf(p.allowSms) }
    var apps by remember { mutableStateOf(p.allowApps) }
    var web by remember { mutableStateOf(p.allowWeb) }
    var alarms by remember { mutableStateOf(p.allowAlarms) }
    var flash by remember { mutableStateOf(p.allowFlash) }
    var control by remember { mutableStateOf(p.allowControl) }

    var vstyle by remember { mutableStateOf(p.voiceStyle) }
    var vname by remember { mutableStateOf(p.voiceName) }
    var voices by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(lang) {
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts = t
                val l = Locale.forLanguageTag(lang).language
                voices = (t?.voices?.filter { it.locale.language == l }?.map { it.name }?.sorted() ?: emptyList()).take(12)
            }
        }
        onDispose { t?.shutdown() }
    }

    fun save(l: List<Agent>) { agents = l; saveAgents(p, l) }

    val startPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (has(ctx, Manifest.permission.RECORD_AUDIO)) startSvc(ctx)
    }
    val extraPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    fun start() {
        val need = mutableListOf<String>()
        if (!has(ctx, Manifest.permission.RECORD_AUDIO)) need += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && !has(ctx, Manifest.permission.POST_NOTIFICATIONS)) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isEmpty()) startSvc(ctx) else startPerms.launch(need.toTypedArray())
    }
    fun allPerms(): Array<String> {
        val l = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.POST_NOTIFICATIONS
        return l.toTypedArray()
    }

    Column(
        Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("hey 👋", color = Muted, fontSize = 16.sp)
            Text(name.ifBlank { "Assistant" }, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        }
        Orb(running) { if (running) stopSvc(ctx) else start() }
        Text(
            if (running) "listening… say \"$wake\"" else "tap me to wake me up",
            color = if (running) Lime else Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold
        )

        if (JarvisService.lastHeard.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bubble(JarvisService.lastHeard, mine = true)
                if (JarvisService.lastReply.isNotEmpty()) Bubble(JarvisService.lastReply, mine = false)
            }
        }

        Bento("✏️ identity") {
            Field("Assistant name", name) { name = it; p.assistantName = it; wake = it.lowercase().trim(); p.wakeWord = wake }
            Field("Vibe (who it is and how it talks)", persona, lines = 3) { persona = it; p.persona = it }
            Text("changing the name also changes the wake word. restart to apply.", color = Muted, fontSize = 12.sp)
        }

        Bento("🤖 AI agents") {
            Text("say \"ask <agent name> ...\" to use one for a single question, or \"switch to <agent name>\" to change the main brain.", color = Muted, fontSize = 12.sp)
            agents.forEach { a ->
                key(a.id) {
                    AgentCard(
                        a, a.id == active,
                        onChange = { n -> save(agents.map { if (it.id == a.id) n else it }) },
                        onUse = { active = a.id; p.activeAgent = a.id },
                        onDelete = {
                            if (agents.size > 1) {
                                val rest = agents.filter { it.id != a.id }
                                save(rest)
                                if (active == a.id) { active = rest.first().id; p.activeAgent = active }
                            }
                        }
                    )
                }
            }
            Text("add an agent", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Claude", "ChatGPT", "Gemini", "Groq", "OpenRouter", "Ollama").forEach { k ->
                    Power("+ $k", false) { save(agents + newAgent(k)) }
                }
            }
        }

        Bento("⚡ powers") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Power("📞 calls", calls) { calls = it; p.allowCalls = it }
                Power("💬 texts", sms) { sms = it; p.allowSms = it }
                Power("📱 apps", apps) { apps = it; p.allowApps = it }
                Power("🔎 web", web) { web = it; p.allowWeb = it }
                Power("⏰ alarms", alarms) { alarms = it; p.allowAlarms = it }
                Power("🔦 torch", flash) { flash = it; p.allowFlash = it }
                Power("🖐️ phone control", control) { control = it; p.allowControl = it }
            }
        }

        Bento("🎙️ voice") {
            Field("Wake word", wake) { wake = it; p.wakeWord = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en-IN" to "English IN", "en-US" to "English US", "hi-IN" to "हिन्दी", "te-IN" to "తెలుగు", "ta-IN" to "தமிழ்").forEach { (c, n) ->
                    Power(n, lang == c) { lang = c; p.language = c }
                }
            }
            Text("voice type", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "✨ Auto", "female" to "👩 Female", "male" to "👨 Male", "neutral" to "🧑 Neutral", "robot" to "🤖 Robot", "deep" to "🎚️ Deep", "kid" to "🧒 Kid").forEach { (k, n) ->
                    Power(n, vstyle == k && vname.isEmpty()) { vstyle = k; p.voiceStyle = k; vname = ""; p.voiceName = "" }
                }
            }
            if (voices.isNotEmpty()) {
                Text("or pick an installed voice", color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    voices.forEachIndexed { i, v -> Power("Voice ${i + 1}", vname == v) { vname = v; p.voiceName = v } }
                }
            }
            Pill("▶ preview voice") { tts?.let { VoiceUtil.apply(it, p); it.speak("Hi, I'm ${name.ifBlank { "your assistant" }}. This is how I sound.", TextToSpeech.QUEUE_FLUSH, null, "pv") } }
            Text("speed %.1f".format(rate), color = Muted)
            Slider(value = rate, onValueChange = { rate = it; p.rate = it }, valueRange = 0.5f..1.5f, colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime))
            Text("pitch %.2f".format(pitch), color = Muted)
            Slider(value = pitch, onValueChange = { pitch = it; p.pitch = it }, valueRange = 0.5f..1.5f, colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink))
        }

        Bento("🔐 full access") {
            Text(
                if (JarvisAccessibilityService.connected) "✅ phone control is on" else "⚠️ phone control is off",
                color = if (JarvisAccessibilityService.connected) Lime else Pink, fontWeight = FontWeight.Bold
            )
            Pill("1 · Grant all permissions") { extraPerms.launch(allPerms()) }
            Pill("2 · Turn on phone control") { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            Pill("3 · Act from the background") { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }
            Text("for screen-off use: set battery to Unrestricted, lock the app in recent apps, and allow autostart if your phone has it", color = Muted, fontSize = 12.sp)
            Pill("4 · Battery settings") { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }

        Bento("💭 memory") {
            Field("one fact per line, or say \"remember that…\"", memory, lines = 6) { memory = it; p.memory = it }
            Text("keys and memory stay encrypted on this phone", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
