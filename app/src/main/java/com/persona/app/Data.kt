package com.persona.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(12)

@Serializable
data class Voice(val pitch: Float = 1f, val rate: Float = 1f, val autoRead: Boolean = false)

@Serializable
data class Character(
    val id: String = newId(),
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val greetings: List<String> = listOf(""),
    val aiGreeting: Boolean = false,
    val definition: String = "",
    val soft: Float = 0.5f,
    val playful: Float = 0.5f,
    val emotional: Float = 0.5f,
    val relation: String = "",
    val writesFirst: Boolean = false,
    val tags: List<String> = emptyList(),
    val lorebookIds: List<String> = emptyList(),
    val voice: Voice? = null,
    val avatar: String? = null,
    val shareable: Boolean = false,
    val preset: Preset? = null,
    val moodEnabled: Boolean = false,
    val diaryEnabled: Boolean = false,
    val routineEnabled: Boolean = false,
    val routine: String = "",
    val background: String? = null,
    /** Аватарки-эмоции: эмоция → файл картинки. */
    val emotions: Map<String, String> = emptyMap(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val created: Long = System.currentTimeMillis(),
)

/** Твой герой: от его лица ты пишешь в чате. */
@Serializable
data class Persona(val id: String = newId(), val name: String = "", val about: String = "")

@Serializable enum class SceneMode(val title: String) { HANGOUT("Общий чат"), ADVENTURE("Приключение") }

@Serializable enum class TurnMode(val title: String) { MENTION("По обращению"), ROUND("По очереди"), RANDOM("Случайно") }

@Serializable
data class HeroStats(val hp: Int = 20, val maxHp: Int = 20, val gold: Int = 0, val inventory: String = "", val notes: String = "")

/** Групповая сцена: мир, несколько персонажей, рассказчик, кубики. */
@Serializable
data class Scene(
    val id: String = newId(),
    val name: String = "",
    val world: String = "",
    val charIds: List<String> = emptyList(),
    val mode: SceneMode = SceneMode.ADVENTURE,
    val narrator: Boolean = true,
    val turnMode: TurnMode = TurnMode.MENTION,
    val dice: Boolean = true,
    val randomEvents: Boolean = false,
    val hero: HeroStats = HeroStats(),
    val opening: String = "",
    val lorebookIds: List<String> = emptyList(),
    val background: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val created: Long = System.currentTimeMillis(),
)

@Serializable
data class DiaryEntry(val id: String = newId(), val text: String, val created: Long = System.currentTimeMillis())

const val NARRATOR = "narrator"
const val DICE = "dice"

@Serializable
data class LoreEntry(val id: String = newId(), val keys: List<String> = emptyList(), val content: String = "", val enabled: Boolean = true)

@Serializable
data class Lorebook(val id: String = newId(), val name: String = "", val entries: List<LoreEntry> = emptyList())

@Serializable
data class Msg(
    val id: String = newId(),
    val fromUser: Boolean,
    val variants: List<String>,
    val sel: Int = 0,
    val info: String = "",
    val time: Long = System.currentTimeMillis(),
    /** В сцене: id персонажа, NARRATOR или DICE. В обычном чате — null. */
    val speaker: String? = null,
    /** Файл картинки в папке images: фото от тебя или нарисованная сцена. */
    val image: String? = null,
    /** "picture" — иллюстрация, модель её не видит. */
    val kind: String? = null,
    /** Твоя реакция на сообщение персонажа (эмодзи). */
    val reaction: String? = null,
) {
    val text: String get() = variants.getOrElse(sel) { variants.lastOrNull() ?: "" }
}

@Serializable
data class Fact(val id: String = newId(), val text: String, val created: Long = System.currentTimeMillis())

@Serializable
data class Chat(
    val id: String = newId(),
    val charId: String,
    val title: String = "",
    val messages: List<Msg> = emptyList(),
    val summary: String = "",
    val summarizedUpTo: Int = 0,
    val facts: List<Fact> = emptyList(),
    val updated: Long = System.currentTimeMillis(),
    val personaId: String? = null,
    val mood: String = "",
    val affinity: Int = 50,
    val diary: List<DiaryEntry> = emptyList(),
    val diaryAt: Int = 0,
    val sceneId: String? = null,
    val hero: HeroStats? = null,
    val authorNote: String = "",
    val moments: List<Moment> = emptyList(),
    /** Инкогнито: не сохраняется на диск, без памяти и дневника, исчезает при закрытии приложения. */
    val incognito: Boolean = false,
)

@Serializable
data class Moment(val id: String = newId(), val text: String, val created: Long = System.currentTimeMillis())

const val PICTURE = "picture"

@Serializable enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), DARK("Тёмная"), LIGHT("Светлая"), AMOLED("Чёрная") }
@Serializable enum class BubbleStyle(val title: String) { ROUND("Скруглённые"), COMPACT("Компактные"), FLAT("Без пузырей") }

