package com.persona.app

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.fold
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.DataInputStream
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var repo: Repo
    lateinit var engines: Engines
    lateinit var voice: VoicePlayer
    val runner by lazy { ChatRunner(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ErrorLog.init(this)
        repo = Repo(this)
        engines = Engines(this, repo)
        voice = VoicePlayer(this)
        Thread.setDefaultUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler().let { prev ->
            Thread.UncaughtExceptionHandler { t, e -> ErrorLog.add("crash", e); prev?.uncaughtException(t, e) }
        })
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("messages", "Сообщения персонажей", NotificationManager.IMPORTANCE_DEFAULT))
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("writes_first", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WritesFirstWorker>(2, TimeUnit.HOURS).build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("auto_backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(6, TimeUnit.HOURS).build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("updates", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build())
    }

    companion object { lateinit var instance: App }
}

val Context.app get() = applicationContext as App

/** Озвучка реплик голосом персонажа (системный синтез речи). */
class VoicePlayer(ctx: Context) {
    private var ready = false
    private var onDone: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val tts = TextToSpeech(ctx) { ready = it == TextToSpeech.SUCCESS }.apply {
        setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { val cb = onDone; onDone = null; cb?.let { main.post(it) } }
            @Deprecated("") override fun onError(id: String?) { onDone(id) }
        })
    }
    /** [done] вызывается, когда реплика договорена (для звонка). */
    fun speak(text: String, v: Voice, done: (() -> Unit)? = null) {
        if (!ready) { done?.invoke(); return }
        tts.language = if (text.any { it in 'а'..'я' || it in 'А'..'Я' }) Locale("ru") else Locale.getDefault()
        tts.setPitch(v.pitch); tts.setSpeechRate(v.rate)
        onDone = done
        val clean = text.replace(Regex("\\*[^*]*\\*"), " ").trim()
        if (clean.isBlank()) { onDone = null; done?.invoke(); return }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "persona")
    }
    fun stop() { onDone = null; tts.stop() }
}

/** Загрузка моделей системным загрузчиком: качается в фоне и докачивается после обрыва сети. */
object Downloads {
    data class State(val done: Long, val total: Long, val status: Int, val reason: Int)

    fun start(ctx: Context, m: ModelInfo): Long {
        val local = ctx.app.engines.local
        val shared = local.sharedAllowed()
        File(local.saveDir, m.file).delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("Модель ${m.title}")
            .setDescription("Персона")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        if (shared) req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "$SHARED_FOLDER/${m.file}")
        else req.setDestinationUri(Uri.fromFile(File(local.privateDir, m.file)))
        val id = ctx.getSystemService(DownloadManager::class.java).enqueue(req)
        ctx.app.repo.settings { it.copy(downloads = it.downloads + (m.file to id)) }
        return id
    }

    fun state(ctx: Context, id: Long): State? {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return State(
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }

    fun cancel(ctx: Context, file: String) {
        val id = ctx.app.repo.settings.value.downloads[file] ?: return
        ctx.getSystemService(DownloadManager::class.java).remove(id)
        ctx.app.repo.settings { it.copy(downloads = it.downloads - file) }
    }

    fun finish(ctx: Context, file: String) {
        ctx.app.repo.settings { s -> s.copy(downloads = s.downloads - file, activeModel = s.activeModel.ifBlank { file }) }
    }
}

/** Карточки персонажей: экспорт в JSON (формат Character Card V2) и импорт из JSON или PNG SillyTavern. */
object Cards {
    fun export(c: Character): String = buildJsonObject {
        put("spec", "chara_card_v2"); put("spec_version", "2.0")
        putJsonObject("data") {
            put("name", c.name)
            put("description", c.description)
            put("personality", Prompt.traits(c))
            put("scenario", c.relation)
            put("first_mes", c.greetings.firstOrNull().orEmpty())
            put("mes_example", c.definition)
            put("creator_notes", c.tagline)
            putJsonArray("alternate_greetings") { c.greetings.drop(1).forEach { add(JsonPrimitive(it)) } }
            putJsonArray("tags") { c.tags.forEach { add(JsonPrimitive(it)) } }
            putJsonObject("extensions") {
                putJsonObject("persona") {
                    put("tagline", c.tagline); put("soft", c.soft); put("playful", c.playful); put("emotional", c.emotional)
                }
            }
        }
    }.toString()

