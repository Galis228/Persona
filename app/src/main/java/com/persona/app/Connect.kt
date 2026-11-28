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

/** Где взять ключ у каждого провайдера и как его получить. */
object KeyLinks {
    fun url(p: Provider) = when (p) {
        Provider.OPENROUTER -> "https://openrouter.ai/keys"
        Provider.GEMINI -> "https://aistudio.google.com/apikey"
        Provider.OPENAI -> "https://platform.openai.com/api-keys"
        Provider.CLAUDE -> "https://console.anthropic.com/settings/keys"
        Provider.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
        Provider.CUSTOM -> null
    }

    /** Где пополнить баланс (если без этого ключ не работает). */
    fun billing(p: Provider) = when (p) {
        Provider.OPENROUTER -> "https://openrouter.ai/settings/credits"
        Provider.OPENAI -> "https://platform.openai.com/settings/organization/billing"
        Provider.CLAUDE -> "https://console.anthropic.com/settings/billing"
        Provider.DEEPSEEK -> "https://platform.deepseek.com/top_up"
        else -> null
    }

    /** Короткая инструкция «как получить ключ» по шагам. */
    fun steps(p: Provider): List<String> = when (p) {
        Provider.OPENROUTER -> listOf("Нажми «Войти через OpenRouter» — ключ создастся сам", "Или открой сайт → Keys → Create Key и вставь ключ ниже")
        Provider.OPENAI -> listOf("Открой сайт → войди → «Create new secret key»", "Пополни баланс в Billing (от \$5) — без денег на счету ключ OpenAI не отвечает", "Скопируй ключ (sk-…) и вставь ниже")
        Provider.GEMINI -> listOf("Открой Google AI Studio → «Create API key»", "Скопируй ключ (AIza…) и вставь ниже — платить не нужно, есть бесплатный лимит")
        Provider.CLAUDE -> listOf("Открой консоль Anthropic → API Keys → Create Key", "Пополни баланс в Billing", "Скопируй ключ (sk-ant-…) и вставь ниже")
        Provider.DEEPSEEK -> listOf("Открой платформу DeepSeek → API keys → Create", "Пополни баланс (Top up) — хватит пары долларов надолго", "Скопируй ключ и вставь ниже")
        Provider.CUSTOM -> listOf("Запусти LM Studio, Ollama или другой OpenAI-совместимый сервер", "Впиши его адрес, например http://192.168.1.10:1234/v1", "Ключ — если сервер его требует")
    }

    /** Угадываем провайдера по виду ключа (только однозначные префиксы). */
    fun detect(key: String): Provider? = when {
        key.startsWith("sk-or-") -> Provider.OPENROUTER
        key.startsWith("sk-ant-") -> Provider.CLAUDE
        key.startsWith("AIza") -> Provider.GEMINI
        key.startsWith("sk-proj-") || key.startsWith("sk-svcacct-") || key.startsWith("sk-admin-") -> Provider.OPENAI
        else -> null
    }

    /** Чистит ключ после вставки: пробелы, переносы, кавычки, «Bearer ». */
    fun clean(t: String) = t.trim().removePrefix("Bearer ").trim().trim('"', '\'', '«', '»').replace(Regex("\\s"), "")

    /** Похоже ли на ключ: одно «слово» без пробелов, достаточно длинное. */
    fun looksLikeKey(t: String) = t.length in 20..300 && !t.contains(Regex("\\s"))
}

/** Известные модели: понятное имя, цена за 1 млн токенов, пометки. Цены примерные. */
object KnownModels {
    data class Info(val id: String, val title: String, val price: Price, val note: String = "", val tag: String = "")

