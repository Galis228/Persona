package com.persona.app

data class Turn(val user: Boolean, val text: String)

data class GenRequest(
    val system: String,
    val history: List<Turn>,
    val maxOut: Int,
    val temperature: Float,
    val topP: Float,
    val rep: Float,
    /** Фото к последней реплике пользователя (JPEG). */
    val image: ByteArray? = null,
)

/** Сборка промптов. Чистые функции — без Android, чтобы их можно было проверить тестом. */
const val MOOD_RULE = "В самом конце ответа на отдельной строке добавь служебную пометку строго в формате [настроение: <одно слово: радостное, игривое, спокойное, грустное, злое, обиженное, удивлённое или смущённое>; отношение: <число от -3 до 3>] — как изменилось отношение к собеседнику после этой реплики. Пометку игрок не увидит."

object Prompt {
    fun fill(t: String, char: String, user: String) = t.replace("{{char}}", char).replace("{{user}}", user)
        .replace("{{Char}}", char).replace("{{User}}", user).replace("{{персонаж}}", char).replace("{{я}}", user)

    fun estimateTokens(text: String): Int = text.length / 3 + 4

    fun traits(c: Character): String {
        val out = mutableListOf<String>()
        fun pick(v: Float, low: String, high: String) {
            when {
                v < 0.2f -> out += "очень $low"
                v < 0.4f -> out += low
                v > 0.8f -> out += "очень $high"
                v > 0.6f -> out += high
            }
        }
        pick(c.soft, "мягкий и деликатный", "дерзкий и прямолинейный")
        pick(c.playful, "серьёзный", "шутливый и игривый")
        pick(c.emotional, "сдержанный", "эмоциональный")
        return out.joinToString(", ")
    }

    fun card(c: Character, user: String): String = buildString {
        append("Персонаж — ${c.name}:")
        if (c.tagline.isNotBlank()) append("\n${c.tagline.trim()}")
        if (c.description.isNotBlank()) append("\n${c.description.trim()}")
        val t = traits(c)
        if (t.isNotBlank()) append("\nЧерты характера: $t.")
        if (c.relation.isNotBlank()) append("\nОтношения с собеседником ($user): ${c.relation.trim()}")
        if (c.definition.isNotBlank()) append("\n\nПодробности и примеры речи:\n${c.definition.trim()}")
    }.let { fill(it, c.name, user) }

    fun memory(chat: Chat, c: Character, user: String): String {
        if (chat.summary.isBlank() && chat.facts.isEmpty()) return ""
        return buildString {
            if (chat.summary.isNotBlank()) append("Что было раньше:\n${chat.summary.trim()}")
            if (chat.facts.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Что ${c.name} помнит о собеседнике ($user):\n")
                append(chat.facts.joinToString("\n") { "- ${it.text}" })
            }
        }
    }

    /** Грубая основа слова, чтобы «гильдия» находилась и в «гильдией», «гильдии». */
    fun stem(k: String): String {
        val w = k.trim().lowercase()
        return if (w.length >= 5 && w.last().isLetter()) w.dropLast(minOf(2, w.length - 4)) else w
    }

    fun lore(c: Character, books: List<Lorebook>, recent: String): String {
        val low = recent.lowercase()
        val hits = books.filter { it.id in c.lorebookIds }.flatMap { it.entries }
            .filter { e -> e.enabled && e.content.isNotBlank() && e.keys.any { k -> k.isNotBlank() && low.contains(stem(k)) } }
            .take(6)
        if (hits.isEmpty()) return ""
        return "Сведения о мире:\n" + hits.joinToString("\n") { "- ${it.content.trim()}" }
    }

    fun rules(s: Settings, c: Character, user: String): String = buildList {
        add("Отвечай на том языке, на котором пишет собеседник.")
        if (s.noImpersonation) add("Никогда не пиши реплики, мысли или действия за собеседника ($user) — только за себя.")
        if (s.italicActions) add("Действия и описания оформляй между звёздочками, например: *улыбается*.")
        add(s.length.hint)
        add("Не повторяй свои прошлые фразы, продвигай сюжет.")
        if (c.moodEnabled) add(MOOD_RULE)
    }.joinToString("\n") { "• $it" }.let { "Правила:\n$it" }