    private fun s(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    fun parse(text: String): Character {
        val root = json.parseToJsonElement(text).jsonObject
        val d = (root["data"] as? JsonObject) ?: root
        val name = s(d, "name").ifBlank { s(d, "char_name") }
        if (name.isBlank()) throw IllegalArgumentException("В файле нет карточки персонажа")
        val ext = ((d["extensions"] as? JsonObject)?.get("persona") as? JsonObject)
        fun f(k: String) = (ext?.get(k) as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0.5f
        val desc = s(d, "description").ifBlank { s(d, "char_persona") }
        val def = listOf(
            s(d, "personality").takeIf { it.isNotBlank() && ext == null }?.let { "Характер: $it" },
            desc.takeIf { it.length > 500 },
            s(d, "mes_example").ifBlank { s(d, "example_dialogue") }.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("\n\n")
        val greets = listOf(s(d, "first_mes").ifBlank { s(d, "char_greeting") }) +
            ((d["alternate_greetings"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList())
        return Character(
            name = name.take(20),
            tagline = (ext?.get("tagline") as? JsonPrimitive)?.content ?: s(d, "creator_notes").take(50),
            description = desc.take(500),
            greetings = greets.filter { it.isNotBlank() }.take(5).ifEmpty { listOf("") }.map { it.take(4096) },
            definition = def.take(32000),
            relation = s(d, "scenario").ifBlank { s(d, "world_scenario") }.take(1000),
            tags = (d["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.take(10) ?: emptyList(),
            soft = f("soft"), playful = f("playful"), emotional = f("emotional"),
        )
    }

    /** Достаёт JSON карточки из текстовых блоков PNG («chara» или «ccv3», base64). */
    fun fromPng(bytes: ByteArray): String? {
        val inp = DataInputStream(bytes.inputStream())
        val sig = ByteArray(8); inp.readFully(sig)
        var v2: String? = null; var v3: String? = null
        while (inp.available() > 12) {
            val len = inp.readInt()
            val type = ByteArray(4).also { inp.readFully(it) }.toString(Charsets.ISO_8859_1)
            if (len < 0 || len > inp.available()) break
            val data = ByteArray(len).also { inp.readFully(it) }
            inp.readInt()
            if (type == "tEXt") {
                val z = data.indexOf(0)
                if (z > 0) {
                    val k = String(data, 0, z, Charsets.ISO_8859_1).lowercase()
                    val v = String(data, z + 1, data.size - z - 1, Charsets.ISO_8859_1)
                    val decoded = runCatching { String(Base64.decode(v, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                    if (k == "chara") v2 = decoded else if (k == "ccv3") v3 = decoded
                }
            }
            if (type == "IEND") break
        }
        return v3 ?: v2
    }

    /** Лорбук SillyTavern: отдельный World Info ({"entries": {...}}) или character_book внутри карточки. */
    fun parseLorebook(text: String, fallbackName: String = "Лорбук"): Lorebook? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val book = ((root["data"] as? JsonObject)?.get("character_book") as? JsonObject) ?: (root["character_book"] as? JsonObject) ?: root
        val raw = book["entries"] ?: return null
        val list = when (raw) { is JsonArray -> raw.mapNotNull { it as? JsonObject }; is JsonObject -> raw.values.mapNotNull { it as? JsonObject }; else -> emptyList() }
        if (list.isEmpty()) return null
        fun strs(o: JsonObject, k: String) = (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val entries = list.mapNotNull { e ->
            val content = s(e, "content").trim()
            if (content.isBlank()) return@mapNotNull null
            val keys = (strs(e, "keys") + strs(e, "key") + strs(e, "secondary_keys") + strs(e, "keysecondary")).distinct().take(12)
            val disabled = (e["disable"] as? JsonPrimitive)?.content == "true" || (e["enabled"] as? JsonPrimitive)?.content == "false"
            LoreEntry(keys = keys.ifEmpty { listOf(s(e, "comment").ifBlank { s(e, "name") }).filter { it.isNotBlank() } }, content = content.take(4000), enabled = !disabled)
        }
        if (entries.isEmpty()) return null
        return Lorebook(name = s(book, "name").ifBlank { fallbackName }.take(60), entries = entries)
    }

    fun importLorebook(ctx: Context, uri: Uri): Lorebook {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val isPng = bytes.size > 8 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()
        val text = if (isPng) fromPng(bytes) ?: throw IllegalArgumentException("В картинке нет данных") else String(bytes)
        val name = runCatching { ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null } }.getOrNull()
            ?.substringBeforeLast('.') ?: "Лорбук"
        return parseLorebook(text, name) ?: throw IllegalArgumentException("В файле нет записей лорбука")
    }

    fun import(ctx: Context, uri: Uri): Character {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val isPng = bytes.size > 8 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()
        val text = if (isPng) fromPng(bytes) ?: throw IllegalArgumentException("В этой картинке нет карточки персонажа") else String(bytes)
        var c = parse(text)
        // Встроенный в карточку лорбук — отдельным лорбуком, сразу привязанным к персонажу
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()?.let { root ->
            val d = (root["data"] as? JsonObject) ?: root
            if (d["character_book"] != null) parseLorebook(text, "Мир: ${c.name}")?.let { b -> ctx.app.repo.saveBook(b); c = c.copy(lorebookIds = c.lorebookIds + b.id) }
        }
        if (isPng) BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { c = c.copy(avatar = ctx.app.repo.saveAvatarBitmap(c.id, it)) }
        return c
    }

    fun shareFile(ctx: Context, c: Character): File {
        val f = File(ctx.cacheDir, "cards").apply { mkdirs() }.let { File(it, c.name.ifBlank { "character" }.replace(Regex("[^\\p{L}\\p{N}_-]"), "_") + ".json") }
        f.writeText(export(c))
        return f
    }
}

/** «Пишет первым»: раз в пару часов персонаж может сам написать, если ты давно молчишь. */
class WritesFirstWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in 10..21) return Result.success()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val repo = app.repo
        val s = repo.settings.value
        val now = System.currentTimeMillis()
        val candidates = repo.chars.value.filter { it.writesFirst }.mapNotNull { c ->
            val chat = repo.chatsOf(c.id).firstOrNull() ?: return@mapNotNull null
            val last = chat.messages.lastOrNull() ?: return@mapNotNull null
            val silent = now - last.time
            // Не чаще раза в 6 часов и только если последнее слово было не за персонажем «по своей инициативе».
            if (silent < TimeUnit.HOURS.toMillis(6) || (!last.fromUser && last.info.startsWith("сам"))) null else Triple(c, chat, silent)
        }
        val (c, chat, silent) = candidates.randomOrNull() ?: return Result.success()
        return runCatching {
            val cs = s.forChat(chat)
            val req = Prompt.proactive(c, chat, cs, repo.books.value, TimeUnit.MILLISECONDS.toHours(silent))
            val (label, flow) = app.engines.generate(req, cs)
            val text = Prompt.parseMood(flow.fold("") { a, b -> a + b }).first.trim()
            if (text.isBlank()) return Result.success()
            repo.updateChat(chat.id) { it.copy(messages = it.messages + Msg(fromUser = false, variants = listOf(text), info = "сам · $label")) }
            Notifier.post(app, repo.chat(chat.id) ?: chat, c, text)
            Result.success()
        }.getOrElse { ErrorLog.add("writes-first", it); Result.success() }
    }
}

