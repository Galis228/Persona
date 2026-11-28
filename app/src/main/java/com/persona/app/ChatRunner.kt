package com.persona.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class Running(val chatId: String, val msgId: String)

sealed interface ChatEvent {
    data class Error(val chatId: String, val message: String, val kind: ErrKind = ErrKind.OTHER, val url: String? = null) : ChatEvent
    data class Remembered(val chatId: String, val facts: List<String>) : ChatEvent
    data class Mood(val chatId: String, val mood: String?, val delta: Int) : ChatEvent
}

/** Вся логика переписки: чаты и сцены, свайпы, перегенерация, продолжение, память, настроение, дневник. */
class ChatRunner(private val app: App) {
    private val repo get() = app.repo
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val _running = MutableStateFlow<Running?>(null)
    val running: StateFlow<Running?> = _running
    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events
    private val background = mutableSetOf<String>()

    private fun settingsFor(chatId: String) = repo.settings.value.forChat(repo.chat(chatId))
    private fun sceneChars(sc: Scene) = sc.charIds.mapNotNull { repo.char(it) }

    // ================= Обычный чат =================

    fun newChat(charId: String, personaId: String? = null, incognito: Boolean = false): Chat {
        val c = repo.char(charId) ?: error("Нет персонажа")
        val s = repo.settings.value
        val pid = personaId ?: s.defaultPersonaId
        val user = s.persona(pid)?.name ?: s.user
        val greets = c.greetings.map { it.trim() }.filter { it.isNotBlank() }.map { Prompt.fill(it, c.name, user) }
        val chat = Chat(charId = charId, personaId = pid, incognito = incognito,
            messages = if (greets.isNotEmpty()) listOf(Msg(fromUser = false, variants = greets, info = "приветствие")) else emptyList())
        repo.saveChat(chat)
        if (greets.isEmpty() || c.aiGreeting) {
            if (greets.isNotEmpty()) {
                addVariant(chat.id, chat.messages[0].id)
                launchGen(chat.id, chat.messages[0].id) { _, st, _ -> Prompt.greeting(c, st) }
            } else {
                val m = Msg(fromUser = false, variants = listOf(""))
                repo.saveChat(chat.copy(messages = listOf(m)))
                launchGen(chat.id, m.id) { _, st, _ -> Prompt.greeting(c, st) }
            }
        }
        return chat
    }

    fun send(chatId: String, text: String, image: String? = null) {
        if (_running.value != null) return
        val chat = repo.chat(chatId) ?: return
        if (chat.sceneId != null) { sceneSend(chatId, text); return }
        val t = text.trim()
        val msgs = if (t.isEmpty() && image == null) chat.messages else chat.messages + Msg(fromUser = true, variants = listOf(t), image = image)
        val reply = Msg(fromUser = false, variants = listOf(""))
        repo.saveChat(chat.copy(messages = msgs + reply))
        launchGen(chatId, reply.id, build = ::replyRequest)
    }

    private fun replyRequest(ch: Chat, s: Settings, local: Boolean): GenRequest {
        val c = repo.char(ch.charId) ?: throw EngineException("Персонаж удалён")
        return Prompt.reply(c, ch, s, repo.books.value, ch.messages, local)
    }

    fun regenerate(chatId: String, msgId: String) {
        if (_running.value != null) return
        val ch = repo.chat(chatId) ?: return
        val idx = ch.messages.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        addVariant(chatId, msgId)
        val m = ch.messages[idx]
        if (ch.sceneId != null) { launchGen(chatId, msgId, build = sceneBuilder(m.speaker)); return }
        if (idx == 0) {
            val c = repo.char(ch.charId) ?: return
            launchGen(chatId, msgId) { _, st, _ -> Prompt.greeting(c, st) }
        } else launchGen(chatId, msgId, build = ::replyRequest)
    }

    fun swipe(chatId: String, msgId: String, dir: Int) {
        if (_running.value != null) return
        val chat = repo.chat(chatId) ?: return
        val m = chat.messages.firstOrNull { it.id == msgId } ?: return
        val next = m.sel + dir
        if (next >= m.variants.size) {
            if (chat.messages.last().id == msgId) regenerate(chatId, msgId)
            return
        }
        if (next < 0) return
        repo.updateChat(chatId) { ch -> ch.copy(messages = ch.messages.map { if (it.id == msgId) it.copy(sel = next) else it }) }
    }