/** Внешний вид приложения. accent = -1 — цвета обоев (Material You). */
@Serializable
data class Appearance(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0,
    val textScale: Float = 1f,
    val bubbles: BubbleStyle = BubbleStyle.ROUND,
    val bubbleAlpha: Float = 0.9f,
    val chatAvatars: Boolean = true,
    val squareAvatars: Boolean = false,
    val compact: Boolean = false,
    val serif: Boolean = false,
)

@Serializable enum class BackupFreq(val title: String, val ms: Long) {
    OFF("Выкл", 0), DAILY("Каждый день", 86_400_000L), WEEKLY("Раз в неделю", 7 * 86_400_000L), MONTHLY("Раз в месяц", 30 * 86_400_000L)
}

@Serializable
data class AutoBackupCfg(val freq: BackupFreq = BackupFreq.OFF, val keep: Int = 5, val folderUri: String? = null, val folderName: String? = null,
    val last: Long = 0, val lastError: String? = null)

@Serializable
data class UpdateInfo(val repo: String = DEFAULT_REPO, val lastCheck: Long = 0, val latest: String? = null, val url: String? = null, val notes: String? = null,
    /** Скачивать и ставить обновления самостоятельно. */
    val auto: Boolean = true, val page: String? = null)

const val DEFAULT_REPO = "Galis228/Persona"

@Serializable
data class Price(val inPerM: Double = 0.0, val outPerM: Double = 0.0)

/** Расход API за день по одной модели. */
@Serializable
data class UsageDay(val date: String, val model: String, val inTok: Long = 0, val outTok: Long = 0, val cost: Double = 0.0, val costKnown: Boolean = false, val requests: Int = 0)

@Serializable enum class Mode { AUTO, LOCAL, API }
@Serializable enum class Preset(val title: String, val temp: Float, val topP: Float, val rep: Float) {
    // Штраф за повторы мягкий: в русском у слова много форм с общим началом, сильный штраф ломает окончания
    STRICT("Строгий", 0.6f, 0.9f, 1.0f),
    BALANCE("Баланс", 0.85f, 0.95f, 1.03f),
    FANTASY("Фантазия", 1.1f, 0.98f, 1.06f),
}
@Serializable enum class Length(val title: String, val maxOut: Int, val hint: String) {
    REPLY("Реплика", 160, "Отвечай коротко: 1–3 предложения."),
    PARAGRAPH("Абзац", 400, "Отвечай одним-двумя абзацами."),
    SCENE("Сцена", 900, "Отвечай развёрнуто: подробная сцена из нескольких абзацев."),
}
@Serializable enum class Provider(val title: String, val baseUrl: String, val keyHint: String) {
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "sk-or-…"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "AIza…"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "sk-…"),
    CLAUDE("Claude", "https://api.anthropic.com/v1", "sk-ant-…"),
    CUSTOM("Свой URL", "", "ключ"),
}

const val DEFAULT_TEMPLATE = "Ты — {{персонаж}}. Это ролевая переписка, твой собеседник — {{я}}. Всегда оставайся в образе: говори и действуй только как {{персонаж}}, не выходи из роли и не упоминай, что ты ИИ или языковая модель.\n\n{{описание}}\n\n{{память}}\n\n{{лор}}\n\n{{правила}}"

/** Прежние стандартные шаблоны — при обновлении меняются на новый сами. */
val OLD_TEMPLATES = setOf(
    "Ты — {{char}}. Это ролевая переписка, твой собеседник — {{user}}. Всегда оставайся в образе: говори и действуй только как {{char}}, не выходи из роли и не упоминай, что ты ИИ или языковая модель.\n\n{{card}}\n\n{{memory}}\n\n{{lore}}\n\n{{rules}}",
    "Ты играешь роль {{char}} в ролевой переписке с {{user}}. Всегда оставайся в образе {{char}}: говори и действуй только от лица {{char}}, не выходи из роли и не упоминай, что ты ИИ или языковая модель.\n\n{{card}}\n\n{{memory}}\n\n{{lore}}\n\n{{rules}}",
)

