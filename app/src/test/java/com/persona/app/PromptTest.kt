package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {
    private val lira = Character(id = "l", name = "Лира", tagline = "Бард", description = "Странствующий бард.", soft = 0.9f, playful = 0.7f,
        definition = "{{char}}: Ой, только не начинай.\n{{user}}: Я молчу!", lorebookIds = listOf("b"))
    private val book = Lorebook(id = "b", name = "Мир", entries = listOf(LoreEntry(keys = listOf("гильдия"), content = "Гильдия бардов — закрытый клуб.")))
    private val s = Settings(userName = "Эйвин")

    @Test fun systemPromptHasCardRulesAndNames() {
        val chat = Chat(charId = "l", summary = "Они сбежали из гильдии.", facts = listOf(Fact(text = "Эйвин сдал экзамен")))
        val sys = Prompt.system(lira, chat, s, listOf(book), "что там с гильдией?")
        println(sys)
        assertTrue(sys.contains("Лира: Ой, только не начинай."))
        assertTrue(sys.contains("Эйвин: Я молчу!"))
        assertTrue(sys.contains("очень дерзкий"))
        assertTrue(sys.contains("Гильдия бардов — закрытый клуб."))
        assertTrue(sys.contains("Эйвин сдал экзамен"))
        assertFalse(sys.contains("{{"))
    }

    @Test fun loreOnlyWhenKeywordAppears() {
        val sys = Prompt.system(lira, Chat(charId = "l"), s, listOf(book), "просто привет")
        assertFalse(sys.contains("закрытый клуб"))
    }

    @Test fun replyEndsWithUserAndMergesTurns() {
        val msgs = listOf(Msg(fromUser = false, variants = listOf("Привет!")), Msg(fromUser = true, variants = listOf("Хей")), Msg(fromUser = true, variants = listOf("Ты тут?")))
        val r = Prompt.reply(lira, Chat(charId = "l", messages = msgs), s, emptyList(), msgs, local = true)
        assertEquals(2, r.history.size)
        assertTrue(r.history.last().user)
        assertEquals("Хей\n\nТы тут?", r.history.last().text)
    }

    @Test fun continueAddsInstruction() {
        val msgs = listOf(Msg(fromUser = true, variants = listOf("Расскажи")), Msg(fromUser = false, variants = listOf("Жила-была")))
        val r = Prompt.reply(lira, Chat(charId = "l", messages = msgs), s, emptyList(), msgs, local = true, instruction = "Продолжи")
        assertTrue(r.history.last().user); assertEquals("(Продолжи)", r.history.last().text)
    }

    @Test fun historyRespectsBudget() {
        val msgs = (1..200).map { Msg(fromUser = it % 2 == 0, variants = listOf("сообщение номер $it ".repeat(10))) }
        val r = Prompt.reply(lira, Chat(charId = "l", messages = msgs), s, emptyList(), msgs, local = true)
        val tokens = r.history.sumOf { Prompt.estimateTokens(it.text) } + Prompt.estimateTokens(r.system)
        assertTrue("tokens=$tokens", tokens < s.contextTokens)
        assertTrue(r.history.last().text.contains("номер 200"))
    }

    @Test fun parsesSummary() {
        val (sum, facts) = Prompt.parseSummary("КРАТКО:\nОни помирились в таверне.\nФАКТЫ:\n- Эйвин любит лютню\n- нет\n* Должен ужин")
        assertEquals("Они помирились в таверне.", sum)
        assertEquals(listOf("Эйвин любит лютню", "Должен ужин"), facts)
        assertEquals(emptyList<String>(), Prompt.parseSummary("КРАТКО: x\nФАКТЫ:\n- нет").second)
    }

    @Test fun cardRoundTrip() {
        // Экспорт использует только kotlinx.serialization, без Android.
        val c = Cards.parse(Cards.export(lira.copy(greetings = listOf("Привет", "Здорово"), tags = listOf("фэнтези"))))
        assertEquals("Лира", c.name); assertEquals(listOf("Привет", "Здорово"), c.greetings); assertEquals(0.9f, c.soft); assertEquals("Бард", c.tagline)
    }

    @Test fun parsesSillyTavernV1() {
        val c = Cards.parse("""{"name":"Ран","description":"Самурай","personality":"угрюмый","first_mes":"*кланяется*","mes_example":"<START>"}""")
        assertEquals("Ран", c.name); assertEquals("*кланяется*", c.greetings[0]); assertTrue(c.definition.contains("угрюмый"))
    }
}