    fun continueLast(chatId: String) {
        if (_running.value != null) return
        val chat = repo.chat(chatId) ?: return
        if (chat.sceneId != null) { sceneNext(chatId); return }
        val last = chat.messages.lastOrNull()
        if (last == null || last.fromUser) { send(chatId, ""); return }
        val c = repo.char(chat.charId) ?: return
        launchGen(chatId, last.id, continueText = true) { ch, st, local ->
            Prompt.reply(c, ch, st, repo.books.value, ch.messages + last, local,
                instruction = "Продолжи свою последнюю реплику с того места, где остановился. Не повторяй уже написанное.")
        }
    }

    fun stop() { job?.cancel() }

    fun edit(chatId: String, msgId: String, text: String) = repo.updateChat(chatId) { ch ->
        ch.copy(messages = ch.messages.map { m -> if (m.id == msgId) m.copy(variants = m.variants.toMutableList().also { it[m.sel] = text }) else m })
    }

    /** Удаляет сообщение и возвращает действие «Отменить». */
    fun deleteWithUndo(chatId: String, msgId: String): (() -> Unit)? {
        val ch = repo.chat(chatId) ?: return null
        val idx = ch.messages.indexOfFirst { it.id == msgId }
        if (idx < 0) return null
        val m = ch.messages[idx]
        delete(chatId, msgId)
        return { repo.updateChat(chatId) { cur -> cur.copy(messages = cur.messages.toMutableList().also { it.add(idx.coerceAtMost(it.size), m) }) } }
    }

    fun delete(chatId: String, msgId: String) = repo.updateChat(chatId) { ch ->
        val idx = ch.messages.indexOfFirst { it.id == msgId }
        ch.copy(messages = ch.messages.filterNot { it.id == msgId },
            summarizedUpTo = if (idx in 0 until ch.summarizedUpTo) (ch.summarizedUpTo - 1).coerceAtLeast(0) else ch.summarizedUpTo)
    }

    fun react(chatId: String, msgId: String, emoji: String?) = repo.updateChat(chatId) { ch ->
        ch.copy(messages = ch.messages.map { if (it.id == msgId) it.copy(reaction = emoji) else it })
    }

    fun setPersona(chatId: String, personaId: String?) = repo.updateChat(chatId) { it.copy(personaId = personaId) }

    /** Новая ветка: копия чата до этого сообщения включительно. */
    fun branch(chatId: String, msgId: String): Chat? {
        val ch = repo.chat(chatId) ?: return null
        val idx = ch.messages.indexOfFirst { it.id == msgId }
        if (idx < 0) return null
        val keepSummary = ch.summarizedUpTo <= idx + 1
        val n = ch.copy(id = newId(), messages = ch.messages.take(idx + 1).map { it.copy(id = newId()) },
            summary = if (keepSummary) ch.summary else "", summarizedUpTo = if (keepSummary) ch.summarizedUpTo else 0,
            title = "Ветка", diary = emptyList(), diaryAt = 0)
        repo.saveChat(n)
        return n
    }