/** Метки шаблона: русская, английская (как в SillyTavern) и пояснение. */
data class PromptTag(val ru: String, val en: String, val title: String, val hint: String)
val PROMPT_TAGS = listOf(
    PromptTag("{{персонаж}}", "{{char}}", "Имя персонажа", "Например: Лира"),
    PromptTag("{{я}}", "{{user}}", "Твоё имя", "Имя твоей персоны в этом чате"),
    PromptTag("{{описание}}", "{{card}}", "Всё о персонаже", "Слоган, описание, характер, отношения, примеры речи, настроение и распорядок"),
    PromptTag("{{память}}", "{{memory}}", "Память", "Краткое содержание прошлого и факты о тебе"),
    PromptTag("{{лор}}", "{{lore}}", "Лорбук", "Записи о мире — только те, чьи ключевые слова звучат в чате"),
    PromptTag("{{правила}}", "{{rules}}", "Правила", "Язык ответа, «не писать за меня», *действия*, длина ответа"),
)

@Serializable
data class Settings(
    val userName: String = "",
    val userAbout: String = "",
    val personas: List<Persona> = emptyList(),
    val defaultPersonaId: String? = null,
    val mode: Mode = Mode.AUTO,
    val activeModel: String = "",
    val useGpu: Boolean = true,
    val contextTokens: Int = 4096,
    val provider: Provider = Provider.OPENROUTER,
    val apiKeyEnc: String = "",
    val baseUrl: String = "",
    val apiModel: String = "",
    val preset: Preset = Preset.BALANCE,
    val temperature: Float = 0.85f,
    val topP: Float = 0.95f,
    val repPenalty: Float = 1.03f,
    /** Вычитка: после ответа модель исправляет орфографию и грамматику. */
    val proofread: Boolean = false,
    val length: Length = Length.PARAGRAPH,
    val noImpersonation: Boolean = true,
    val italicActions: Boolean = true,
    val summarize: Boolean = true,
    val summarizeEvery: Int = 30,
    val systemTemplate: String = DEFAULT_TEMPLATE,
    val downloads: Map<String, Long> = emptyMap(),
    val notifyAsked: Boolean = false,
    val imageModel: String = "",
    val lockEnabled: Boolean = false,
    val look: Appearance = Appearance(),
    val autoBackup: AutoBackupCfg = AutoBackupCfg(),
    val updates: UpdateInfo = UpdateInfo(),
    val onboarded: Boolean = false,
    /** Цены за 1 млн токенов по моделям API: вход и выход, в долларах. */
    val prices: Map<String, Price> = emptyMap(),
) {
    val user: String get() = userName.ifBlank { "Пользователь" }
    fun persona(id: String?): Persona? = personas.firstOrNull { it.id == (id ?: defaultPersonaId) }
    /** Настройки с подставленной персоной чата: {{user}} = её имя. */
    fun forChat(chat: Chat?): Settings = persona(chat?.personaId)?.let { copy(userName = it.name, userAbout = it.about) } ?: this
    val apiBase: String get() = (if (provider == Provider.CUSTOM) baseUrl else provider.baseUrl).trimEnd('/')
}

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false; coerceInputValues = true }