    val list = listOf(
        Info("gpt-4o-mini", "GPT-4o mini", Price(0.15, 0.60, 0.075), "OpenAI отключит её 14 января 2027", "Дёшево"),
        Info("gpt-4.1-mini", "GPT-4.1 mini", Price(0.40, 1.60, 0.10), "OpenAI отключит её 14 января 2027"),
        Info("gpt-4.1-nano", "GPT-4.1 nano", Price(0.10, 0.40, 0.025), "Самая дешёвая, пишет проще"),
        Info("gpt-5-mini", "GPT-5 mini", Price(0.25, 2.00, 0.025), "Думает перед ответом — медленнее"),
        Info("gpt-5-nano", "GPT-5 nano", Price(0.05, 0.40, 0.005), "Думает перед ответом, очень дешёвая"),
        Info("gpt-4o", "GPT-4o", Price(2.50, 10.0, 1.25), "Дорогая"),
        Info("claude-haiku-4-5", "Claude Haiku 4.5", Price(1.0, 5.0, 0.10), "Быстрая, хорошо держит роль", "Баланс"),
        Info("claude-sonnet-4-5", "Claude Sonnet 4.5", Price(3.0, 15.0, 0.30), "Лучше всех пишет, но дороже", "Качество"),
        Info("claude-sonnet-4", "Claude Sonnet 4", Price(3.0, 15.0, 0.30)),
        Info("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", Price(0.10, 0.40, 0.01), "Очень дешёвая"),
        Info("gemini-2.5-flash", "Gemini 2.5 Flash", Price(0.30, 2.50, 0.03), "Есть бесплатный лимит", "Дёшево"),
        Info("gemini-2.5-pro", "Gemini 2.5 Pro", Price(1.25, 10.0, 0.31), "Умнее, но дороже"),
        Info("deepseek-chat", "DeepSeek Chat", Price(0.28, 0.42, 0.028), "Очень дёшево, кэш автоматически", "Дёшево"),
        Info("deepseek-reasoner", "DeepSeek Reasoner", Price(0.28, 0.42, 0.028), "Думает перед ответом — медленнее"),
    )

    /** Цены, которые прислал сам провайдер (OpenRouter). */
    val live = java.util.concurrent.ConcurrentHashMap<String, Price>()

    private fun bare(model: String) = model.substringAfterLast('/').removeSuffix(":free").lowercase().replace('.', '-')

    fun info(model: String): Info? {
        val b = bare(model)
        return list.filter { val id = it.id.replace('.', '-'); b == id || b.startsWith("$id-") }.maxByOrNull { it.id.length }
    }

    fun title(model: String) = info(model)?.title?.let { if (model.endsWith(":free")) "$it (бесплатно)" else it } ?: model.substringAfterLast('/')

    fun price(model: String): Price? = when {
        model.endsWith(":free") -> Price(0.0, 0.0, 0.0)
        else -> live[model] ?: info(model)?.price
    }

    fun cost(p: Price, inTok: Long, cachedTok: Long, outTok: Long): Double {
        val c = cachedTok.coerceIn(0, inTok)
        return ((inTok - c) * p.inPerM + c * (p.cachedPerM ?: p.inPerM) + outTok * p.outPerM) / 1_000_000.0
    }

    /** Сколько в среднем токенов уходит на один ответ при такой длине памяти. */
    fun avgInput(apiContext: Int) = 2000 + (apiContext * 0.35).toInt()

    /** Примерная цена одного ответа в долларах. */
    fun perReply(p: Price, s: Settings): Double {
        val inTok = avgInput(s.apiContext).toLong()
        val cached = if (s.promptCache && p.cachedPerM != null) (inTok * 0.7).toLong() else 0L
        return cost(p, inTok, cached, (s.length.maxOut * 0.7).toLong())
    }

    fun money(v: Double): String = when {
        v == 0.0 -> "бесплатно"
        v < 0.01 -> String.format(java.util.Locale.US, "$%.4f", v)
        else -> String.format(java.util.Locale.US, "$%.2f", v)
    }

    fun repliesLabel(n: Double): String = when {
        n >= 1_000_000 -> "больше миллиона"
        n >= 10_000 -> "≈${(n / 1000).toInt()} тыс."
        n >= 1000 -> "≈${String.format(java.util.Locale.US, "%.1f", n / 1000).replace('.', ',')} тыс."
        else -> "≈${n.toInt()}"
    }
}