    fun about(s: Settings): String = if (s.userAbout.isBlank()) "" else "О собеседнике (${s.user}): ${s.userAbout.trim()}"

    fun affinityLabel(a: Int) = when {
        a < 20 -> "враждебное"; a < 40 -> "холодное"; a < 60 -> "нейтральное"; a < 80 -> "тёплое"; else -> "очень близкое"
    }

    fun moodState(c: Character, chat: Chat, user: String): String = if (!c.moodEnabled) "" else
        "Текущее настроение ${c.name}: ${chat.mood.ifBlank { "обычное" }}. Отношение к собеседнику ($user): ${chat.affinity}/100 — ${affinityLabel(chat.affinity)}. Веди себя в соответствии с ним."

    private val days = listOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
    fun nowLabel(cal: java.util.Calendar = java.util.Calendar.getInstance()): String =
        days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1] + ", " + String.format(java.util.Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))

    fun routine(c: Character, now: String = nowLabel()): String = if (!c.routineEnabled || c.routine.isBlank()) "" else
        "Распорядок дня ${c.name}:\n${c.routine.trim()}\nСейчас $now. Веди себя так, будто ${c.name} сейчас занят(а) тем, что по распорядку (спит — отвечает сонно и коротко, на работе — отвлекается и т. п.)."

    fun system(c: Character, chat: Chat, s: Settings, books: List<Lorebook>, recent: String): String {
        val user = s.user
        val extra = listOf(about(s), moodState(c, chat, user), routine(c)).filter { it.isNotBlank() }.joinToString("\n\n")
        val body = s.systemTemplate.ifBlank { DEFAULT_TEMPLATE }
        val cardText = card(c, user) + if (extra.isNotBlank()) "\n\n$extra" else ""
        val mem = memory(chat, c, user); val lr = lore(c, books, recent); val rl = rules(s, c, user)
        val body2 = body.replace("{{card}}", cardText).replace("{{описание}}", cardText)
            .replace("{{memory}}", mem).replace("{{память}}", mem)
            .replace("{{lore}}", lr).replace("{{лор}}", lr)
            .replace("{{rules}}", rl).replace("{{правила}}", rl)
        return fill(body2, c.name, user).replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Берёт сообщения с конца, пока влезают в бюджет; склеивает подряд идущие реплики одной стороны. */
    fun history(msgs: List<Msg>, budget: Int): List<Turn> {
        val picked = ArrayList<Turn>()
        var used = 0
        // Реакция на реплику персонажа становится короткой пометкой от пользователя сразу после неё
        val withReactions = msgs.flatMap { m ->
            if (!m.fromUser && m.reaction != null) listOf(m, Msg(fromUser = true, variants = listOf("(реакция на твоё сообщение: ${m.reaction})"))) else listOf(m)
        }
        for (m in withReactions.asReversed()) {
            if (m.kind == PICTURE) continue
            val t = (if (m.image != null) "[фото] " else "") + m.text.trim()
            if (t.isEmpty()) continue
            val cost = estimateTokens(t)
            if (used + cost > budget && picked.isNotEmpty()) break
            picked += Turn(m.fromUser, t)
            used += cost
        }
        picked.reverse()
        val merged = ArrayList<Turn>()
        for (t in picked) {
            val last = merged.lastOrNull()
            if (last != null && last.user == t.user) merged[merged.lastIndex] = last.copy(text = last.text + "\n\n" + t.text)
            else merged += t
        }
        return merged
    }

    fun sampling(s: Settings, c: Character): Triple<Float, Float, Float> {
        val p = c.preset
        return if (p != null) Triple(p.temp, p.topP, p.rep) else Triple(s.temperature, s.topP, s.repPenalty)
    }

    /**
     * Запрос на ответ персонажа. [msgs] — сообщения, которые модель должна видеть (без того, что генерируется).
     * [local] — для модели на телефоне бюджет контекста меньше.
     * [instruction] — служебная просьба (продолжить, поздороваться), если последняя реплика не от пользователя.
     */
    fun reply(c: Character, chat: Chat, s: Settings, books: List<Lorebook>, msgs: List<Msg>, local: Boolean, instruction: String? = null): GenRequest {
        val visible = msgs.drop(chat.summarizedUpTo.coerceAtMost(maxOf(0, msgs.size - 6)))
        val recent = visible.takeLast(6).joinToString("\n") { it.text }
        val system = system(c, chat, s, books, recent)
        val ctx = if (local) s.contextTokens else 24000
        val out = s.length.maxOut
        val budget = (ctx - out - estimateTokens(system) - 64).coerceAtLeast(200)
        var hist = history(visible, budget)
        val needsTail = instruction != null || hist.isEmpty() || !hist.last().user
        if (needsTail) hist = hist + Turn(true, "(${instruction ?: "Продолжай сцену от лица ${c.name}."})")
        hist = withNote(hist, chat.authorNote)
        val (t, p, r) = sampling(s, c)
        return GenRequest(system, hist, out, t, p, r)
    }

    /** Заметка автора — скрытая подсказка к ближайшим ответам, добавляется к последней реплике. */
    fun withNote(hist: List<Turn>, note: String): List<Turn> {
        if (note.isBlank() || hist.isEmpty()) return hist
        val last = hist.last()
        return hist.dropLast(1) + last.copy(text = last.text + "\n\n(Заметка автора — учитывай в ответе, но не упоминай: ${note.trim()})")
    }

    fun greeting(c: Character, s: Settings): GenRequest {
        val chat = Chat(charId = c.id)
        val (t, p, r) = sampling(s, c)
        return GenRequest(
            system(c, chat, s, emptyList(), ""),
            listOf(Turn(true, "(Начни ролевую переписку: напиши первое сообщение от лица ${c.name}, обращаясь к ${s.user}. Задай сцену и настроение.)")),
            s.length.maxOut, t, p, r,
        )
    }

    fun impersonate(c: Character, chat: Chat, s: Settings): GenRequest {
        val transcript = chat.messages.takeLast(12).joinToString("\n") { (if (it.fromUser) s.user else c.name) + ": " + it.text.trim() }
        return GenRequest(
            "Ты помогаешь ${s.user} вести ролевую переписку с персонажем ${c.name}. Предложи одну короткую реплику от лица ${s.user}, которая продвинет сцену. Напиши только саму реплику, без пояснений и без имени в начале.",
            listOf(Turn(true, "Переписка:\n$transcript\n\nСледующая реплика ${s.user}:")),
            120, 0.9f, 0.95f, 1.05f,
        )
    }

    fun summary(c: Character, chat: Chat, s: Settings, upTo: Int): GenRequest {
        val part = chat.messages.subList(chat.summarizedUpTo.coerceIn(0, upTo), upTo)
        val transcript = part.joinToString("\n") { (if (it.fromUser) s.user else c.name) + ": " + it.text.trim() }.takeLast(9000)
        val known = chat.facts.joinToString("\n") { "- ${it.text}" }.ifBlank { "(пока нет)" }
        val user = "Прежнее краткое содержание:\n${chat.summary.ifBlank { "(пока нет)" }}\n\nУже известные факты о ${s.user}:\n$known\n\nНовые сообщения:\n$transcript\n\n" +
            "Обнови краткое содержание истории (не больше 120 слов, от третьего лица) и выпиши НОВЫЕ важные факты о ${s.user} (имя, предпочтения, события, договорённости). Ещё назови один ключевой момент в отношениях за этот отрезок (первая ссора, признание, примирение, общая победа), если он был. Ответь строго в формате:\nКРАТКО:\n<текст>\nФАКТЫ:\n- <факт>\n- <факт>\nМОМЕНТ: <одно предложение или «нет»>\nЕсли новых фактов нет, после «ФАКТЫ:» напиши «- нет»."
        return GenRequest("Ты аккуратно ведёшь память ролевой переписки между ${c.name} и ${s.user}.", listOf(Turn(true, user)), 400, 0.3f, 0.9f, 1.0f)
    }

    data class SummaryResult(val summary: String?, val facts: List<String>, val moment: String?)

    fun parseSummaryFull(text: String): SummaryResult {
        val mi = Regex("(?im)^\\s*\\**\\s*МОМЕНТ\\s*\\**\\s*:").find(text)
        val moment = mi?.let { text.substring(it.range.last + 1).lines().first().trim().trim('*', '.', ' ', '«', '»') }
            ?.takeIf { it.isNotBlank() && !it.startsWith("нет", true) && it.length < 300 }
        val (sum, facts) = parseSummary(if (mi != null) text.substring(0, mi.range.first) else text)
        return SummaryResult(sum, facts, moment)
    }

    fun parseSummary(text: String): Pair<String?, List<String>> {
        val t = text.replace("\r", "")
        val sumIdx = Regex("(?i)КРАТКО\\s*:").find(t)
        val factIdx = Regex("(?i)ФАКТЫ\\s*:").find(t)
        val summary = when {
            sumIdx != null -> t.substring(sumIdx.range.last + 1, factIdx?.range?.first?.takeIf { it > sumIdx.range.last } ?: t.length)
            factIdx != null -> t.substring(0, factIdx.range.first)
            else -> t
        }.trim().trim('*').trim().takeIf { it.isNotBlank() }
        val facts = if (factIdx == null) emptyList() else t.substring(factIdx.range.last + 1).lines()
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() && !it.equals("нет", true) && !it.startsWith("нет", true) && it.length < 200 }
        return summary to facts.take(8)
    }

    fun proactive(c: Character, chat: Chat, s: Settings, books: List<Lorebook>, hoursSilent: Long): GenRequest {
        val r = reply(c, chat, s.copy(length = Length.REPLY), books, chat.messages, local = true,
            instruction = "${s.user} молчит уже около $hoursSilent ч. Напиши ему первым короткое сообщение — так, как это сделал бы ${c.name}: вспомни что-то из прошлого разговора или спроси, как дела.")
        return r.copy(maxOut = 120)
    }

    // ---------- Настроение ----------
    /** Эмоции для аватарок и слова настроения, которые к ним ведут. */
    val EMOTIONS = linkedMapOf(
        "радость" to listOf("рад", "весел", "счаст", "игрив", "довол", "восторг", "тёпл", "тепл", "влюбл"),
        "грусть" to listOf("груст", "печал", "тоск", "уныл", "одинок"),
        "злость" to listOf("зл", "раздраж", "обиж", "сердит", "ярост", "недовол"),
        "удивление" to listOf("удивл", "изумл", "шок", "растерян"),
        "смущение" to listOf("смущ", "неловк", "застенч", "стесн"),
        "спокойствие" to listOf("спокой", "задумч", "нейтрал", "сонн", "устал"),
    )

    fun emotionFor(mood: String): String? {
        val m = mood.lowercase()
        if (m.isBlank()) return null
        return EMOTIONS.entries.firstOrNull { (_, keys) -> keys.any { m.contains(it) } }?.key
    }

    private val moodRe = Regex("\\[\\s*настроение\\s*:\\s*([^;\\]]*?)\\s*;\\s*отношение\\s*:\\s*([+\\-−–]?\\s*\\d+)\\s*\\]", RegexOption.IGNORE_CASE)

    /** Убирает пометку настроения. Возвращает текст, настроение и изменение отношения. */
    fun parseMood(t: String): Triple<String, String?, Int> {
        val m = moodRe.findAll(t).lastOrNull() ?: return Triple(hideTags(t), null, 0)
        val delta = m.groupValues[2].replace(Regex("[−–]"), "-").replace(" ", "").toIntOrNull()?.coerceIn(-3, 3) ?: 0
        return Triple(hideTags(t.removeRange(m.range)), m.groupValues[1].trim().trim('.').lowercase().take(30).ifBlank { null }, delta)
    }

    /** Прячет служебные пометки (в том числе недописанные во время печати). */
    fun hideTags(t: String): String {
        var r = t
        val open = Regex("\\[\\s*(настроение|hp|хп|здоровье|золото)", RegexOption.IGNORE_CASE)
        r = r.replace(Regex("\\[\\s*(hp|хп|здоровье|золото)\\s*[:]?\\s*[+\\-−–]\\s*\\d+\\s*\\]", RegexOption.IGNORE_CASE), "")
        r = r.replace(moodRe, "")
        open.find(r)?.let { if (!r.substring(it.range.first).contains(']')) r = r.substring(0, it.range.first) }
        return r.trimEnd()
    }

    // ---------- Дневник ----------
    fun diary(c: Character, chat: Chat, s: Settings): GenRequest {
        val transcript = chat.messages.takeLast(24).joinToString("\n") { (if (it.fromUser) s.user else c.name) + ": " + it.text.trim() }.takeLast(7000)
        val prev = chat.diary.lastOrNull()?.text?.let { "\n\nПрошлая запись в дневнике:\n$it" } ?: ""
        return GenRequest(
            system(c, chat, s, emptyList(), ""),
            listOf(Turn(true, "(Вне ролевой сцены.) Вот недавняя переписка:\n$transcript$prev\n\nНапиши новую запись в личный дневник ${c.name} о последних событиях и о ${s.user}: 3–5 предложений, от первого лица, в характере ${c.name}, с чувствами и мыслями, которые он(а) не сказал(а) вслух. Только текст записи, без даты и заголовка.")),
            300, 0.9f, 0.95f, 1.1f,
        )
    }

    // ---------- Создание персонажа по фразе ----------
    fun generateCharacter(idea: String): GenRequest = GenRequest(
        "Ты помогаешь придумывать персонажей для ролевых чатов. Пиши по-русски, живо и конкретно.",
        listOf(Turn(true, "Придумай персонажа по идее: «${idea.trim()}».\nОтветь строго в формате (каждое поле с новой строки, заголовки как есть):\n" +
            "ИМЯ: <до 20 символов>\nСЛОГАН: <до 50 символов>\nОПИСАНИЕ: <2–4 предложения, до 500 символов>\nХАРАКТЕР: <3–5 черт через запятую>\n" +
            "ОТНОШЕНИЯ: <кем персонаж приходится собеседнику>\nПРИВЕТСТВИЕ: <первое сообщение персонажа с *действиями* в звёздочках>\n" +
            "ПРИМЕРЫ:\n{{char}}: <реплика>\n{{user}}: <реплика>\n{{char}}: <реплика>\nТЕГИ: <3–5 тегов через запятую>")),
        900, 0.95f, 0.95f, 1.05f,
    )

    fun parseCharacter(t: String, base: Character): Character {
        val keys = listOf("ИМЯ", "СЛОГАН", "ОПИСАНИЕ", "ХАРАКТЕР", "ОТНОШЕНИЯ", "ПРИВЕТСТВИЕ", "ПРИМЕРЫ", "ТЕГИ")
        val re = Regex("(?m)^\\s*\\**\\s*(${keys.joinToString("|")})\\s*\\**\\s*:")
        val hits = re.findAll(t).toList()
        val f = hits.mapIndexed { i, m ->
            val end = if (i + 1 < hits.size) hits[i + 1].range.first else t.length
            m.groupValues[1] to t.substring(m.range.last + 1, end).trim().let { v -> if (v.startsWith("**")) v.removePrefix("**").trim() else v }
        }.toMap()
        if (f["ИМЯ"].isNullOrBlank()) throw IllegalArgumentException("Модель ответила не по формату. Попробуй ещё раз.")
        val def = listOfNotNull(f["ХАРАКТЕР"]?.takeIf { it.isNotBlank() }?.let { "Характер: $it" }, f["ПРИМЕРЫ"]?.takeIf { it.isNotBlank() }).joinToString("\n\n")
        return base.copy(
            name = f["ИМЯ"]!!.trim('"', '«', '»').take(20),
            tagline = f["СЛОГАН"].orEmpty().trim('"', '«', '»').take(50),
            description = f["ОПИСАНИЕ"].orEmpty().take(500),
            relation = f["ОТНОШЕНИЯ"].orEmpty().take(1000),
            greetings = listOf(f["ПРИВЕТСТВИЕ"].orEmpty().take(4096)) + base.greetings.drop(1).filter { it.isNotBlank() },
            definition = def.take(32000),
            tags = f["ТЕГИ"].orEmpty().split(',', ';').map { it.trim().trim('#', '.').lowercase() }.filter { it.isNotBlank() && it.length < 25 }.take(6).ifEmpty { base.tags },
        )
    }

    // ---------- Сцены ----------
    fun sceneTranscriptName(m: Msg, chars: Map<String, Character>, user: String) = when (m.speaker) {
        null -> if (m.fromUser) user else "?"
        NARRATOR -> "Рассказчик"
        DICE -> "Кубик"
        else -> chars[m.speaker]?.name ?: "?"
    }

    private fun sceneIntro(sc: Scene, chars: List<Character>, hero: HeroStats?, s: Settings): String = buildString {
        if (sc.mode == SceneMode.HANGOUT) {
            append("Общий чат «${sc.name}» — непринуждённая переписка друзей, без сюжета и рассказчика.")
            if (sc.world.isNotBlank()) append("\nГде и как общаетесь: ${sc.world.trim()}")
            append("\nВ чате:")
            chars.forEach { c -> append("\n- ${c.name}" + (c.tagline.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")) }
            append("\n- ${s.user}" + (s.userAbout.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""))
            return@buildString
        }
        append("Групповая ролевая сцена «${sc.name}».")
        if (sc.world.isNotBlank()) append("\nМир и обстановка: ${sc.world.trim()}")
        append("\nУчастники:")
        chars.forEach { c -> append("\n- ${c.name}" + (c.tagline.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")) }
        append("\n- ${s.user} — игрок" + (s.userAbout.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""))
        if (sc.dice && hero != null) append("\nГерой игрока: здоровье ${hero.hp}/${hero.maxHp}, золото ${hero.gold}" +
            (hero.inventory.takeIf { it.isNotBlank() }?.let { ", инвентарь: $it" } ?: "") + (hero.notes.takeIf { it.isNotBlank() }?.let { ". $it" } ?: ""))
    }

    /** Историю сцены превращаем в диалог с точки зрения одного говорящего. */
    fun sceneHistory(msgs: List<Msg>, me: String, chars: Map<String, Character>, user: String, budget: Int): List<Turn> {
        val asTurns = msgs.filter { it.kind != PICTURE }.map { m ->
            val mine = m.speaker == me
            Msg(fromUser = !mine, variants = listOf(if (mine) m.text else "${sceneTranscriptName(m, chars, user)}: ${m.text}"))
        }
        return history(asTurns, budget)
    }

    fun sceneReply(sc: Scene, c: Character, chars: List<Character>, chat: Chat, s: Settings, books: List<Lorebook>, local: Boolean, instruction: String? = null): GenRequest {
        val map = chars.associateBy { it.id }
        val recent = chat.messages.takeLast(6).joinToString("\n") { it.text }
        val sys = buildString {
            append(sceneIntro(sc, chars, chat.hero, s))
            if (sc.mode == SceneMode.HANGOUT) append("\n\nТы — ${c.name}. Пиши только свои сообщения, как в групповом мессенджере: можешь отвечать ${s.user} или другим участникам, обращаться к ним по имени, подхватывать и подкалывать. Не пиши за других.")
            else append("\n\nТвоя роль — ТОЛЬКО ${c.name}. Не пиши реплики и действия других участников, рассказчика и игрока ${s.user}.")
            append("\n\n" + card(c, s.user))
            moodState(c, chat, s.user).takeIf { it.isNotBlank() }?.let { append("\n\n$it") }
            memory(chat, c, s.user).takeIf { it.isNotBlank() }?.let { append("\n\n$it") }
            lore(c.copy(lorebookIds = c.lorebookIds + sc.lorebookIds), books, recent).takeIf { it.isNotBlank() }?.let { append("\n\n$it") }
            append("\n\n" + rules(s, c, s.user))
        }
        val ctx = if (local) s.contextTokens else 24000
        val out = s.length.maxOut
        var hist = sceneHistory(chat.messages, c.id, map, s.user, (ctx - out - estimateTokens(sys) - 64).coerceAtLeast(200))
        hist = hist + Turn(true, "(${instruction ?: "Твоя реплика — ты ${c.name}."})")
        val merged = withNote(history(hist.map { Msg(fromUser = it.user, variants = listOf(it.text)) }, Int.MAX_VALUE), chat.authorNote)
        val (t, p, r) = sampling(s, c)
        return GenRequest(sys, merged, out, t, p, r)
    }

    const val EVENT_HINT = "Внеси неожиданный поворот: случайное событие, которое меняет ситуацию (кто-то появляется, что-то ломается, приходит весть и т. п.)."

    fun narrator(sc: Scene, chars: List<Character>, chat: Chat, s: Settings, books: List<Lorebook>, local: Boolean, instruction: String? = null): GenRequest {
        val map = chars.associateBy { it.id }
        val recent = chat.messages.takeLast(6).joinToString("\n") { it.text }
        val sys = buildString {
            append("Ты — рассказчик и мастер игры. ")
            append(sceneIntro(sc, chars, chat.hero, s))
            append("\n\nОписывай мир, обстановку и последствия действий игрока коротко и образно (2–4 предложения). Не говори за персонажей и за игрока — только повествование от третьего лица.")
            if (sc.dice) append(" Если игрок бросил кубик, опиши результат: 1–5 — провал, 6–10 — с трудом, 11–15 — успех, 16–19 — блестяще, 20 — невероятная удача. Если здоровье или золото героя меняется, добавь в конце пометку вида [HP -3] или [золото +5].")
            lore(Character(name = "", lorebookIds = sc.lorebookIds + chars.flatMap { it.lorebookIds }), books, recent).takeIf { it.isNotBlank() }?.let { append("\n\n$it") }
            append("\nОтвечай на том языке, на котором пишет игрок.")
        }
        val ctx = if (local) s.contextTokens else 24000
        var hist = sceneHistory(chat.messages, NARRATOR, map, s.user, (ctx - 300 - estimateTokens(sys) - 64).coerceAtLeast(200))
        hist = hist + Turn(true, "(${instruction ?: "Рассказчик, опиши, что происходит дальше."})")
        val merged = withNote(history(hist.map { Msg(fromUser = it.user, variants = listOf(it.text)) }, Int.MAX_VALUE), chat.authorNote)
        return GenRequest(sys, merged, 300, 0.9f, 0.95f, 1.1f)
    }

    /** Кого из участников упомянули в тексте (по основе имени). */
    fun mentioned(text: String, chars: List<Character>, except: String? = null): List<Character> {
        val low = text.lowercase()
        return chars.filter { c -> c.id != except && c.name.isNotBlank() && (low.contains("@" + c.name.lowercase()) || low.contains(stem(c.name))) }
    }

    const val BANTER_HINT = "Продолжи разговор с другими участниками чата: обратись к кому-то из них по имени, подхвати тему или начни новую."

    /** Кто отвечает в сцене после реплики игрока. */
    fun responders(sc: Scene, chars: List<Character>, chat: Chat, userText: String, random: kotlin.random.Random = kotlin.random.Random): List<Character> {
        if (chars.isEmpty()) return emptyList()
        val lastChar = chat.messages.lastOrNull { it.speaker != null && it.speaker != NARRATOR && it.speaker != DICE }?.speaker
        val low = userText.lowercase()
        val named = chars.filter { c -> c.name.isNotBlank() && (low.contains("@" + c.name.lowercase()) || low.contains(stem(c.name))) }
        if (sc.mode == SceneMode.HANGOUT) {
            val first = named.firstOrNull() ?: if (sc.turnMode == TurnMode.MENTION) chars.firstOrNull { it.id != lastChar && random.nextBoolean() } ?: chars[random.nextInt(chars.size)]
                else chars[random.nextInt(chars.size)]
            val second = (named.drop(1) + chars.filter { it.id != first.id }.shuffled(random)).firstOrNull()
                ?.takeIf { named.size > 1 || (chars.size > 1 && random.nextInt(100) < 45) }
            return listOfNotNull(first, second)
        }
        return when {
            named.isNotEmpty() -> named.take(2)
            sc.turnMode == TurnMode.ROUND -> {
                val i = chars.indexOfFirst { it.id == lastChar }
                listOf(chars[(i + 1) % chars.size])
            }
            sc.turnMode == TurnMode.RANDOM -> listOf(chars[random.nextInt(chars.size)])
            else -> listOf(chars.firstOrNull { it.id == lastChar } ?: chars.first())
        }
    }

    private val heroRe = Regex("\\[\\s*(hp|хп|здоровье|золото)\\s*:?\\s*([+\\-−–])\\s*(\\d+)\\s*\\]", RegexOption.IGNORE_CASE)

    /** Применяет пометки рассказчика [HP -3], [золото +5]. */
    fun applyHero(t: String, h: HeroStats): HeroStats {
        var r = h
        heroRe.findAll(t).forEach { m ->
            val n = (m.groupValues[3].toIntOrNull() ?: 0) * if (m.groupValues[2] == "+") 1 else -1
            r = if (m.groupValues[1].lowercase() == "золото") r.copy(gold = (r.gold + n).coerceAtLeast(0))
            else r.copy(hp = (r.hp + n).coerceIn(0, r.maxHp))
        }
        return r
    }

    // ---------- Иллюстрации ----------
    fun illustration(names: String, transcript: String, world: String = ""): GenRequest = GenRequest(
        "You write prompts for an image generation model.",
        listOf(Turn(true, "Characters: $names\n${if (world.isNotBlank()) "Setting: $world\n" else ""}Recent roleplay:\n$transcript\n\n" +
            "Write ONE vivid image prompt in English (max 70 words) that illustrates the current moment: who is there, what they do, place, lighting, mood. Painterly digital illustration style. No text, no captions, no speech bubbles. Output only the prompt.")),
        160, 0.7f, 0.9f, 1.0f,
    )

    // ---------- Вычитка ----------
    fun proofread(text: String): GenRequest = GenRequest(
        "Ты корректор. Исправь в тексте только ошибки: орфографию, пунктуацию, падежи, роды, согласование слов, опечатки. " +
            "Не меняй смысл, стиль, порядок фраз и слова, которые написаны правильно. Сохрани *звёздочки*, эмодзи, переносы строк и язык текста. " +
            "Если ошибок нет — верни текст без изменений. Ответь только исправленным текстом, без пояснений и кавычек.",
        listOf(Turn(true, text)),
        (estimateTokens(text) * 1.4).toInt() + 40, 0.15f, 0.9f, 1.0f,
    )

    /** Принимаем вычитку, только если она похожа на исправление, а не на переписанный текст. */
    fun acceptProofread(orig: String, fixed: String): Boolean {
        val f = fixed.trim().trim('"', '«', '»')
        if (f.isBlank() || f == orig.trim()) return false
        val ratio = f.length.toFloat() / orig.trim().length.coerceAtLeast(1)
        if (ratio < 0.85f || ratio > 1.15f) return false
        if (orig.count { it == '*' } != f.count { it == '*' }) return false
        val wo = orig.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 3 }.toSet()
        val wf = f.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 3 }.toSet()
        if (wo.isEmpty()) return true
        // Большая часть длинных слов должна остаться (пусть и с исправленными окончаниями)
        val kept = wo.count { w -> wf.any { it.take(4) == w.take(4) } }
        return kept.toFloat() / wo.size >= 0.8f
    }
}
