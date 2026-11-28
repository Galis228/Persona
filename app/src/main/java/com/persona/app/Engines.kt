package com.persona.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import android.os.Build
import android.os.Environment
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val SHARED_FOLDER = "AI-Models"

enum class ErrKind { OTHER, KEY, CREDITS, QUOTA_DAILY, RATE, PRIVACY, MODEL }

class EngineException(msg: String, val kind: ErrKind = ErrKind.OTHER, val url: String? = null) : Exception(msg)

/** Шифрует API-ключ ключом из Android Keystore — в файлах настроек он не лежит открытым текстом. */
object KeyCrypto {
    private const val ALIAS = "persona_api_key"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }
    fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }
    fun decrypt(enc: String): String = runCatching {
        if (enc.isBlank()) return ""
        val all = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)))
    }.getOrDefault("")
}

data class ModelInfo(
    val file: String, val title: String, val sizeBytes: Long, val note: String, val url: String,
    val best: Boolean = false,
)

private const val HF = "https://huggingface.co/litert-community"

object ModelCatalog {
    val all = listOf(
        ModelInfo("gemma-4-E2B-it.litertlm", "Gemma 4 E2B", 2_780_000_000L,
            "Лучший баланс ума и скорости, отличный русский.", "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm", best = true),
        ModelInfo("gemma-4-E4B-it.litertlm", "Gemma 4 E4B", 3_930_000_000L,
            "Умнее и живее в ролях, но медленнее и тяжелее.", "$HF/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"),
        ModelInfo("LFM2-2.6B_int4.litertlm", "LFM2 2.6B", 1_640_000_000L,
            "Быстрая и лёгкая, русский средний.", "$HF/LFM2-2.6B/resolve/main/LFM2-2.6B_int4.litertlm"),
        ModelInfo("qwen3_4b_mixed_int4.litertlm", "Qwen 3 4B", 2_860_000_000L,
            "Сильна в логике, по-русски пишет хуже Gemma.", "$HF/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm"),
    )
    fun title(file: String) = all.firstOrNull { it.file == file }?.title ?: file.substringBeforeLast('.')
}

/** Модель на телефоне через LiteRT-LM. */
class LocalEngine(private val ctx: Context) {
    @Volatile private var engine: Engine? = null
    @Volatile private var loadedKey: String? = null
    private val loadLock = Mutex()
    private val genLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var unloadJob: Job? = null