/** Сама выбирает хорошую модель для ролплея и понимает названия «как попало» («4o mini», «gpt 4o-mini»). */
object ModelPick {
    private val junk = listOf("embed", "tts", "audio", "realtime", "transcribe", "image", "vision-preview", "search", "moderation", "whisper", "dall-e", "imagen", "live", "guard", "instruct-beta", "codex", "babbage", "davinci", "computer-use", "deep-research", "veo", "aqa", "learnlm")

    fun isChat(m: String) = junk.none { m.contains(it, true) }

    private fun tokens(t: String) = Regex("[a-z]+|\\d+").findAll(t.lowercase()).map { it.value }.toList()

    /** Подходит ли модель под то, что набрал человек: все «слова» запроса есть в названии. */
    fun matches(query: String, model: String): Boolean {
        val q = tokens(query)
        if (q.isEmpty()) return true
        if (model.contains(query.trim(), true)) return true
        val m = tokens(model) + tokens(KnownModels.title(model))
        return q.all { it in m }
    }

    /** Модели, у которых совпадает хоть часть «слов» запроса — для подсказки. */
    fun similar(query: String, models: List<String>): List<String> {
        val q = tokens(query).filter { it.length > 1 || it.any(Char::isDigit) }
        return models.filter { isChat(it) }.map { it to tokens(it).count { t -> t in q } }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length }).map { it.first }
    }

    /** Лучшее совпадение для набранного вручную названия: самое короткое из подходящих. */
    fun best(query: String, models: List<String>): String? {
        val q = query.trim()
        if (q.isBlank()) return null
        models.firstOrNull { it.equals(q, true) }?.let { return it }
        return models.filter { isChat(it) && matches(q, it) }.minByOrNull { it.length }
    }

    private val prefer = mapOf(
        Provider.OPENAI to listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-5-mini"),
        Provider.CLAUDE to listOf("claude-haiku-4-5", "claude-sonnet-4-5"),
        Provider.GEMINI to listOf("gemini-2.5-flash"),
        Provider.DEEPSEEK to listOf("deepseek-chat"),
    )

    fun recommend(p: Provider, models: List<String>): String? {
        val ok = models.filter { isChat(it) }
        fun latest(l: List<String>) = l.sortedDescending().firstOrNull()
        prefer[p]?.forEach { id -> ok.filter { it == id || it.startsWith("$id-") }.minByOrNull { it.length }?.let { return it } }
        return when (p) {
            Provider.OPENROUTER -> {
                val free = ok.filter { it.endsWith(":free") }
                listOf("deepseek", "gemini", "llama", "mistral", "qwen", "gemma").firstNotNullOfOrNull { f -> latest(free.filter { it.contains(f, true) }) }
                    ?: latest(free) ?: ok.firstOrNull { it == "openrouter/auto" } ?: ok.firstOrNull()
            }
            Provider.GEMINI -> latest(ok.filter { it.contains("gemini") && it.contains("flash") && !it.contains("lite") && !it.contains("exp") })
                ?: latest(ok.filter { it.contains("gemini") }) ?: ok.firstOrNull()
            Provider.OPENAI -> latest(ok.filter { it.startsWith("gpt-") && it.contains("mini") && !it.contains("nano") })
                ?: latest(ok.filter { it.startsWith("gpt-") }) ?: ok.firstOrNull()
            Provider.CLAUDE -> latest(ok.filter { it.contains("haiku") }) ?: latest(ok.filter { it.contains("sonnet") }) ?: ok.firstOrNull()
            Provider.DEEPSEEK, Provider.CUSTOM -> ok.firstOrNull()
        }
    }

    /** Рекомендуемые модели провайдера: известные и есть в списке (или все известные, если список пуст). */
    fun featured(p: Provider, models: List<String>): List<String> {
        val ids = when (p) {
            Provider.OPENAI -> listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-5-mini")
            Provider.CLAUDE -> listOf("claude-haiku-4-5", "claude-sonnet-4-5")
            Provider.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro")
            Provider.DEEPSEEK -> listOf("deepseek-chat")
            Provider.OPENROUTER -> listOf("openai/gpt-4o-mini", "deepseek/deepseek-chat", "google/gemini-2.5-flash", "anthropic/claude-haiku-4.5")
            Provider.CUSTOM -> emptyList()
        }
        if (models.isEmpty()) return ids
        val found = ids.mapNotNull { id -> models.filter { it == id || it.startsWith("$id-") }.minByOrNull { it.length } }
        val free = if (p == Provider.OPENROUTER) models.filter { it.endsWith(":free") && isChat(it) }.sortedBy { KnownModels.title(it) }.take(6) else emptyList()
        return (found + free).distinct()
    }
}