/** Всё хранится JSON-файлами во внутренней памяти приложения. */
class Repo(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val io = Mutex()
    private val dir = ctx.filesDir
    private val chatsDir = File(dir, "chats").apply { mkdirs() }
    val avatarsDir = File(dir, "avatars").apply { mkdirs() }
    val imagesDir = File(dir, "images").apply { mkdirs() }
    fun imageFile(name: String?): File? = name?.let { File(imagesDir, it) }?.takeIf { it.exists() }

    /** Сохраняет фото уменьшенным до 1024 px по длинной стороне. */
    fun saveImage(uri: Uri): String? = runCatching {
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) } ?: return null
        saveImageBitmap(bmp)
    }.onFailure { ErrorLog.add("image", it) }.getOrNull()

    fun saveImageBitmap(bmp: Bitmap): String {
        val k = 1024f / maxOf(bmp.width, bmp.height)
        val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val name = "${newId()}.jpg"
        File(imagesDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return name
    }

    private val _chars = MutableStateFlow<List<Character>>(emptyList())
    private val _books = MutableStateFlow<List<Lorebook>>(emptyList())
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    private val _settings = MutableStateFlow(Settings())
    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    val scenes: StateFlow<List<Scene>> = _scenes
    val chars: StateFlow<List<Character>> = _chars
    val books: StateFlow<List<Lorebook>> = _books
    val chats: StateFlow<List<Chat>> = _chats
    val settings: StateFlow<Settings> = _settings

    private val _usage = MutableStateFlow<List<UsageDay>>(emptyList())
    val usage: StateFlow<List<UsageDay>> = _usage

    init { load() }

    private fun load() {
        _usage.value = read("usage.json") ?: emptyList()
        _chars.value = read("characters.json") ?: emptyList()
        _books.value = read("lorebooks.json") ?: emptyList()
        _scenes.value = read("scenes.json") ?: emptyList()
        _settings.value = (read<Settings>("settings.json") ?: Settings()).let { if (it.systemTemplate in OLD_TEMPLATES) it.copy(systemTemplate = DEFAULT_TEMPLATE) else it }
            .let { st -> if (st.updates.repo.isBlank()) st.copy(updates = st.updates.copy(repo = DEFAULT_REPO)) else st }
            .let { st ->
                // Прежние пресеты со слишком сильным штрафом за повторы — переводим на новые
                val old = mapOf(Triple(0.6f, 0.9f, 1.05f) to Preset.STRICT, Triple(0.9f, 0.95f, 1.1f) to Preset.BALANCE, Triple(1.15f, 0.98f, 1.15f) to Preset.FANTASY)
                old[Triple(st.temperature, st.topP, st.repPenalty)]?.let { p -> st.copy(temperature = p.temp, topP = p.topP, repPenalty = p.rep) } ?: st
            }
        _chats.value = chatsDir.listFiles()?.mapNotNull { f ->
            runCatching { json.decodeFromString<Chat>(f.readText()) }.getOrNull()
        }?.sortedByDescending { it.updated } ?: emptyList()
    }

    private inline fun <reified T> read(name: String): T? =
        runCatching { json.decodeFromString<T>(File(dir, name).readText()) }.getOrNull()

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.path + ".tmp"); tmp.writeText(text); tmp.renameTo(f)
    }

    private fun persist(block: () -> Unit) { scope.launch { io.withLock { runCatching(block).onFailure { ErrorLog.add("save", it) } } } }

    fun char(id: String) = _chars.value.firstOrNull { it.id == id }
    fun chat(id: String) = _chats.value.firstOrNull { it.id == id }

    fun saveChar(c: Character) {
        _chars.update { l -> if (l.any { it.id == c.id }) l.map { if (it.id == c.id) c else it } else listOf(c) + l }
        val snap = _chars.value
        persist { writeAtomic(File(dir, "characters.json"), json.encodeToString(snap)) }
    }

    fun deleteChar(id: String) {
        _chars.update { l -> l.filterNot { it.id == id } }
        val gone = _chats.value.filter { it.charId == id }
        _chats.update { l -> l.filterNot { it.charId == id } }
        val snap = _chars.value
        persist {
            writeAtomic(File(dir, "characters.json"), json.encodeToString(snap))
            gone.forEach { File(chatsDir, "${it.id}.json").delete() }
        }
    }

    /** Отмена удаления: возвращает персонажа или группу вместе с чатами. */
    fun restore(chars: List<Character>, scenes: List<Scene>, chats: List<Chat>) {
        chars.forEach { saveChar(it) }
        scenes.forEach { saveScene(it) }
        chats.forEach { saveChat(it.copy(updated = it.updated)) }
    }

    fun scene(id: String?) = _scenes.value.firstOrNull { it.id == id }

    // ---------- Расходы API ----------
    fun addUsage(model: String, inTok: Long, outTok: Long, reportedCost: Double?) {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val price = _settings.value.prices[model]
        val cost = reportedCost ?: price?.let { (inTok * it.inPerM + outTok * it.outPerM) / 1_000_000.0 }
        _usage.update { l ->
            val cur = l.firstOrNull { it.date == date && it.model == model } ?: UsageDay(date, model)
            val n = cur.copy(inTok = cur.inTok + inTok, outTok = cur.outTok + outTok, cost = cur.cost + (cost ?: 0.0),
                costKnown = cur.costKnown || cost != null, requests = cur.requests + 1)
            (l.filterNot { it.date == date && it.model == model } + n).takeLast(2000)
        }
        val snap = _usage.value
        persist { writeAtomic(File(dir, "usage.json"), json.encodeToString(snap)) }
    }

    fun clearUsage() { _usage.value = emptyList(); persist { File(dir, "usage.json").delete() } }

    // ---------- Резервная копия ----------
    /** Всё в один zip: персонажи, чаты, группы, лорбуки, настройки (без API-ключа), аватарки и картинки. */
    fun exportBackup(out: java.io.OutputStream) {
        java.util.zip.ZipOutputStream(out.buffered()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("persona.json", """{"app":"persona","format":1,"created":${System.currentTimeMillis()}}""".toByteArray())
            put("characters.json", json.encodeToString(_chars.value).toByteArray())
            put("lorebooks.json", json.encodeToString(_books.value).toByteArray())
            put("scenes.json", json.encodeToString(_scenes.value).toByteArray())
            put("settings.json", json.encodeToString(_settings.value.copy(apiKeyEnc = "", downloads = emptyMap())).toByteArray())
            _chats.value.filterNot { it.incognito }.forEach { put("chats/${it.id}.json", json.encodeToString(it).toByteArray()) }
            avatarsDir.listFiles()?.forEach { put("avatars/${it.name}", it.readBytes()) }
            imagesDir.listFiles()?.forEach { put("images/${it.name}", it.readBytes()) }
        }
    }

    /** Восстанавливает копию поверх текущих данных: совпадающие по id заменяются, остальное остаётся. Возвращает число персонажей и чатов. */
    fun importBackup(inp: java.io.InputStream): Pair<Int, Int> {
        var chars = emptyList<Character>(); var books = emptyList<Lorebook>(); var scenes = emptyList<Scene>(); var st: Settings? = null
        val chats = mutableListOf<Chat>(); var ok = false
        java.util.zip.ZipInputStream(inp.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name
                if (name.contains("..")) continue
                val bytes = z.readBytes()
                when {
                    name == "persona.json" -> ok = true
                    name == "characters.json" -> chars = json.decodeFromString(String(bytes))
                    name == "lorebooks.json" -> books = json.decodeFromString(String(bytes))
                    name == "scenes.json" -> scenes = json.decodeFromString(String(bytes))
                    name == "settings.json" -> st = json.decodeFromString<Settings>(String(bytes))
                    name.startsWith("chats/") -> runCatching { chats += json.decodeFromString<Chat>(String(bytes)) }
                    name.startsWith("avatars/") -> File(avatarsDir, name.substringAfter('/')).writeBytes(bytes)
                    name.startsWith("images/") -> File(imagesDir, name.substringAfter('/')).writeBytes(bytes)
                }
            }
        }
        if (!ok) throw IllegalArgumentException("Это не резервная копия Персоны")
        fun <T> merge(cur: List<T>, add: List<T>, id: (T) -> String) = add + cur.filter { c -> add.none { id(it) == id(c) } }
        writeAtomic(File(dir, "characters.json"), json.encodeToString(merge(_chars.value, chars) { it.id }))
        writeAtomic(File(dir, "lorebooks.json"), json.encodeToString(merge(_books.value, books) { it.id }))
        writeAtomic(File(dir, "scenes.json"), json.encodeToString(merge(_scenes.value, scenes) { it.id }))
        chats.forEach { writeAtomic(File(chatsDir, "${it.id}.json"), json.encodeToString(it)) }
        st?.let { b ->
            val cur = _settings.value
            val merged = b.copy(apiKeyEnc = cur.apiKeyEnc, activeModel = cur.activeModel, downloads = cur.downloads, useGpu = cur.useGpu,
                personas = merge(cur.personas, b.personas) { it.id })
            writeAtomic(File(dir, "settings.json"), json.encodeToString(merged))
        }
        load()
        return chars.size to chats.size
    }

    fun saveScene(sc: Scene) {
        _scenes.update { l -> if (l.any { it.id == sc.id }) l.map { if (it.id == sc.id) sc else it } else listOf(sc) + l }
        val snap = _scenes.value
        persist { writeAtomic(File(dir, "scenes.json"), json.encodeToString(snap)) }
    }

    fun deleteScene(id: String) {
        _scenes.update { l -> l.filterNot { it.id == id } }
        val gone = _chats.value.filter { it.sceneId == id }
        _chats.update { l -> l.filterNot { it.sceneId == id } }
        val snap = _scenes.value
        persist { writeAtomic(File(dir, "scenes.json"), json.encodeToString(snap)); gone.forEach { File(chatsDir, "${it.id}.json").delete() } }
    }

    fun saveBook(b: Lorebook) {
        _books.update { l -> if (l.any { it.id == b.id }) l.map { if (it.id == b.id) b else it } else l + b }
        val snap = _books.value
        persist { writeAtomic(File(dir, "lorebooks.json"), json.encodeToString(snap)) }
    }

    fun deleteBook(id: String) {
        _books.update { l -> l.filterNot { it.id == id } }
        val snap = _books.value
        persist { writeAtomic(File(dir, "lorebooks.json"), json.encodeToString(snap)) }
    }

    /** [persistNow] = false — только в памяти (во время печати ответа), на диск попадёт при следующем сохранении. */
    fun saveChat(c: Chat, persistNow: Boolean = true) {
        val u = c.copy(updated = System.currentTimeMillis())
        _chats.update { l -> listOf(u) + l.filterNot { it.id == c.id } }
        if (persistNow && !u.incognito) persist { writeAtomic(File(chatsDir, "${u.id}.json"), json.encodeToString(u)); runCatching { ChatWidget.refresh(ctx) } }
    }

    fun deleteChat(id: String) {
        _chats.update { l -> l.filterNot { it.id == id } }
        persist { File(chatsDir, "$id.json").delete() }
    }

    fun updateChat(id: String, persistNow: Boolean = true, f: (Chat) -> Chat) {
        val c = chat(id) ?: return
        saveChat(f(c), persistNow)
    }

    fun settings(f: (Settings) -> Settings) {
        _settings.update(f)
        val snap = _settings.value
        persist { writeAtomic(File(dir, "settings.json"), json.encodeToString(snap)) }
    }

    fun chatsOf(charId: String) = _chats.value.filter { it.charId == charId && it.sceneId == null && !it.incognito }.sortedByDescending { it.updated }

    /** Сохраняет картинку как квадратный JPEG 512×512. */
    fun saveAvatar(charId: String, uri: Uri): String? = runCatching {
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) } ?: return null
        saveAvatarBitmap(charId, bmp)
    }.onFailure { ErrorLog.add("avatar", it) }.getOrNull()

    fun saveAvatarBitmap(charId: String, bmp: Bitmap): String {
        val side = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(sq, 512, 512, true)
        val name = "$charId-${System.currentTimeMillis() % 100000}.jpg"
        avatarsDir.listFiles()?.filter { it.name.startsWith("$charId-") }?.forEach { it.delete() }
        File(avatarsDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return name
    }

    fun avatarFile(name: String?): File? = name?.let { n -> File(avatarsDir, n).takeIf { it.exists() } ?: File(imagesDir, n).takeIf { it.exists() } }
}

