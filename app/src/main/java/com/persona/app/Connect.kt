package com.persona.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Где взять ключ у каждого провайдера. */
object KeyLinks {
    fun url(p: Provider) = when (p) {
        Provider.OPENROUTER -> "https://openrouter.ai/keys"
        Provider.GEMINI -> "https://aistudio.google.com/apikey"
        Provider.OPENAI -> "https://platform.openai.com/api-keys"
        Provider.CLAUDE -> "https://console.anthropic.com/settings/keys"
        Provider.CUSTOM -> null
    }

    /** Угадываем провайдера по виду ключа. */
    fun detect(key: String): Provider? = when {
        key.startsWith("sk-or-") -> Provider.OPENROUTER
        key.startsWith("sk-ant-") -> Provider.CLAUDE
        key.startsWith("AIza") -> Provider.GEMINI
        key.startsWith("sk-") -> Provider.OPENAI
        else -> null
    }

    /** Похоже ли на ключ: одно «слово» без пробелов, достаточно длинное. */
    fun looksLikeKey(t: String) = t.length in 20..300 && !t.contains(Regex("\\s"))
}

/** Сама выбирает хорошую модель для ролплея из списка провайдера. */
object ModelPick {
    private val junk = listOf("embed", "tts", "audio", "realtime", "transcribe", "image", "vision-preview", "search", "moderation", "whisper", "dall-e", "imagen", "live", "guard", "instruct-beta", "codex")

    fun recommend(p: Provider, models: List<String>): String? {
        val ok = models.filter { m -> junk.none { m.contains(it, true) } }
        fun latest(l: List<String>) = l.sortedDescending().firstOrNull()
        return when (p) {
            Provider.OPENROUTER -> {
                val free = ok.filter { it.endsWith(":free") }
                listOf("gemini", "deepseek", "llama", "mistral", "qwen", "gemma").firstNotNullOfOrNull { f -> latest(free.filter { it.contains(f, true) }) }
                    ?: latest(free) ?: ok.firstOrNull { it == "openrouter/auto" } ?: ok.firstOrNull()
            }
            Provider.GEMINI -> latest(ok.filter { it.contains("gemini") && it.contains("flash") && !it.contains("lite") && !it.contains("exp") })
                ?: latest(ok.filter { it.contains("gemini") }) ?: ok.firstOrNull()
            Provider.OPENAI -> latest(ok.filter { it.startsWith("gpt-") && it.contains("mini") && !it.contains("nano") })
                ?: latest(ok.filter { it.startsWith("gpt-") }) ?: ok.firstOrNull()
            Provider.CLAUDE -> latest(ok.filter { it.contains("sonnet") }) ?: latest(ok.filter { it.contains("haiku") }) ?: ok.firstOrNull()
            Provider.CUSTOM -> ok.firstOrNull()
        }
    }
}

/** Подключение по ключу в одно действие: проверяет ключ, выбирает модель, включает API. */
object Connector {
    data class Result(val ok: Boolean, val message: String, val models: List<String> = emptyList())

    suspend fun connect(ctx: Context, provider: Provider, key: String, baseUrl: String? = null): Result {
        val app = ctx.app
        val repo = app.repo
        val k = key.trim()
        val test = repo.settings.value.copy(provider = provider, baseUrl = baseUrl ?: repo.settings.value.baseUrl)
        return runCatching { app.engines.api.checkKey(test, k); app.engines.api.listModels(test, k) }.fold({ models ->
            val pick = ModelPick.recommend(provider, models)
            repo.settings { s ->
                s.copy(provider = provider, baseUrl = baseUrl ?: s.baseUrl, apiKeyEnc = KeyCrypto.encrypt(k),
                    apiModel = pick ?: s.apiModel.takeIf { provider == s.provider }.orEmpty(),
                    mode = if (app.engines.local.hasModel(s)) s.mode else Mode.API)
            }
            Result(true, if (pick != null) "Готово! Модель: ${pick.substringAfterLast('/')}" else "Ключ работает. Выбери модель ниже.", models)
        }, { Result(false, it.message ?: "Не получилось проверить ключ") })
    }
}

/** Вход через OpenRouter без копирования ключа (OAuth PKCE с возвратом на localhost). */
object OpenRouterAuth {
    sealed interface State {
        data object Idle : State
        data object Waiting : State
        data object Exchanging : State
        data class Done(val message: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun start(ctx: Context) {
        job?.cancel()
        val app = ctx.applicationContext
        val verifier = b64url(ByteArray(48).also { SecureRandom().nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val server = runCatching { ServerSocket(0).apply { soTimeout = 10 * 60 * 1000 } }.getOrElse {
            _state.value = State.Failed("Не удалось начать вход: ${it.message}"); return
        }
        val callback = "http://localhost:${server.localPort}/callback"
        _state.value = State.Waiting
        job = scope.launch {
            try {
                val code = server.use { srv ->
                    var found: String? = null
                    while (found == null) {
                        val sock = srv.accept()
                        sock.use { s ->
                            val line = s.getInputStream().bufferedReader().readLine().orEmpty()
                            val path = line.split(' ').getOrNull(1).orEmpty()
                            val q = path.substringAfter('?', "")
                            val params = q.split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], "UTF-8") } }.toMap()
                            found = params["code"]
                            val html = if (found != null) DONE_HTML else "<html><body>Ожидаю ответ OpenRouter…</body></html>"
                            val body = html.toByteArray()
                            s.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body); flush()
                            }
                        }
                    }
                    found!!
                }
                _state.value = State.Exchanging
                val key = exchange(code, verifier)
                val r = Connector.connect(app, Provider.OPENROUTER, key)
                _state.value = if (r.ok) State.Done(r.message) else State.Failed(r.message)
            } catch (e: SocketTimeoutException) {
                _state.value = State.Failed("Время входа вышло. Попробуй ещё раз.")
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ErrorLog.add("openrouter-auth", e)
                _state.value = State.Failed(e.message ?: "Вход не удался")
            }
        }
        val url = "https://openrouter.ai/auth?callback_url=${Uri.encode(callback)}&code_challenge=$challenge&code_challenge_method=S256&key_label=${Uri.encode("Персона")}"
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun reset() { job?.cancel(); _state.value = State.Idle }

    private suspend fun exchange(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val c = URL("https://openrouter.ai/api/v1/auth/keys").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(buildJsonObject { put("code", code); put("code_verifier", verifier); put("code_challenge_method", "S256") }.toString().toByteArray()) }
            if (c.responseCode !in 200..299) throw EngineException("OpenRouter не выдал ключ (${c.responseCode}). Попробуй ещё раз.")
            (json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject["key"] as? JsonPrimitive)?.content
                ?: throw EngineException("OpenRouter не прислал ключ")
        } finally { c.disconnect() }
    }

    private const val DONE_HTML = """<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Персона</title><style>body{margin:0;font-family:sans-serif;background:#14121A;color:#ECE6F3;display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center}
a{display:inline-block;margin-top:20px;padding:14px 28px;border-radius:28px;background:#C9B6FF;color:#24124F;font-weight:700;text-decoration:none}</style></head>
<body><div><h2>Готово!</h2><p>OpenRouter подключён к Персоне.</p><a href="intent://connected#Intent;scheme=persona;package=com.persona.app;end">Вернуться в Персону</a></div></body></html>"""
}