/** Подключение по ключу в одно действие: проверяет ключ, выбирает модель, пробует настоящий запрос, включает API. */
object Connector {
    data class Result(val ok: Boolean, val message: String, val models: List<String> = emptyList(), val url: String? = null, val model: String? = null)

    suspend fun connect(ctx: Context, provider: Provider, key: String, baseUrl: String? = null, wantModel: String? = null): Result {
        val app = ctx.app
        val repo = app.repo
        val k = KeyLinks.clean(key)
        if (k.isBlank() && provider != Provider.CUSTOM) return Result(false, "Вставь ключ.")
        val test = repo.settings.value.copy(provider = provider, baseUrl = baseUrl ?: repo.settings.value.baseUrl)
        if (provider == Provider.CUSTOM && test.apiBase.isBlank()) return Result(false, "Впиши адрес сервера.")
        val models = try {
            app.engines.api.checkKey(test, k)
            app.engines.api.listModels(test, k)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (provider != Provider.CUSTOM) return Result(false, e.message ?: "Не получилось проверить ключ", url = (e as? EngineException)?.url)
            emptyList()
        }
        val want = wantModel?.trim()?.takeIf { it.isNotBlank() }
        val found = want?.let { ModelPick.best(it, models) ?: it.takeIf { models.isEmpty() } }
        if (want != null && found == null) {
            val similar = ModelPick.similar(want, models).take(3).joinToString { KnownModels.title(it) }
            return Result(false, "Ключ рабочий, но модели «$want» у ${provider.title} нет." + if (similar.isNotBlank()) " Похожие: $similar." else " Оставь поле пустым — подберу сам.", models)
        }
        val pick = found
            ?: ModelPick.recommend(provider, models)
            ?: test.apiModel.takeIf { provider == test.provider && it.isNotBlank() }
            ?: return Result(false, if (provider == Provider.CUSTOM) "Сервер не сказал, какие у него модели. Впиши название модели." else "Ключ работает, но подходящих моделей не нашлось.", models)
        // Настоящий маленький запрос: так сразу видно, есть ли деньги на счету и отвечает ли модель
        val probe = test.copy(apiModel = pick)
        val err = runCatching { app.engines.api.ping(probe, k) }.exceptionOrNull()
        if (err is kotlinx.coroutines.CancellationException) throw err
        if (err != null) return Result(false, "Ключ принят, но модель ${KnownModels.title(pick)} не ответила.\n${err.message}", models, (err as? EngineException)?.url ?: KeyLinks.billing(provider).takeIf { (err as? EngineException)?.kind == ErrKind.CREDITS }, pick)
        repo.settings { s ->
            s.copy(provider = provider, baseUrl = baseUrl ?: s.baseUrl, apiKeyEnc = KeyCrypto.encrypt(k), apiModel = pick,
                mode = if (app.engines.local.hasModel(s)) s.mode else Mode.API)
        }
        return Result(true, "Готово! Подключено: ${KnownModels.title(pick)}", models, model = pick)
    }

    /** Проверка связи с уже подключённой моделью. */
    suspend fun test(ctx: Context, model: String? = null): Result {
        val app = ctx.app
        val s = app.repo.settings.value.let { if (model != null) it.copy(apiModel = model) else it }
        return runCatching { app.engines.api.ping(s, app.engines.api.key(s)) }.fold(
            { Result(true, "Связь есть: ${KnownModels.title(s.apiModel)} отвечает.") },
            { if (it is kotlinx.coroutines.CancellationException) throw it; Result(false, it.message ?: "Модель не ответила", url = (it as? EngineException)?.url) })
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
