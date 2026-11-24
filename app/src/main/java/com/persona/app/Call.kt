package com.persona.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private enum class CallState(val label: String) { WAITING("Соединяю…"), LISTENING("Слушаю"), HEARD("Расслышал"), THINKING("Думает…"), SPEAKING("Говорит"), MUTED("Микрофон выключен"), ERROR("Ошибка") }

/** Звонок персонажу: говоришь голосом — он отвечает голосом. */
@Composable
fun CallScreen(nav: Nav, chatId: String) {
    val ctx = LocalContext.current
    val app = ctx.app
    val repo = app.repo
    val runner = app.runner
    val chats by repo.chats.collectAsStateWithLifecycle()
    val running by runner.running.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    val c = chat?.let { repo.char(it.charId) }
    if (chat == null || c == null) { LaunchedEffect(Unit) { nav.back() }; return }

    var state by remember { mutableStateOf(CallState.WAITING) }
    var heard by remember { mutableStateOf("") }
    var said by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(System.currentTimeMillis()) }
    var seconds by remember { mutableStateOf(0) }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasMic = ok
        if (!ok) { error = "Без доступа к микрофону звонок не работает"; state = CallState.ERROR }
    }
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    val voice = c.voice ?: Voice()

    fun listen() {
        if (muted || recognizer == null || !hasMic) return
        state = CallState.LISTENING; heard = ""
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L))
    }

    DisposableEffect(recognizer) {
        val act = ctx as? android.app.Activity
        act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(r: Bundle?) {
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
            }
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (t.isBlank() || running != null) { listen(); return }
                heard = t; state = CallState.THINKING
                runner.send(chatId, t)
            }
            override fun onError(code: Int) {
                when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { error = "Нет доступа к микрофону"; state = CallState.ERROR }
                    else -> if (state == CallState.LISTENING) { error = "Распознавание речи: ошибка $code"; state = CallState.ERROR }
                }
            }
        })
        onDispose {
            act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            runCatching { recognizer?.destroy() }
            app.voice.stop()
        }
    }

    LaunchedEffect(Unit) {
        if (recognizer == null) { error = "На телефоне нет распознавания речи (нужно приложение Google)"; state = CallState.ERROR; return@LaunchedEffect }
        if (!hasMic) perm.launch(Manifest.permission.RECORD_AUDIO)
        started = System.currentTimeMillis()
        // Персонаж здоровается
        val last = chat.messages.lastOrNull()
        if (last != null && !last.fromUser && running == null) {
            state = CallState.SPEAKING; said = last.text
            app.voice.speak(last.text, voice) { listen() }
        } else listen()
    }
    LaunchedEffect(hasMic) { if (hasMic && state == CallState.WAITING) listen() }
    // Ответ готов — произносим и снова слушаем
    LaunchedEffect(running) {
        if (running == null && state == CallState.THINKING) {
            val m = repo.chat(chatId)?.messages?.lastOrNull()
            if (m != null && !m.fromUser && m.text.isNotBlank()) {
                said = m.text; state = CallState.SPEAKING
                app.voice.speak(m.text, voice) { listen() }
            } else { error = "Персонаж не ответил — проверь модель"; listen() }
        }
    }
    LaunchedEffect(Unit) { while (true) { seconds = ((System.currentTimeMillis() - started) / 1000).toInt(); delay(1000) } }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, if (state == CallState.SPEAKING || state == CallState.LISTENING) 1.08f else 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")

    Column(Modifier.fillMaxSize().background(Color(0xFF100E15)).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text(c.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp).scale(scale).clip(CircleShape).background(
                when (state) { CallState.SPEAKING -> Accent.copy(alpha = 0.25f); CallState.LISTENING -> Mint.copy(alpha = 0.2f); else -> Color.Transparent }))
            Avatar(c, 170.dp)
        }
        Spacer(Modifier.height(24.dp))
        Text(error?.takeIf { state == CallState.ERROR } ?: state.label, style = MaterialTheme.typography.titleMedium,
            color = when (state) { CallState.LISTENING -> Mint; CallState.SPEAKING -> Accent; CallState.ERROR -> MaterialTheme.colorScheme.error; else -> Amber })
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (heard.isNotBlank() && state != CallState.SPEAKING) Text("«$heard»", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (said.isNotBlank() && state == CallState.SPEAKING) Text(rpText(said, Accent), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton({
                muted = !muted
                if (muted) { recognizer?.cancel(); state = CallState.MUTED } else if (state == CallState.MUTED || state == CallState.ERROR) { error = null; listen() }
            }, Modifier.size(68.dp)) { Icon(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (muted) "Включить микрофон" else "Выключить микрофон", Modifier.size(30.dp)) }
            FilledIconButton({ runner.stop(); nav.back() }, Modifier.size(76.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE5484D), contentColor = Color.White)) {
                Icon(Icons.Rounded.CallEnd, "Завершить", Modifier.size(34.dp))
            }
        }
        Text("Реплики сохраняются в чат", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