    /** Своя папка приложения — её видит только «Персона». */
    val privateDir: File get() = (ctx.getExternalFilesDir("models") ?: File(ctx.filesDir, "models")).apply { mkdirs() }
    /** Общая папка моделей для «Персоны» и «НаноПеро»: Загрузки/AI-Models. Нужен «Доступ ко всем файлам». */
    val sharedDir: File get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SHARED_FOLDER)
    fun sharedAllowed(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    /** Куда класть новые модели: в общую папку, если есть доступ. */
    val saveDir: File get() = (if (sharedAllowed()) sharedDir else privateDir).apply { mkdirs() }
    private fun dirs(): List<File> = listOfNotNull(sharedDir.takeIf { sharedAllowed() }, privateDir)
    fun models(): List<File> = dirs().flatMap { d -> d.listFiles()?.toList() ?: emptyList() }
        .filter { it.isFile && it.name.endsWith(".litertlm") && it.length() > 10_000_000 }
        .distinctBy { it.name }.sortedBy { it.name }
    fun find(name: String): File? = if (name.isBlank()) null else dirs().map { File(it, name) }.firstOrNull { it.exists() && it.length() > 10_000_000 }
    fun isShared(f: File) = f.parentFile?.absolutePath == sharedDir.absolutePath
    fun hasModel(s: Settings) = find(s.activeModel) != null

    /** Переносит модели из своей папки в общую, чтобы их видел и «НаноПеро». */
    suspend fun moveToShared(onProgress: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        if (!sharedAllowed()) throw EngineException("Нет доступа к общей папке")
        loadLock.withLock { unloadLocked() }
        val dst = sharedDir.apply { mkdirs() }
        var moved = 0
        privateDir.listFiles()?.filter { it.isFile && it.name.endsWith(".litertlm") }?.forEach { f ->
            val target = File(dst, f.name)
            if (target.exists() && target.length() == f.length()) { f.delete(); moved++; return@forEach }
            onProgress("Переношу ${f.name}…")
            val tmp = File(dst, f.name + ".part")
            f.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
            if (tmp.length() == f.length() && tmp.renameTo(target)) { f.delete(); moved++ } else tmp.delete()
        }
        moved
    }

    fun unload() { scope.launch { loadLock.withLock { unloadLocked() } } }
    private fun unloadLocked() { runCatching { engine?.close() }; engine = null; loadedKey = null }

    private suspend fun ensure(s: Settings, gpu: Boolean = s.useGpu, vision: Boolean = false): Engine = loadLock.withLock {
        val f = find(s.activeModel)
        if (f == null) throw EngineException("Модель на телефоне не выбрана. Открой «Настройки» и скачай Gemma 4.")
        val base = "${f.path}|${s.contextTokens}|$gpu"
        // Движок с «глазами» годится и для текста — перезагружаем только когда нужна картинка
        engine?.takeIf { loadedKey == "$base|v" || (!vision && loadedKey == base) }?.let { return it }
        val key = if (vision) "$base|v" else base
        withContext(Dispatchers.IO) {
            unloadLocked()
            val backend: Backend = if (gpu) Backend.GPU() else Backend.CPU()
            if (vision) {
                val e = runCatching {
                    Engine(EngineConfig(f.absolutePath, backend, Backend.GPU(), null, s.contextTokens, 1, ctx.cacheDir.absolutePath)).also { it.initialize() }
                }.recoverCatching {
                    Engine(EngineConfig(f.absolutePath, backend, Backend.CPU(), null, s.contextTokens, 1, ctx.cacheDir.absolutePath)).also { it.initialize() }
                }.getOrElse { throw EngineException("Эта модель не умеет смотреть на фото (${it.message}). Фото понимают Gemma 4 E2B и E4B.") }
                return@withContext e.also { engine = it; loadedKey = key }
            }
            val e = Engine(EngineConfig(f.absolutePath, backend, null, null, s.contextTokens, null, ctx.cacheDir.absolutePath))
            try { e.initialize() } catch (t: Throwable) {
                runCatching { e.close() }
                throw EngineException(if (gpu) "Модель не запустилась на GPU (${t.message}). Выключи «Ускорение GPU»." else "Модель не запустилась: ${t.message}")
            }
            e.also { engine = it; loadedKey = key }
        }
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch { delay(120_000); if (!genLock.isLocked) loadLock.withLock { unloadLocked() } }
    }

    fun generate(s: Settings, r: GenRequest, gpu: Boolean = s.useGpu): Flow<String> = flow {
        genLock.withLock {
            unloadJob?.cancel()
            val e = ensure(s, gpu, vision = r.image != null)
            val hist = r.folded()
            val initial = hist.dropLast(1).map { if (it.user) Message.user(it.text) else Message.model(it.text) }
            val last = hist.lastOrNull()?.text ?: "Привет"
            val cfg = ConversationConfig(Contents.of(r.system), initial, emptyList(),
                SamplerConfig(40, r.topP.toDouble(), r.temperature.toDouble(), 0))
            val conv = e.createConversation(cfg)
            try {
                val limit = r.maxOut * 5
                var total = 0
                val chunks = callbackFlow {
                    val cb = object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val t = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                            if (t.isNotEmpty()) trySend(t)
                        }
                        override fun onDone() { close() }
                        override fun onError(throwable: Throwable) { close(EngineException("Ошибка модели: ${throwable.message}")) }
                    }
                    if (r.image != null) conv.sendMessageAsync(Contents.of(Content.ImageBytes(r.image), Content.Text(last)), cb, emptyMap(), RepetitionPenaltyConfig(r.rep))
                    else conv.sendMessageAsync(last, cb, emptyMap(), RepetitionPenaltyConfig(r.rep))
                    awaitClose { runCatching { conv.cancelProcess() } }
                }
                chunks.collect { t ->
                    emit(t); total += t.length
                    if (total > limit) { runCatching { conv.cancelProcess() }; return@collect }
                }
            } finally {
                runCatching { conv.close() }
                scheduleUnload()
            }
        }
    }.flowOn(Dispatchers.IO)
}