/** Последние ошибки. Пишутся в файл, чтобы отчёт пережил падение приложения. */
object ErrorLog {
    private val items = ArrayDeque<String>()
    private var file: File? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "errors.log")
        runCatching { file?.takeIf { it.exists() }?.readText()?.split("\n\n")?.filter { it.isNotBlank() }?.takeLast(40)?.forEach { items.addLast(it) } }
    }

    fun add(where: String, t: Throwable) = synchronized(items) {
        items.addLast("[${java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $where: ${t::class.simpleName}: ${t.message}\n" +
            t.stackTrace.take(8).joinToString("\n") { "    at $it" } + (t.cause?.let { "\n  причина: ${it::class.simpleName}: ${it.message}" } ?: ""))
        while (items.size > 40) items.removeFirst()
        runCatching { file?.writeText(items.joinToString("\n\n")) }
    }
    fun text(): String = synchronized(items) { items.joinToString("\n\n").ifBlank { "Ошибок нет." } }
    fun hasCrash(): Boolean = synchronized(items) { items.any { it.contains("] crash:") } }
    fun clear() = synchronized(items) { items.clear(); runCatching { file?.delete() } }
}


/** Шина «Отменить»: экран показывает подсказку с кнопкой, по нажатию выполняется действие. */
object UndoBus {
    val flow = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, () -> Unit>>(extraBufferCapacity = 4)
    fun post(text: String, undo: () -> Unit) { flow.tryEmit(text to undo) }
}