    fun impersonate(chatId: String, onResult: (String) -> Unit) {
        if (_running.value != null) return
        val ch = repo.chat(chatId) ?: return
        val s = settingsFor(chatId)
        val c = repo.char(ch.charId) ?: ch.sceneId?.let { repo.scene(it) }?.let { sc -> sceneChars(sc).firstOrNull()?.copy(name = sc.name) } ?: return
        job = scope.launch {
            _running.value = Running(chatId, "")
            try {
                val (_, flow) = app.engines.generate(Prompt.impersonate(c, ch, s), s)
                val t = flow.fold("") { a, b -> a + b }.trim().removePrefix("${s.user}:").trim().trim('"', '«', '»')
                onResult(t)
            } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                ErrorLog.add("impersonate", e); _events.tryEmit(ChatEvent.Error(chatId, e.message ?: e.toString(), (e as? EngineException)?.kind ?: ErrKind.OTHER, (e as? EngineException)?.url))
            } finally { _running.value = null }
        }
    }

    // ================= Сцены =================

    fun newSceneChat(sceneId: String, personaId: String? = null): Chat {
        val sc = repo.scene(sceneId) ?: error("Нет сцены")
        val s = repo.settings.value
        val pid = personaId ?: s.defaultPersonaId
        val opening = sc.opening.trim().ifBlank { null }?.let { listOf(Msg(fromUser = false, variants = listOf(it), speaker = NARRATOR, info = "начало")) } ?: emptyList()
        val chat = Chat(charId = "", sceneId = sceneId, personaId = pid, messages = opening, hero = sc.hero)
        repo.saveChat(chat)
        if (opening.isEmpty() && sc.mode == SceneMode.ADVENTURE && sc.narrator) {
            val m = Msg(fromUser = false, variants = listOf(""), speaker = NARRATOR)
            repo.saveChat(chat.copy(messages = listOf(m)))
            launchGen(chat.id, m.id, build = sceneBuilder(NARRATOR, "Начни сцену: опиши место, время и атмосферу, представь участников."))
        } else if (opening.isEmpty() && sc.mode == SceneMode.HANGOUT) {
            sceneChars(sc).firstOrNull()?.let { first ->
                runQueue(chat.id, listOf(first.id to "Начни разговор в общем чате: поздоровайся со всеми, можно с шуткой или новостью."))
            }
        }
        return chat
    }

    private fun sceneBuilder(speaker: String?, instruction: String? = null): (Chat, Settings, Boolean) -> GenRequest = { ch, st, local ->
        val sc = repo.scene(ch.sceneId) ?: throw EngineException("Сцена удалена")
        val chars = sceneChars(sc)
        if (speaker == NARRATOR || speaker == null) Prompt.narrator(sc, chars, ch, st, repo.books.value, local, instruction)
        else {
            val c = chars.firstOrNull { it.id == speaker } ?: repo.char(speaker) ?: throw EngineException("Персонаж удалён")
            Prompt.sceneReply(sc, c, chars, ch, st, repo.books.value, local, instruction)
        }
    }

    private fun sceneSend(chatId: String, text: String) {
        val chat = repo.chat(chatId) ?: return
        val sc = repo.scene(chat.sceneId) ?: return
        val t = text.trim()
        if (t.isNotEmpty()) repo.saveChat(chat.copy(messages = chat.messages + Msg(fromUser = true, variants = listOf(t))))
        val chars = sceneChars(sc)
        val adv = sc.mode == SceneMode.ADVENTURE
        val event = adv && sc.narrator && sc.randomEvents && kotlin.random.Random.nextInt(100) < 15
        val narrate = adv && sc.narrator && (event || t.contains('*') || chars.isEmpty())
        val who = Prompt.responders(sc, chars, repo.chat(chatId)!!, t)
        runQueue(chatId, buildList {
            if (narrate) add(NARRATOR to if (event) Prompt.EVENT_HINT else null)
            who.forEach { add(it.id to null) }
        })
    }

    /** «Продолжи» в сцене: следующий по очереди говорит без реплики игрока. */
    fun sceneNext(chatId: String) {
        if (_running.value != null) return
        val chat = repo.chat(chatId) ?: return
        val sc = repo.scene(chat.sceneId) ?: return
        val chars = sceneChars(sc)
        val next = Prompt.responders(sc.copy(turnMode = if (sc.turnMode == TurnMode.MENTION) TurnMode.ROUND else sc.turnMode), chars, chat, "")
        runQueue(chatId, next.map { it.id to null }.ifEmpty { if (sc.mode == SceneMode.ADVENTURE) listOf(NARRATOR to null) else emptyList() })
    }

    /** «Пусть поболтают»: персонажи общаются между собой несколько реплик. */
    fun banter(chatId: String) {
        if (_running.value != null) return
        val chat = repo.chat(chatId) ?: return
        val sc = repo.scene(chat.sceneId) ?: return
        val chars = sceneChars(sc)
        if (chars.size < 2) return
        val last = chat.messages.lastOrNull { it.speaker != null && it.speaker != NARRATOR && it.speaker != DICE }?.speaker
        val start = (chars.indexOfFirst { it.id == last } + 1).coerceAtLeast(0)
        val order = (0 until 3).map { chars[(start + it) % chars.size].id }
        runQueue(chatId, order.map { it to Prompt.BANTER_HINT }, followMentions = false)
    }

    fun setSceneMode(sceneId: String, mode: SceneMode) {
        val sc = repo.scene(sceneId) ?: return
        repo.saveScene(sc.copy(mode = mode))
        if (mode == SceneMode.ADVENTURE) repo.chats.value.filter { it.sceneId == sceneId && it.hero == null }.forEach { ch -> repo.saveChat(ch.copy(hero = sc.hero)) }
    }

    fun sceneSpeak(chatId: String, speaker: String) {
        if (_running.value != null) return
        runQueue(chatId, listOf(speaker to null))
    }

    fun rollDice(chatId: String, sides: Int = 20) {
        if (_running.value != null) return
        val chat = repo.chat(chatId) ?: return
        val sc = repo.scene(chat.sceneId) ?: return
        val v = kotlin.random.Random.nextInt(1, sides + 1)
        repo.saveChat(chat.copy(messages = chat.messages + Msg(fromUser = true, variants = listOf("Бросок d$sides: $v"), speaker = DICE)))
        if (sc.narrator) runQueue(chatId, listOf(NARRATOR to "Опиши результат броска d$sides = $v для последнего действия игрока."))
    }

    fun setHero(chatId: String, h: HeroStats) = repo.updateChat(chatId) { it.copy(hero = h) }

    /** Очередь реплик в сцене. Если персонаж обратился к другому по имени — тот ответит следом (до 3 реплик подряд). */
    private fun runQueue(chatId: String, steps: List<Pair<String, String?>>, followMentions: Boolean = true) {
        if (steps.isEmpty()) return
        job = scope.launch {
            val queue = ArrayDeque(steps)
            var spoken = 0
            while (queue.isNotEmpty()) {
                val (speaker, instr) = queue.removeFirst()
                val ch = repo.chat(chatId) ?: break
                val m = Msg(fromUser = false, variants = listOf(""), speaker = speaker)
                repo.saveChat(ch.copy(messages = ch.messages + m))
                if (!genNow(chatId, m.id, false, sceneBuilder(speaker, instr))) break
                spoken++
                if (followMentions && speaker != NARRATOR && spoken < 3 && queue.none { it.first != NARRATOR }) {
                    val sc = repo.scene(ch.sceneId) ?: continue
                    val text = repo.chat(chatId)?.messages?.firstOrNull { it.id == m.id }?.text.orEmpty()
                    Prompt.mentioned(text, sceneChars(sc), except = speaker).firstOrNull()?.let { queue.addLast(it.id to null) }
                }
            }
        }
    }

    // ================= Генерация =================

    private fun addVariant(chatId: String, msgId: String) = repo.updateChat(chatId, false) { ch ->
        ch.copy(messages = ch.messages.map { if (it.id == msgId) it.copy(variants = it.variants + "", sel = it.variants.size) else it })
    }

    private fun launchGen(chatId: String, msgId: String, continueText: Boolean = false, build: (Chat, Settings, Boolean) -> GenRequest) {
        job = scope.launch { genNow(chatId, msgId, continueText, build) }
    }

    /** Генерирует текст в сообщение [msgId]. Модель видит чат только до этого сообщения. Возвращает true при успехе. */
    private suspend fun genNow(chatId: String, msgId: String, continueText: Boolean, build: (Chat, Settings, Boolean) -> GenRequest): Boolean {
        _running.value = Running(chatId, msgId)
        val started = System.currentTimeMillis()
        var label = ""
        try {
            val ch = repo.chat(chatId) ?: return false
            val s = repo.settings.value.forChat(ch)
            val idx = ch.messages.indexOfFirst { it.id == msgId }
            if (idx < 0) return false
            val target = ch.messages[idx]
            val local = app.engines.isLocal(s)
            val prevUser = ch.messages.take(idx).lastOrNull()?.takeIf { it.fromUser && it.image != null }
            val img = prevUser?.image?.let { repo.imageFile(it) }?.readBytes()
            val req = build(ch.copy(messages = ch.messages.take(idx)), s, local).let { if (img != null) it.copy(image = img) else it }
            val (lbl, flow) = app.engines.generate(req, s)
            label = lbl
            val base = if (continueText) target.text else ""
            var acc = base
            var lastSave = 0L
            flow.collect { piece ->
                acc = when {
                    acc.isEmpty() -> piece.trimStart()
                    continueText && acc == base && !acc.endsWith(" ") && !piece.startsWith(" ") -> "$acc $piece"
                    else -> acc + piece
                }
                val now = System.currentTimeMillis()
                if (now - lastSave > 60) { setText(chatId, msgId, Prompt.hideTags(acc), null, false); lastSave = now }
            }
            val speakerChar = when (target.speaker) {
                null -> repo.char(ch.charId)
                NARRATOR, DICE -> null
                else -> repo.char(target.speaker)
            }
            val names = (listOfNotNull(speakerChar?.name, if (target.speaker == NARRATOR) "Рассказчик" else null)).firstOrNull() ?: ""
            var clean = cleanup(acc, names, s.user, ch.sceneId?.let { repo.scene(it) }?.let { sceneChars(it).map { c -> c.name } } ?: emptyList())
            // Пометки рассказчика о здоровье и золоте
            if (target.speaker == NARRATOR) ch.hero?.let { h ->
                val nh = Prompt.applyHero(acc, h)
                if (nh != h) repo.updateChat(chatId, false) { it.copy(hero = nh) }
            }
            // Настроение и отношение
            if (speakerChar?.moodEnabled == true) {
                val (t, mood, delta) = Prompt.parseMood(clean)
                clean = t
                if (mood != null || delta != 0) {
                    repo.updateChat(chatId, false) { it.copy(mood = mood ?: it.mood, affinity = (it.affinity + delta * 2).coerceIn(0, 100)) }
                    _events.tryEmit(ChatEvent.Mood(chatId, mood, delta))
                }
            } else clean = Prompt.hideTags(clean)
            if (clean.isBlank()) throw EngineException("Модель вернула пустой ответ. Попробуй ещё раз или смени модель.")
            var extra = ""
            if (s.proofread && target.kind != PICTURE && clean.length in 8..6000) {
                setText(chatId, msgId, clean, null, false)
                runCatching {
                    val (_, pf) = app.engines.generate(Prompt.proofread(clean), s)
                    val fixed = Prompt.hideTags(pf.fold("") { a, b -> a + b }).trim().trim('"', '«', '»')
                    if (Prompt.acceptProofread(clean, fixed)) { clean = fixed; extra = " · вычитано" }
                }.onFailure { if (it is CancellationException) throw it; ErrorLog.add("proofread", it) }
            }
            setText(chatId, msgId, clean, info(label, started) + extra, true)
            speakerChar?.voice?.takeIf { it.autoRead }?.let { app.voice.speak(clean, it) }
            if (ch.sceneId == null) afterReply(chatId)
            return true
        } catch (e: CancellationException) {
            val cur = repo.chat(chatId)?.messages?.firstOrNull { it.id == msgId }
            if (cur != null) {
                if (cur.text.isBlank()) dropEmpty(chatId, msgId) else setText(chatId, msgId, Prompt.hideTags(cur.text).trim(), info(label, started) + " · остановлено", true)
            }
            throw e
        } catch (e: Throwable) {
            ErrorLog.add("generate", e)
            dropEmpty(chatId, msgId)
            _events.tryEmit(ChatEvent.Error(chatId, e.message ?: e.toString(), (e as? EngineException)?.kind ?: ErrKind.OTHER, (e as? EngineException)?.url))
            return false
        } finally {
            _running.value = null
        }
    }

    private fun info(label: String, started: Long) =
        label + " · " + String.format(java.util.Locale.US, "%.1f с", (System.currentTimeMillis() - started) / 1000f).replace('.', ',')

    private fun cleanup(t: String, me: String, user: String, others: List<String>): String {
        var s = t.trim()
        if (me.isNotBlank()) s = s.removePrefix("$me:").removePrefix("**$me:**").trim()
        // Модель иногда начинает писать за других — обрезаем такую реплику.
        (listOf(user) + others.filter { it != me } + listOf("Рассказчик")).filter { it.isNotBlank() }.forEach { n ->
            Regex("\n\\s*\\*{0,2}${Regex.escape(n)}\\*{0,2}\\s*:").find(s)?.let { s = s.substring(0, it.range.first).trim() }
        }
        return s
    }

    private fun setText(chatId: String, msgId: String, text: String, info: String?, persist: Boolean) =
        repo.updateChat(chatId, persist) { ch ->
            ch.copy(messages = ch.messages.map { m ->
                if (m.id != msgId) m else m.copy(variants = m.variants.toMutableList().also { it[m.sel.coerceIn(0, it.lastIndex)] = text }, info = info ?: m.info)
            })
        }

    private fun dropEmpty(chatId: String, msgId: String) = repo.updateChat(chatId) { ch ->
        ch.copy(messages = ch.messages.mapNotNull { m ->
            if (m.id != msgId) m else {
                val v = m.variants.filter { it.isNotBlank() }
                if (v.isEmpty()) null else m.copy(variants = v, sel = m.sel.coerceAtMost(v.lastIndex))
            }
        })
    }

    // ================= Память и дневник =================

    private fun afterReply(chatId: String) {
        val s = repo.settings.value
        val ch = repo.chat(chatId) ?: return
        val c = repo.char(ch.charId) ?: return
        if (ch.incognito) return
        val upTo = ch.messages.size - 8
        if (s.summarize && upTo - ch.summarizedUpTo >= s.summarizeEvery) bg("sum:$chatId") { summarize(chatId, upTo) }
        if (c.diaryEnabled && ch.messages.size - ch.diaryAt >= 20) bg("diary:$chatId") { writeDiary(chatId) }
    }

    private fun bg(key: String, block: suspend () -> Unit) {
        if (!background.add(key)) return
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { ErrorLog.add(key, e) } finally { background.remove(key) }
        }
    }

    fun summarizeNow(chatId: String, onDone: (String?) -> Unit) {
        val ch = repo.chat(chatId) ?: return
        val upTo = (ch.messages.size - 2).coerceAtLeast(0)
        if (upTo <= ch.summarizedUpTo) { onDone("Пока нечего сжимать — сообщений мало."); return }
        if (!background.add("sum:$chatId")) return
        scope.launch {
            try { summarize(chatId, upTo); onDone(null) } catch (e: Throwable) { onDone(e.message) } finally { background.remove("sum:$chatId") }
        }
    }

    fun diaryNow(chatId: String, onDone: (String?) -> Unit) {
        if (!background.add("diary:$chatId")) return
        scope.launch {
            try { writeDiary(chatId); onDone(null) } catch (e: Throwable) { onDone(e.message) } finally { background.remove("diary:$chatId") }
        }
    }

    private suspend fun writeDiary(chatId: String) {
        val ch = repo.chat(chatId) ?: return
        val c = repo.char(ch.charId) ?: return
        if (ch.messages.size < 2) throw EngineException("Сначала поговорите — дневнику пока не о чем писать.")
        val (_, flow) = app.engines.generate(Prompt.diary(c, ch, settingsFor(chatId)))
        val text = Prompt.hideTags(flow.fold("") { a, b -> a + b }).trim()
        if (text.isBlank()) throw EngineException("Модель вернула пустую запись")
        repo.updateChat(chatId) { it.copy(diary = it.diary + DiaryEntry(text = text), diaryAt = it.messages.size) }
    }

    private suspend fun summarize(chatId: String, upTo: Int) {
        val ch = repo.chat(chatId) ?: return
        val c = repo.char(ch.charId) ?: return
        val (_, flow) = app.engines.generate(Prompt.summary(c, ch, settingsFor(chatId), upTo))
        val text = flow.fold("") { a, b -> a + b }
        val res = Prompt.parseSummaryFull(text)
        val existing = ch.facts.map { it.text.lowercase() }.toSet()
        val fresh = res.facts.filter { it.lowercase() !in existing }
        repo.updateChat(chatId) { cur ->
            cur.copy(summary = res.summary ?: cur.summary, summarizedUpTo = upTo, facts = cur.facts + fresh.map { Fact(text = it) },
                moments = cur.moments + listOfNotNull(res.moment?.let { Moment(text = it) }))
        }
        if (fresh.isNotEmpty()) _events.tryEmit(ChatEvent.Remembered(chatId, fresh))
    }

    // ================= Создание персонажа по фразе =================

    suspend fun generateCharacter(idea: String, base: Character): Character {
        val (_, flow) = app.engines.generate(Prompt.generateCharacter(idea))
        return Prompt.parseCharacter(flow.fold("") { a, b -> a + b }, base)
    }

    // ================= Моменты, иллюстрации, ответы из уведомлений =================

    fun addMoment(chatId: String, text: String) = repo.updateChat(chatId) { it.copy(moments = it.moments + Moment(text = text.trim())) }

    fun setNote(chatId: String, note: String) = repo.updateChat(chatId) { it.copy(authorNote = note.trim()) }

    /** Рисует текущий момент: текстовая модель пишет описание, модель картинок рисует. */
    fun illustrate(chatId: String) {
        if (_running.value != null) return
        val ch = repo.chat(chatId) ?: return
        val placeholder = Msg(fromUser = false, variants = listOf(""), kind = PICTURE, speaker = if (ch.sceneId != null) NARRATOR else null)
        repo.saveChat(ch.copy(messages = ch.messages + placeholder))
        job = scope.launch {
            _running.value = Running(chatId, placeholder.id)
            try {
                val s = settingsFor(chatId)
                val sc = repo.scene(ch.sceneId)
                val names = sc?.let { sceneChars(it).joinToString { c -> c.name + (c.tagline.takeIf { t -> t.isNotBlank() }?.let { t -> " ($t)" } ?: "") } }
                    ?: repo.char(ch.charId)?.let { c -> "${c.name}: ${c.description.ifBlank { c.tagline }}" } ?: ""
                val map = repo.chars.value.associateBy { it.id }
                val transcript = ch.messages.filter { it.kind != PICTURE }.takeLast(8).joinToString("\n") { m ->
                    (if (m.speaker != null) Prompt.sceneTranscriptName(m, map, s.user) else if (m.fromUser) s.user else repo.char(ch.charId)?.name ?: "") + ": " + m.text
                }.takeLast(3000)
                val (_, flow) = app.engines.generate(Prompt.illustration(names, transcript, sc?.world ?: ""), s)
                val prompt = flow.fold("") { a, b -> a + b }.trim().trim('"')
                setText(chatId, placeholder.id, "Рисую…", null, false)
                val bytes = app.engines.api.generateImage(repo.settings.value, prompt)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw EngineException("Не удалось открыть картинку")
                val file = repo.saveImageBitmap(bmp)
                repo.updateChat(chatId) { cur -> cur.copy(messages = cur.messages.map { if (it.id == placeholder.id) it.copy(variants = listOf(prompt), image = file, info = "иллюстрация · ${ImageModels.title(ImageModels.effective(s).orEmpty())}") else it }) }
            } catch (e: CancellationException) {
                repo.updateChat(chatId) { cur -> cur.copy(messages = cur.messages.filterNot { it.id == placeholder.id }) }
                throw e
            } catch (e: Throwable) {
                ErrorLog.add("illustrate", e)
                repo.updateChat(chatId) { cur -> cur.copy(messages = cur.messages.filterNot { it.id == placeholder.id }) }
                _events.tryEmit(ChatEvent.Error(chatId, e.message ?: e.toString(), (e as? EngineException)?.kind ?: ErrKind.OTHER, (e as? EngineException)?.url))
            } finally { _running.value = null }
        }
    }

    /** Для ответа из уведомления: отправить и дождаться ответа персонажа. */
    suspend fun sendAndWait(chatId: String, text: String): String? = kotlinx.coroutines.withContext(Dispatchers.Main) {
        while (_running.value != null) kotlinx.coroutines.delay(300)
        val chat = repo.chat(chatId) ?: return@withContext null
        val reply = Msg(fromUser = false, variants = listOf(""))
        repo.saveChat(chat.copy(messages = chat.messages + Msg(fromUser = true, variants = listOf(text.trim())) + reply))
        val ok = genNow(chatId, reply.id, false, ::replyRequest)
        if (ok) repo.chat(chatId)?.messages?.firstOrNull { it.id == reply.id }?.text else null
    }
}