/** Облачные модели по API-ключу: OpenAI-совместимые (OpenRouter, Gemini, OpenAI, свой URL) и Claude. */
class ApiEngine(private val repo: Repo? = null) {
    private fun open(url: String, s: Settings, key: String, method: String = "POST"): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000; c.readTimeout = 120_000
        c.setRequestProperty("Content-Type", "application/json")
        if (s.provider == Provider.CLAUDE) {
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
        } else c.setRequestProperty("Authorization", "Bearer $key")
        if (s.provider == Provider.OPENROUTER) {
            c.setRequestProperty("HTTP-Referer", "https://persona.app")
            c.setRequestProperty("X-Title", "Persona")
        }
        return c
    }

    /** Превращает ответ сервера с ошибкой в понятное сообщение: что случилось и что делать. */
    private fun failure(c: HttpURLConnection, s: Settings): EngineException =
        failureOf(c.responseCode, runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty(), s)

    private fun failureOf(code: Int, body: String, s: Settings): EngineException {
        val msg = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            when (val e = o["error"]) {
                is JsonObject -> (e["message"] as? JsonPrimitive)?.content
                is JsonPrimitive -> e.content
                else -> (o["message"] as? JsonPrimitive)?.content
            }
        }.getOrNull() ?: body.take(200)
        return ApiErrors.classify(code, body, msg, s)
    }

    /** Модели OpenAI с рассуждениями (o1/o3/o4, gpt-5 и новее) не принимают temperature и max_tokens. */
    private fun reasoning(model: String) = Regex("^(openai/)?(o\\d|gpt-([5-9]|\\d\\d))").containsMatchIn(model.lowercase())

    /** Модели, которым мы уже отправляли «лишние» параметры и получили отказ — дальше шлём только самое нужное. */
    private val minimalModels = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Проверяет ключ по-настоящему (для OpenRouter список моделей открыт и без ключа). */
    suspend fun checkKey(s: Settings, key: String) = withContext(Dispatchers.IO) {
        if (s.provider != Provider.OPENROUTER) return@withContext
        val c = open(s.apiBase + "/key", s, key, "GET")
        try { if (c.responseCode !in 200..299) throw failure(c, s) } finally { c.disconnect() }
    }

    /** Остаток кредитов OpenRouter в долларах, если известен. */
    suspend fun balance(s: Settings): Double? = withContext(Dispatchers.IO) {
        if (s.provider != Provider.OPENROUTER || s.apiKeyEnc.isBlank()) return@withContext null
        runCatching {
            val c = open(s.apiBase + "/credits", s, key(s), "GET")
            try {
                if (c.responseCode !in 200..299) return@runCatching null
                val d = json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject["data"] as? JsonObject
                val total = (d?.get("total_credits") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@runCatching null
                val used = (d["total_usage"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                total - used
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** Маленький настоящий запрос: модель жива, ключ рабочий, деньги на счету есть. */
    suspend fun ping(s: Settings, key: String) {
        val r = GenRequest("Отвечай одним словом.", listOf(Turn(true, "Скажи: готово")), 8, 0.2f, 1f, 1f)
        generate(s, r, key, record = false).collect { }
    }

    fun key(s: Settings) = KeyCrypto.decrypt(s.apiKeyEnc)
    fun ready(s: Settings) = s.apiKeyEnc.isNotBlank() && s.apiModel.isNotBlank() && s.apiBase.isNotBlank()

    private fun cacheOn(s: Settings) = s.promptCache && (s.provider == Provider.CLAUDE || (s.provider == Provider.OPENROUTER && s.apiModel.startsWith("anthropic/")))

    private fun body(s: Settings, r: GenRequest, minimal: Boolean, completionTokens: Boolean): JsonObject {
        val claude = s.provider == Provider.CLAUDE
        val marks = cacheOn(s) // метки cache_control для Claude (напрямую и через OpenRouter)
        val turns = r.history.toMutableList()
        if (claude && turns.firstOrNull()?.user == false) turns.add(0, Turn(true, "(Начало ролевой переписки)"))
        if (turns.isEmpty()) turns += Turn(true, "(Продолжай.)")
        val img64 = r.image?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        fun text(t: String, cache: Boolean) = buildJsonObject {
            put("type", "text"); put("text", t)
            if (cache) put("cache_control", buildJsonObject { put("type", "ephemeral") })
        }
        val msgs = buildJsonArray {
            if (!claude) add(buildJsonObject {
                put("role", "system")
                if (marks) put("content", buildJsonArray { add(text(r.system, true)) }) else put("content", r.system)
            })
            turns.forEachIndexed { i, t ->
                val last = i == turns.lastIndex
                add(buildJsonObject {
                    put("role", if (t.user) "user" else "assistant")
                    val tail = if (last && t.user) r.tail else ""
                    val image = img64 != null && last && t.user
                    if (image || (last && marks) || (tail.isNotBlank() && marks)) put("content", buildJsonArray {
                        if (image) {
                            if (claude) add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject { put("type", "base64"); put("media_type", "image/jpeg"); put("data", img64) })
                            }) else add(buildJsonObject {
                                put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$img64") })
                            })
                        }
                        // Метка кэша стоит до изменчивой приписки: всё, что выше, в следующий раз возьмётся из кэша
                        add(text(t.text, marks && last))
                        if (tail.isNotBlank()) add(text(tail, false))
                    }) else put("content", if (tail.isNotBlank()) t.text + "\n\n" + tail else t.text)
                })
            }
        }
        val reason = reasoning(s.apiModel) && s.provider != Provider.OPENROUTER
        return buildJsonObject {
            put("model", s.apiModel)
            put("messages", msgs)
            put("stream", true)
            if (claude) {
                put("max_tokens", r.maxOut)
                if (marks) put("system", buildJsonArray { add(text(r.system, true)) }) else put("system", r.system)
                if (!minimal) put("temperature", r.temperature.coerceIn(0f, 1f))
                return@buildJsonObject
            }
            if (s.provider == Provider.OPENAI || s.provider == Provider.OPENROUTER || s.provider == Provider.DEEPSEEK)
                put("stream_options", buildJsonObject { put("include_usage", true) })
            if (reason || completionTokens) put("max_completion_tokens", if (reason) r.maxOut * 4 + 256 else r.maxOut)
            else put("max_tokens", r.maxOut)
            if (minimal) return@buildJsonObject
            if (s.provider == Provider.OPENAI && s.promptCache) r.cacheKey?.let { put("prompt_cache_key", it) }
            if (reason) {
                // Меньше скрытых «размышлений» — меньше платных токенов
                put("reasoning_effort", "low")
                return@buildJsonObject
            }
            put("temperature", r.temperature)
            put("top_p", r.topP)
            if (s.provider == Provider.OPENROUTER) put("usage", buildJsonObject { put("include", true) })
            when (s.provider) {
                Provider.OPENROUTER -> put("repetition_penalty", r.rep)
                Provider.OPENAI, Provider.CUSTOM, Provider.DEEPSEEK -> put("frequency_penalty", ((r.rep - 1f) * 2f).coerceIn(0f, 2f))
                else -> {}
            }
        }
    }

    fun generate(s: Settings, r: GenRequest, keyOverride: String? = null, record: Boolean = true): Flow<String> = flow {
        val key = keyOverride ?: key(s)
        if (key.isBlank() && s.provider != Provider.CUSTOM) throw EngineException("Не указан API-ключ. Открой «Настройки → Через интернет».")
        if (s.apiModel.isBlank()) throw EngineException("Не выбрана модель API. Открой «Настройки → Через интернет».")
        val claude = s.provider == Provider.CLAUDE
        var minimal = s.apiModel in minimalModels
        var completionTokens = false
        var c: HttpURLConnection
        var attempt = 0
        while (true) {
            c = open(s.apiBase + if (claude) "/messages" else "/chat/completions", s, key)
            c.doOutput = true
            c.outputStream.use { it.write(body(s, r, minimal, completionTokens).toString().toByteArray()) }
            val code = c.responseCode
            if (code in 200..299) break
            val err = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
            c.disconnect()
            val low = err.lowercase()
            val paramProblem = code == 400 && listOf("unsupported", "not supported", "unrecognized", "unknown parameter", "extra inputs", "does not support", "invalid_request_error").any { low.contains(it) } &&
                listOf("temperature", "top_p", "max_tokens", "frequency_penalty", "repetition_penalty", "stream_options", "prompt_cache_key", "reasoning_effort", "cache_control", "parameter").any { low.contains(it) }
            if (paramProblem && attempt < 2) {
                // Модель не приняла часть параметров — пробуем ещё раз с самым простым запросом
                attempt++
                if (low.contains("max_tokens") && low.contains("max_completion_tokens")) completionTokens = true else { minimal = true; minimalModels += s.apiModel }
                continue
            }
            throw failureOf(code, err, s)
        }
        var inTok: Long? = null; var outTok: Long? = null; var cost: Double? = null; var outChars = 0; var cached = 0L
        var claudeIn = 0L
        fun num(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull()
        try {
            c.inputStream.bufferedReader().use { rd ->
                while (true) {
                    val line = rd.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val o = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    o["error"]?.let { throw EngineException("Ошибка API: ${(it as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: it}") }
                    // Учёт токенов: OpenAI-совместимые присылают usage в конце, Claude — в message_start и message_delta
                    fun readUsage(u: JsonObject) {
                        num(u, "prompt_tokens")?.let { inTok = it.toLong() }; num(u, "completion_tokens")?.let { outTok = it.toLong() }
                        num(u, "output_tokens")?.let { outTok = it.toLong() }
                        num(u, "cost")?.let { cost = it }
                        (u["prompt_tokens_details"] as? JsonObject)?.let { d -> num(d, "cached_tokens")?.let { cached = it.toLong() } }
                        num(u, "prompt_cache_hit_tokens")?.let { cached = it.toLong() } // DeepSeek
                        if (claude) {
                            val i = (num(u, "input_tokens") ?: 0.0) + (num(u, "cache_read_input_tokens") ?: 0.0) + (num(u, "cache_creation_input_tokens") ?: 0.0)
                            if (i > 0) { claudeIn = i.toLong(); inTok = claudeIn }
                            num(u, "cache_read_input_tokens")?.let { if (it > 0) cached = it.toLong() }
                        } else num(u, "input_tokens")?.let { inTok = it.toLong() }
                    }
                    (o["usage"] as? JsonObject)?.let { readUsage(it) }
                    ((o["message"] as? JsonObject)?.get("usage") as? JsonObject)?.let { readUsage(it) }
                    val piece = if (claude) {
                        if (o["type"]?.jsonPrimitive?.content == "content_block_delta")
                            (o["delta"] as? JsonObject)?.get("text")?.jsonPrimitive?.content else null
                    } else {
                        ((o["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.let { ch ->
                            (ch["delta"] as? JsonObject)?.get("content")?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                        }
                    }
                    if (!piece.isNullOrEmpty()) { outChars += piece.length; emit(piece) }
                }
            }
        } finally {
            c.disconnect()
            if (record && (outChars > 0 || inTok != null)) runCatching {
                val est = Prompt.estimateTokens(r.system) + r.history.sumOf { Prompt.estimateTokens(it.text) } + Prompt.estimateTokens(r.tail)
                repo?.addUsage(s.apiModel, inTok ?: est.toLong(), outTok ?: (outChars / 3L), cost, cached)
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Рисует картинку по описанию тем же ключом. Возвращает байты PNG/JPEG. */
    suspend fun generateImage(s: Settings, prompt: String): ByteArray = withContext(Dispatchers.IO) {
        val key = key(s)
        if (key.isBlank()) throw EngineException("Для картинок нужен API-ключ: «Настройки → Через интернет».")
        if (s.provider == Provider.CLAUDE) throw EngineException("Claude не рисует картинки. Выбери OpenRouter, OpenAI или Gemini.")
        if (s.imageModel.isBlank()) throw EngineException("Не указана модель для картинок: «Настройки → Через интернет → Картинки».")
        val openrouter = s.provider == Provider.OPENROUTER
        val body = if (openrouter) buildJsonObject {
            put("model", s.imageModel)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
            put("modalities", buildJsonArray { add(JsonPrimitive("image")); add(JsonPrimitive("text")) })
        } else buildJsonObject {
            put("model", s.imageModel); put("prompt", prompt); put("n", 1)
            if (s.imageModel.startsWith("dall-e") || s.provider == Provider.GEMINI) put("response_format", "b64_json")
        }
        val c = open(s.apiBase + if (openrouter) "/chat/completions" else "/images/generations", s, key)
        c.readTimeout = 180_000
        try {
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) throw failure(c, s)
            val o = json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject
            val url: String? = if (openrouter) {
                val msg = ((o["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
                ((msg?.get("images") as? JsonArray)?.firstOrNull() as? JsonObject)?.let { im ->
                    ((im["image_url"] as? JsonObject)?.get("url") as? JsonPrimitive)?.content
                }
            } else {
                val d = (o["data"] as? JsonArray)?.firstOrNull() as? JsonObject
                (d?.get("b64_json") as? JsonPrimitive)?.content?.let { "data:image/png;base64,$it" } ?: (d?.get("url") as? JsonPrimitive)?.content
            }
            when {
                url == null -> throw EngineException("Модель не вернула картинку. Проверь, что «${s.imageModel}» умеет рисовать.")
                url.startsWith("data:") -> Base64.decode(url.substringAfter(","), Base64.DEFAULT)
                else -> URL(url).openStream().use { it.readBytes() }
            }
        } finally { c.disconnect() }
    }

    suspend fun listModels(s: Settings, keyOverride: String? = null): List<String> = withContext(Dispatchers.IO) {
        val key = keyOverride ?: key(s)
        if (key.isBlank()) throw EngineException("Сначала введи ключ.")
        val c = open(s.apiBase + "/models", s, key, "GET")
        try {
            if (c.responseCode !in 200..299) throw failure(c, s)
            val o = json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject
            val arr = (o["data"] ?: o["models"])?.jsonArray ?: JsonArray(emptyList())
            // OpenRouter присылает цены — запоминаем их (за токен → за миллион)
            arr.forEach { m ->
                val mo = m as? JsonObject ?: return@forEach
                val id = (mo["id"] as? JsonPrimitive)?.content ?: return@forEach
                val pr = mo["pricing"] as? JsonObject ?: return@forEach
                fun per(k: String) = (pr[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.times(1_000_000)
                val pin = per("prompt") ?: return@forEach
                KnownModels.live[id] = Price(pin, per("completion") ?: 0.0, per("input_cache_read"))
            }
            arr.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content?.removePrefix("models/") }.sorted()
        } finally { c.disconnect() }
    }
}

/** Выбирает, кто отвечает: модель на телефоне или API. Возвращает подпись движка и поток кусочков текста. */
class Engines(ctx: Context, private val repo: Repo) {
    val local = LocalEngine(ctx)
    val api = ApiEngine(repo)

    fun pick(s: Settings): Mode = when (s.mode) {
        Mode.LOCAL -> Mode.LOCAL
        Mode.API -> Mode.API
        Mode.AUTO -> if (local.hasModel(s)) Mode.LOCAL else if (api.ready(s)) Mode.API else Mode.LOCAL
    }

    fun label(s: Settings, m: Mode = pick(s)) = if (m == Mode.LOCAL) ModelCatalog.title(s.activeModel) + if (s.useGpu) " · GPU" else " · CPU"
        else s.apiModel.substringAfterLast('/')

    fun isLocal(s: Settings = repo.settings.value) = pick(s) == Mode.LOCAL

    fun generate(r: GenRequest, s: Settings = repo.settings.value): Pair<String, Flow<String>> {
        val m = pick(s)
        if (m == Mode.LOCAL && !local.hasModel(s)) {
            throw EngineException("Нет модели: скачай Gemma 4 или введи API-ключ во вкладке «Модель».")
        }
        val f = if (m == Mode.LOCAL) local.generate(s, r) else api.generate(s, r)
        return label(s, m) to f
    }
}
